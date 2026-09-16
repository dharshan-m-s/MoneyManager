package com.moneymanager.app.updater.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class GitHubAsset(
    val name: String = "",
    @SerializedName("browser_download_url") val browserDownloadUrl: String? = null,
    @SerializedName("content_type") val contentType: String? = null,
    val size: Long = 0L,
    val digest: String? = null
)

@Keep
data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerializedName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

data class ReleaseUpdate(
    val versionName: String,
    val versionCode: Long,
    val tag: String,
    val title: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkName: String,
    val expectedSha256: String?,
    val mandatory: Boolean = false
)

data class UpdateDiagnostics(
    val repository: String = "",
    val endpoint: String = "",
    val lastCheckedAt: Long? = null,
    val httpStatus: Int? = null,
    val releaseTag: String? = null,
    val detectedApk: String? = null,
    val currentVersionName: String? = null,
    val currentVersionCode: Long? = null,
    val availableVersionName: String? = null,
    val availableVersionCode: Long? = null,
    val stage: String? = null,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null
)

sealed interface UpdateError {
    data object NoInternet : UpdateError
    data object DnsFailure : UpdateError
    data object ConnectionTimeout : UpdateError
    data object ReadTimeout : UpdateError
    data object TlsFailure : UpdateError
    data object HttpUnauthorized : UpdateError
    data object HttpForbidden : UpdateError
    data object HttpNotFound : UpdateError
    data object HttpRateLimited : UpdateError
    data class HttpServerError(val code: Int) : UpdateError
    data object InvalidJson : UpdateError
    data object NoRelease : UpdateError
    data object NoApk : UpdateError
    data object InvalidVersion : UpdateError
    data object DownloadFailed : UpdateError
    data object VerificationFailed : UpdateError
    data object InstallationFailed : UpdateError
    data class Unknown(val detail: String? = null) : UpdateError
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val currentVersionName: String) : UpdateState
    data class UpdateAvailable(
        val currentVersionName: String,
        val currentVersionCode: Long,
        val update: ReleaseUpdate
    ) : UpdateState
    data class Downloading(val update: ReleaseUpdate, val progress: Int, val downloadedBytes: Long, val totalBytes: Long?) : UpdateState
    data class Downloaded(val update: ReleaseUpdate, val filePath: String) : UpdateState
    data object Installing : UpdateState
    data object Offline : UpdateState
    data class Error(val error: UpdateError) : UpdateState
}
