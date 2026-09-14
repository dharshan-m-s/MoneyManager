package com.moneymanager.app.data.updater

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Singleton state machine governing the full update lifecycle:
 * check → download → verify → ready → install.
 *
 * The ViewModels observe [uiState]. All work runs on [Dispatchers.IO] inside this
 * class's own scope, so downloads survive a navigation away from the Update Center
 * and are guarded by [Mutex]es against duplicate concurrent requests.
 *
 * Storage: [updatesDir] is [Context.cacheDir]/updates — purged before each new
 * check/download and on app launch. Never touches financial data or databases.
 */
@Singleton
class UpdateManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
    private val updateRepository: UpdateRepository,
    private val verifier: UpdateVerifier,
    private val installer: UpdateInstaller
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val updatesDir: File
        get() = File(appContext.cacheDir, "updates").also { it.mkdirs() }

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** Serializes checks so overlapping triggers never fire two API request chains. */
    private val checkMutex = Mutex()

    /** Serializes downloads; one APK download at a time, ever. */
    private val downloadMutex = Mutex()

    /** Handle to the running download, for cancellation. */
    @Volatile private var downloadJob: Job? = null

    /** True while an APK download is running in the manager-owned scope. */
    val isDownloadInProgress: Boolean
        get() = downloadJob?.isActive == true

    /**
     * Reconciles state with reality. Called on app start and every time the Update
     * Center resumes (notably right after returning from the Android installer):
     *
     * - update installed while we were away → clean [UpdateUiState.Idle] + purge;
     * - user returned from the installer without installing → restore
     *   [UpdateUiState.ReadyToInstall] ("installation cancelled" retry state) when
     *   the verified file is still present, otherwise reset to Idle;
     * - any other state referencing a version that is already installed → Idle.
     */
    fun syncInstallationOutcome() {
        val installed = installedVersionCodeNow()
        val current = _uiState.value
        if (current is UpdateUiState.Installing) {
            if (current.info.versionCode <= installed) {
                _uiState.value = UpdateUiState.Idle
                scope.launch { purgeDownloads() }
            } else {
                val apk = File(current.localFilePath)
                if (apk.exists() && apk.length() > 0L) {
                    _uiState.value = UpdateUiState.ReadyToInstall(current.info, current.localFilePath)
                } else {
                    _uiState.value = UpdateUiState.Idle
                    scope.launch { purgeDownloads() }
                }
            }
            return
        }
        val resolved = resolvePostInstallState(current, installed)
        if (resolved != current) {
            _uiState.value = resolved
        }
        if (resolved == UpdateUiState.Idle || current == UpdateUiState.Idle) {
            // No installable state to protect → orphaned cache files can go.
            val readyFile = (resolved as? UpdateUiState.ReadyToInstall)?.localFilePath?.let { File(it).name }
            scope.launch { purgeDownloads(keepFileName = readyFile) }
        }
    }

    /**
     * Deletes cached update APK/temp files. [keepFileName] (the verified APK the
     * user can still install) is never removed.
     */
    private fun purgeDownloads(keepFileName: String? = null) {
        updatesDir.listFiles { f ->
            (f.name.endsWith(".apk") || f.name.endsWith(".tmp")) && f.name != keepFileName
        }?.forEach { runCatching { it.delete() } }
    }

    /**
     * Checks GitHub for the newest stable public release.
     * Concurrent calls are folded into the in-flight check.
     */
    suspend fun checkForUpdate(installedVersionCode: Long, installedVersionName: String) {
        checkMutex.withLock {
            if (!updateRepository.repoConfigured) {
                _uiState.value = UpdateUiState.UpdateFailed(
                    info = fallbackInfo(),
                    message = "Repository not configured. Set GITHUB_REPO in your Gradle build."
                )
                return
            }

            if (!isOnline()) {
                _uiState.value = UpdateUiState.UpdateFailed(
                    info = fallbackInfo(),
                    message = "No internet connection. Check your network and try again."
                )
                return
            }

            _uiState.value = UpdateUiState.Checking
            val repoPath = updateRepository.repoName()

            val result = try {
                updateRepository.checkForUpdate(installedVersionCode)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Update check crashed")
                UpdateCheckResult.Failed(UpdateFailure.GITHUB_UNAVAILABLE)
            }

            _uiState.value = when (result) {
                is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.Available -> UpdateUiState.UpdateAvailable(
                    info = result.update,
                    installedVersionName = installedVersionName
                )
                is UpdateCheckResult.Failed -> when (result.reason) {
                    UpdateFailure.NETWORK_UNAVAILABLE -> UpdateUiState.UpdateFailed(
                        info = fallbackInfo(), message = "No internet connection"
                    )
                    UpdateFailure.GITHUB_UNAVAILABLE -> UpdateUiState.UpdateFailed(
                        info = fallbackInfo(), message = "GitHub is currently unavailable. Try again later."
                    )
                    UpdateFailure.INVALID_RELEASE -> UpdateUiState.UpdateFailed(
                        info = fallbackInfo(), message = "No valid release found for $repoPath"
                    )
                    UpdateFailure.UNSUPPORTED_UPDATE -> UpdateUiState.UpdateFailed(
                        info = fallbackInfo(), message = "The available update is not compatible with this install"
                    )
                }
            }
        }
    }

    /**
     * Downloads the update APK into [updatesDir], then verifies it.
     *
     * - refuses to start while another download runs (atomic [Mutex.tryLock] gate);
     * - runs in the manager-owned scope, so navigating away from the Update Center
     *   does not abort an in-flight download;
     * - reuses an already-completed download of the same version instead of
     *   re-fetching, but always re-verifies it before offering installation;
     * - deletes incomplete files on failure/cancellation.
     */
    suspend fun downloadAndVerify(info: UpdateInfo) {
        if (downloadJob?.isActive == true) {
            Timber.d("Download already in progress; ignoring duplicate request")
            return
        }
        val job = scope.launch {
            // tryLock: two racing callers must never both download.
            if (!downloadMutex.tryLock()) return@launch
            try {
                performDownloadAndVerify(info)
            } finally {
                downloadMutex.unlock()
            }
        }
        downloadJob = job
        job.join()
    }

    private suspend fun performDownloadAndVerify(info: UpdateInfo) {
        if (info.apkUrl.isBlank()) {
            _uiState.value = UpdateUiState.DownloadFailed(info, "Download URL is empty")
            return
        }
        // Defense in depth: the asset name must be a plain file name so the
        // download target can never escape the updates cache directory.
        if (!updateRepository.isPlainFileName(info.apkName) ||
            File(info.apkName).name != info.apkName
        ) {
            _uiState.value = UpdateUiState.DownloadFailed(info, "The update asset has an invalid file name")
            return
        }
        if (!isOnline()) {
            _uiState.value = UpdateUiState.DownloadFailed(info, "No internet connection")
            return
        }

        // Clean previous incomplete downloads before starting a new one.
        updatesDir.listFiles { f -> f.name.endsWith(".apk") || f.name.endsWith(".tmp") }
            ?.forEach { runCatching { it.delete() } }

        val targetFile = File(updatesDir, info.apkName)
        val tmpFile = File(updatesDir, "${info.apkName}.tmp")
        val context = currentCoroutineContext()
        var lastProgress = 0f
        var progressBytes = 0L

        if (info.downloadSizeBytes > 0 && !hasAvailableSpace(info.downloadSizeBytes)) {
            _uiState.value = UpdateUiState.DownloadFailed(info, "Insufficient storage available for this update")
            return
        }

        try {
            val alreadyDownloaded = targetFile.exists() && targetFile.length() > 0L
            if (!alreadyDownloaded) {
                _uiState.value = UpdateUiState.Downloading(info, 0f, 0, max(info.downloadSizeBytes, 1))
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(info.apkUrl)
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}")
                        }
                        val serverLength = response.body?.contentLength() ?: -1L
                        val actualTotal = if (serverLength > 0) serverLength else max(info.downloadSizeBytes, 1L)

                        FileOutputStream(tmpFile).use { output ->
                            val source = response.body?.source() ?: throw IOException("empty body")
                            val buffer = okio.Buffer()
                            var totalRead = 0L
                            while (true) {
                                context.ensureActive()
                                val read = source.read(buffer, 8192)
                                if (read == -1L) break
                                totalRead += read
                                output.write(buffer.readByteArray(read))
                                val fraction = if (actualTotal > 0) (totalRead.toFloat() / actualTotal).coerceIn(0f, 1f) else 0f
                                // Throttle state emissions: >0.5% or >100KB since last emit.
                                if (fraction - lastProgress > 0.005f || totalRead - progressBytes > 102_400 || fraction >= 1f) {
                                    _uiState.value = UpdateUiState.Downloading(info, fraction, totalRead, actualTotal)
                                    lastProgress = fraction
                                    progressBytes = totalRead
                                }
                            }
                        }
                    }
                    if (!tmpFile.renameTo(targetFile) || !targetFile.exists()) {
                        throw IOException("Downloaded file could not be finalized")
                    }
                }
            } else {
                Timber.d("Reusing previously downloaded %s", info.apkName)
            }

            // Verification phase — always runs, even for reused downloads.
            _uiState.value = UpdateUiState.Verifying(info)
            val validation = withContext(Dispatchers.IO) {
                verifier.fullValidation(
                    apk = targetFile,
                    expectedSha256 = info.expectedSha256,
                    installedApplicationId = appContext.packageName,
                    installedVersionCode = installedVersionCodeNow()
                )
            }
            if (validation is VerificationResult.Failed) {
                targetFile.delete()
                _uiState.value = UpdateUiState.VerificationFailed(info, validation.message)
                return
            }

            _uiState.value = UpdateUiState.ReadyToInstall(info, targetFile.absolutePath)
        } catch (e: kotlinx.coroutines.CancellationException) {
            runCatching { tmpFile.delete() }
            Timber.d("Download cancelled by user")
            _uiState.value = UpdateUiState.Cancelled(info)
        } catch (e: OutOfMemoryError) {
            runCatching { tmpFile.delete(); targetFile.delete() }
            _uiState.value = UpdateUiState.DownloadFailed(info, "Insufficient memory to download this update")
        } catch (e: IOException) {
            runCatching { tmpFile.delete(); targetFile.delete() }
            val msg = when {
                e.message?.contains("timeout", true) == true -> "Connection timed out"
                e.message?.contains("reset", true) == true -> "Connection lost"
                e.message?.contains("ENOSPC") == true -> "Insufficient storage"
                else -> e.message ?: "Download failed"
            }
            _uiState.value = UpdateUiState.DownloadFailed(info, msg)
        } catch (e: Exception) {
            runCatching { tmpFile.delete(); targetFile.delete() }
            Timber.e(e, "Unexpected error during download")
            _uiState.value = UpdateUiState.DownloadFailed(info, "An unexpected error occurred")
        }
    }

    /** Cancels an in-flight download (no-op when none is running). */
    fun cancelDownload() {
        val job = downloadJob
        if (job != null && job.isActive) {
            job.cancel()
        } else {
            // Nothing in flight: make sure the UI doesn't stay stuck on a transient state.
            when (val current = _uiState.value) {
                is UpdateUiState.Downloading -> _uiState.value = UpdateUiState.Cancelled(current.info)
                is UpdateUiState.Verifying -> _uiState.value = UpdateUiState.Cancelled(current.info)
                else -> Unit
            }
        }
    }

    /**
     * Launches the Android installer for a verified APK. Returns true when the
     * installer activity was dispatched.
     */
    fun installUpdate(info: UpdateInfo, apkPath: String): Boolean {
        val apk = File(apkPath)
        if (!apk.exists() || apk.length() == 0L) {
            _uiState.value = UpdateUiState.UpdateFailed(info, "APK file is missing")
            return false
        }
        _uiState.value = UpdateUiState.Installing(info, apkPath)
        val launched = installer.launchInstaller(apk)
        if (!launched) {
            _uiState.value = UpdateUiState.Cancelled(info)
        }
        return launched
    }



    private fun fallbackInfo() = UpdateInfo(
        versionName = "", versionCode = 0L, tag = "", apkName = "",
        apkUrl = "", expectedSha256 = null, downloadSizeBytes = 0,
        whatIsNew = "", mandatory = false
    )

    /** Reads the actual installed package's version code via PackageManager. */
    private fun installedVersionCodeNow(): Long {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
            info.longVersionCode
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            max(com.moneymanager.app.BuildConfig.VERSION_CODE.toLong(), 0L)
        }
    }

    /** True when any validated network is available. On failure to measure, optimistic. */
    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            true // cannot measure → let the actual request surface the failure
        }
    }

    /** True when the cache partition has at least [requiredBytes] free. */
    private fun hasAvailableSpace(requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(appContext.cacheDir.absolutePath)
            stat.availableBytes > requiredBytes + 512L * 1024L
        } catch (e: Exception) {
            true // if we can't measure, let the IO path decide and report
        }
    }
}
