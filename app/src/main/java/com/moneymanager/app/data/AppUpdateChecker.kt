package com.moneymanager.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.data.ChecksumParser
import com.moneymanager.app.updater.data.UpdateUrlPolicy
import com.moneymanager.app.updater.model.ReleaseUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * BitChord-style in-app updater: the release check and notes come from the app's normal
 * [com.moneymanager.app.updater.data.UpdateRepository], and this object handles the actual
 * APK download and hand-off to Android's package installer.
 *
 * Security pass: an APK is never installed until it has passed all three checks below.
 *  1. SHA-256 matches the digest published with the GitHub release.
 *  2. Package name is this app's package and the version is not older than the release.
 *  3. The APK is signed by the official MoneyManager release certificate.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val versionCode: Long,
        val apkUrl: String,
        val apkName: String,
        /** Normalized as `sha256:<lowercase hex>`, or null when GitHub published no digest. */
        val expectedSha256: String?,
        val releaseUrl: String? = null,
        val notes: String? = null,
    )

    private const val CACHE_SUBDIR = "updates"

    /** SHA-256 of the production signing certificate; a downloaded APK must match this exactly. */
    private const val OFFICIAL_SIGNER_SHA256 =
        "d205e23d648bf790d081f4f9df9653392b3dd1b22d4a84a9810533224930a0db"

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /** Where this update's APK download currently stands, for the dialog's progress row. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

    /** Set from the UI thread when the user cancels; polled between network reads. */
    @Volatile
    private var downloadCancelled = false

    /** The in-flight OkHttp call, so cancellation interrupts a blocked read instead of just flagging it. */
    @Volatile
    private var activeCall: Call? = null

    /**
     * Called after the repository has selected and verified a release candidate. Keeping this
     * separate from the network fetch means the download path cannot be pointed at an arbitrary
     * URL by the UI, and it reuses the checksum already resolved by [UpdateRepository].
     */
    fun prepare(update: ReleaseUpdate) {
        _available.value = UpdateInfo(
            version = update.versionName,
            versionCode = update.versionCode,
            apkUrl = update.apkUrl,
            apkName = update.apkName,
            expectedSha256 = ChecksumParser.normalizeDigest(update.expectedSha256),
            releaseUrl = null,
            notes = update.releaseNotes,
        )
    }

    /** Forgets the prepared update so a stale candidate cannot be downloaded later. */
    fun clearPrepared() {
        _available.value = null
    }

    /**
     * Wipes any APK left over from a previous update attempt. Call this only from the
     * update screen (or immediately before a new download), never from Application.onCreate
     * or another process-start path: automatic APK deletion at startup is what made the
     * v1.1.9 build get flagged by Play Protect.
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * Streams the prepared release's APK into a `.part` file while hashing the bytes. The file is
     * atomically renamed to its final name only after checksum, archive, package, version and
     * signature checks pass, so a tampered or partial download can never reach the installer.
     */
    suspend fun downloadApk(context: Context): Unit = withContext(Dispatchers.IO) {
        val info = _available.value
        if (info == null) {
            _download.value = DownloadState.Failed("No update was selected. Check for updates again.")
            return@withContext
        }
        val expectedSha256 = info.expectedSha256
        if (expectedSha256 == null) {
            _download.value = DownloadState.Failed(
                "This release has no published SHA-256 checksum. Use Open release page instead."
            )
            return@withContext
        }

        UpdateUrlPolicy.requireAllowedAssetUrl(info.apkUrl)
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)

        var partFile: File? = null
        runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            // Drop anything left over from an earlier attempt.
            dir.listFiles()?.forEach { it.delete() }
            val token = safeFileToken(info.version)
            val finalFile = File(dir, "moneymanager-$token-${UUID.randomUUID()}.apk")
            val downloadFile = File(dir, "${finalFile.name}.part")
            partFile = downloadFile
            val digest = MessageDigest.getInstance("SHA-256")

            val request = Request.Builder().url(info.apkUrl).build()
            val call = Http.client.newCall(request)
            activeCall = call
            call.execute().use { response ->
                if (!UpdateUrlPolicy.isAllowedAssetUrl(response.request.url.toString())) {
                    throw SecurityException("Redirect left the official GitHub asset hosts")
                }
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 }

                body.byteStream().use { input ->
                    downloadFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            if (downloadCancelled) throw CancellationException("Download cancelled")
                            val read = input.read(buffer)
                            if (read == -1) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            readTotal += read
                            total?.let {
                                _download.value =
                                    DownloadState.Downloading((readTotal.toFloat() / it).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (downloadCancelled) throw CancellationException("Download cancelled")

            // Reject HTML error pages and truncated files before doing any crypto or install work.
            val header = ByteArray(4)
            downloadFile.inputStream().use { input ->
                check(input.read(header) == header.size) { "Downloaded APK was empty" }
            }
            check(header.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04))) {
                "Downloaded file is not a valid APK archive"
            }

            val actualSha256 = "sha256:${digest.digest().toHex()}"
            val expectedBytes = expectedSha256.removePrefix("sha256:").toByteArray(Charsets.UTF_8)
            val actualBytes = actualSha256.removePrefix("sha256:").toByteArray(Charsets.UTF_8)
            if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
                throw SecurityException("APK SHA-256 checksum did not match the published release")
            }

            verifyApk(context, downloadFile, info)?.let { problem -> throw SecurityException(problem) }

            check(downloadFile.renameTo(finalFile)) { "Could not finalize the downloaded APK" }
            _download.value = DownloadState.Ready(finalFile)
        }.onFailure { error ->
            _download.value = if (downloadCancelled || error is CancellationException) {
                DownloadState.Idle
            } else {
                DownloadState.Failed(error.message ?: "Download failed")
            }
        }.also {
            activeCall = null
            partFile?.takeIf { it.exists() }?.delete()
        }
    }

    /** Stops an in-flight download immediately, including a blocked network read. */
    fun cancelDownload() {
        downloadCancelled = true
        activeCall?.cancel()
    }

    /** Back to square one after a failure, so the dialog offers Download again. */
    fun resetDownload() {
        cancelDownload()
        _download.value = DownloadState.Idle
    }

    /**
     * Verifies the downloaded APK before it is handed to the system installer. This is also
     * called again in [installApk] in case the ready file was touched after the download.
     */
    private fun verifyApk(context: Context, file: File, info: UpdateInfo): String? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "APK could not be read as a valid package"

        if (packageInfo.packageName != BuildConfig.APPLICATION_ID) {
            return "APK package name does not match Money Manager"
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        if (versionCode != info.versionCode || versionCode <= BuildConfig.VERSION_CODE.toLong()) {
            return "APK version does not match the announced release"
        }

        val signerBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.map { it.toByteArray() }.orEmpty()
        }
        if (signerBytes.size != 1) {
            return "APK must have exactly one official signing certificate"
        }
        val signerSha256 = "sha256:${signerBytes.single().sha256Hex()}"
        if (!signerSha256.equals("sha256:$OFFICIAL_SIGNER_SHA256", ignoreCase = true)) {
            return "APK is not signed by the official Money Manager release certificate"
        }
        return null
    }

    /**
     * Hands a verified APK to the system installer.
     *
     * Sideloaded apps need the user's blessing per app ("install unknown apps");
     * without it the installer intent silently does nothing on most ROMs, so
     * the user is sent to that one switch first and taps Install again after.
     * Some TV builds have no such settings screen; there the installer is
     * launched anyway and shows its own "not allowed" prompt with a way in.
     */
    @Suppress("DEPRECATION")
    fun installApk(context: Context, file: File): Boolean {
        val info = _available.value
        if (info == null) {
            _download.value = DownloadState.Failed("No update was selected. Check for updates again.")
            return false
        }
        val expectedSha256 = info.expectedSha256
        if (expectedSha256 == null ||
            !MessageDigest.isEqual(
                expectedSha256.removePrefix("sha256:").toByteArray(Charsets.UTF_8),
                file.sha256Hex().toByteArray(Charsets.UTF_8),
            )
        ) {
            file.delete()
            _download.value = DownloadState.Failed("Update verification failed: checksum changed")
            return false
        }
        verifyApk(context, file, info)?.let { problem ->
            file.delete()
            _download.value = DownloadState.Failed("Update verification failed: $problem")
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            // Verification passed; the user just needs to grant the install permission first.
            return opened
        }
        val launched = runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK,
                    ),
            )
        }.isSuccess
        if (!launched) {
            _download.value = DownloadState.Failed("Android could not start the package installer")
        }
        return launched
    }

    /** Version strings come from a parser, but a filename must never trust remote text verbatim. */
    private fun safeFileToken(raw: String): String =
        raw.filter { it.isDigit() || it == '.' || it == '-' || it == '_' }
            .take(40)
            .ifBlank { "update" }

    private fun File.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256").digest(this).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
