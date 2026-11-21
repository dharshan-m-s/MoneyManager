package com.moneymanager.app.updater.data

import android.content.Context
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.model.GitHubAsset
import com.moneymanager.app.updater.model.ReleaseUpdate
import com.moneymanager.app.updater.model.UpdateDiagnostics
import com.moneymanager.app.updater.model.UpdateError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class UpdateRepository(context: Context) {
    private val releaseSource = GitHubReleaseDataSource(context.applicationContext)

    companion object {
        private const val MAX_CHECKSUM_BYTES = 4_096
    }
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
            val apkUrl = apkAsset.browserDownloadUrl?.takeIf(UpdateUrlPolicy::isAllowedAssetUrl) ?: run {
                lastDiagnostics = lastDiagnostics.copy(httpStatus = response.httpStatus, releaseTag = release.tagName, availableVersionName = version.toString(), availableVersionCode = versionCode, detectedApk = apkAsset.name, stage = "UNSAFE_URL")
                return@withContext CheckResult.Error(UpdateError.UnsafeUrl)
            }
            val shaAsset = release.assets.firstOrNull {
                it.name == "${apkAsset.name}.sha256" && UpdateUrlPolicy.isAllowedAssetUrl(it.browserDownloadUrl)
            }
            val (expectedSha256, checksumSource) = resolveChecksum(apkAsset, shaAsset?.browserDownloadUrl)
            val update = ReleaseUpdate(
                versionName = version.toString(),
                versionCode = versionCode,
                tag = release.tagName,
                title = release.name ?: "Money Manager ${version}",
                releaseNotes = release.body.orEmpty().trim(),
                apkUrl = apkUrl,
                apkName = apkAsset.name,
                expectedSha256 = expectedSha256,
                mandatory = false,
                checksumSource = checksumSource
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
        if (!UpdateUrlPolicy.isAllowedAssetUrl(url)) return@runCatching null
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
            if (!UpdateUrlPolicy.isAllowedAssetUrl(connection.url.toString())) return@runCatching null
            val content = connection.inputStream.use { BoundedRead.readText(it, MAX_CHECKSUM_BYTES) }
                ?: return@runCatching null
            ChecksumParser.parseSha256File(content)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun resolveChecksum(
        apkAsset: com.moneymanager.app.updater.model.GitHubAsset,
        shaAssetUrl: String?
    ): Pair<String?, String?> {
        ChecksumParser.normalizeDigest(apkAsset.digest)?.let { return it to "api-digest" }
        shaAssetUrl?.let { url -> fetchChecksum(url)?.let { return it to "sha256-asset" } }
        return null to null
    }

    sealed interface CheckResult {
        data class Available(val update: ReleaseUpdate) : CheckResult
        data class UpToDate(val versionName: String) : CheckResult
        data class Error(val error: UpdateError) : CheckResult
    }
}
