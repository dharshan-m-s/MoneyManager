package com.moneymanager.app.updater.data

import android.content.Context
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.model.ReleaseUpdate
import com.moneymanager.app.updater.model.UpdateDiagnostics
import com.moneymanager.app.updater.model.UpdateError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class UpdateRepository(context: Context) {
    private val appContext = context.applicationContext
    private val releaseSource = GitHubReleaseDataSource(appContext)
    @Volatile var lastDiagnostics: UpdateDiagnostics = UpdateDiagnostics(
        repository = UpdateConfig.repositoryPath,
        endpoint = UpdateConfig.releasesUrl
    )
        private set

    suspend fun checkForUpdate(): CheckResult = withContext(Dispatchers.IO) {
        val currentVersionName = BuildConfig.VERSION_NAME.removePrefix("v")
        val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        lastDiagnostics = UpdateDiagnostics(
            repository = UpdateConfig.repositoryPath,
            endpoint = UpdateConfig.releasesUrl,
            lastCheckedAt = System.currentTimeMillis(),
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            stage = "RELEASES_REQUEST"
        )
        try {
            val response = releaseSource.fetchStableReleases()
            ensureActive()
            val release = ReleaseSelector.selectLatestStable(response.releases) ?: run {
                lastDiagnostics = lastDiagnostics.copy(httpStatus = response.httpStatus, stage = "NO_RELEASE")
                return@withContext CheckResult.Error(UpdateError.NoRelease)
            }
            val version = VersionComparator.parse(release.tagName) ?: run {
                lastDiagnostics = lastDiagnostics.copy(httpStatus = response.httpStatus, releaseTag = release.tagName, stage = "INVALID_VERSION")
                return@withContext CheckResult.Error(UpdateError.InvalidVersion)
            }
            val versionCode = VersionComparator.toVersionCode(version)
            val apkAsset = ReleaseSelector.selectApkAsset(release, version) ?: run {
                lastDiagnostics = lastDiagnostics.copy(httpStatus = response.httpStatus, releaseTag = release.tagName, availableVersionName = version.toString(), availableVersionCode = versionCode, stage = "NO_APK")
                return@withContext CheckResult.Error(UpdateError.NoApk)
            }
            val apkUrl = apkAsset.browserDownloadUrl ?: run {
                lastDiagnostics = lastDiagnostics.copy(httpStatus = response.httpStatus, releaseTag = release.tagName, availableVersionName = version.toString(), availableVersionCode = versionCode, detectedApk = apkAsset.name, stage = "NO_APK_URL")
                return@withContext CheckResult.Error(UpdateError.NoApk)
            }
            val shaAsset = release.assets.firstOrNull { it.name == "${apkAsset.name}.sha256" }
            val expectedSha256 = shaAsset?.browserDownloadUrl?.let { fetchChecksum(it) }
            val update = ReleaseUpdate(
                versionName = version.toString(),
                versionCode = versionCode,
                tag = release.tagName,
                title = release.name ?: "Money Manager ${version}",
                releaseNotes = release.body.orEmpty().trim(),
                apkUrl = apkUrl,
                apkName = apkAsset.name,
                expectedSha256 = expectedSha256,
                mandatory = false
            )
            lastDiagnostics = lastDiagnostics.copy(
                httpStatus = response.httpStatus,
                releaseTag = release.tagName,
                detectedApk = apkAsset.name,
                availableVersionName = update.versionName,
                availableVersionCode = versionCode,
                stage = "VERSION_COMPARISON"
            )
            when {
                versionCode <= currentVersionCode -> CheckResult.UpToDate(currentVersionName)
                else -> CheckResult.Available(update)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            lastDiagnostics = lastDiagnostics.copy(stage = "FAILED", exceptionType = t::class.java.simpleName, exceptionMessage = t.message?.take(300))
            CheckResult.Error(GitHubReleaseDataSource.mapNetworkError(t))
        }
    }

    private fun fetchChecksum(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("User-Agent", "MoneyManager/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val content = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            val hash = Regex("""^([0-9a-fA-F]{64})(?:\s|$)""").find(content)?.groupValues?.get(1) ?: return@runCatching null
            "sha256:$hash"
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    suspend fun download(update: ReleaseUpdate, onProgress: (Int, Long, Long?) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        val safeName = update.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val targetDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val target = File(targetDir, safeName)
        target.delete()
        val temp = File(targetDir, "$safeName.part")
        temp.delete()
        try {
            val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream")
                setRequestProperty("User-Agent", "MoneyManager/${BuildConfig.VERSION_NAME}")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) throw java.io.IOException("HTTP $status")
                val total = connection.contentLengthLong.takeIf { it > 0L }
                var downloaded = 0L
                var lastReported = -1
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            val percent = total?.let { ((downloaded * 100L) / it).toInt().coerceIn(0, 100) } ?: -1
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent, downloaded, total)
                            }
                        }
                        output.fd.sync()
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (!temp.exists() || temp.length() <= 0L) throw java.io.IOException("Downloaded APK is empty")
            update.expectedSha256?.takeIf { it.startsWith("sha256:") }?.let { expected ->
                val actual = "sha256:" + sha256(temp)
                if (!actual.equals(expected, ignoreCase = true)) throw SecurityException("APK digest mismatch")
            }
            if (!temp.renameTo(target)) throw java.io.IOException("Could not finalize APK")
            Result.success(target)
        } catch (t: Throwable) {
            temp.delete()
            target.delete()
            if (t is CancellationException) throw t
            Result.failure(t)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    sealed interface CheckResult {
        data class Available(val update: ReleaseUpdate) : CheckResult
        data class UpToDate(val versionName: String) : CheckResult
        data class Error(val error: UpdateError) : CheckResult
    }
}