package com.moneymanager.app.data.updater

import com.google.gson.annotations.SerializedName

/**
 * Domain types for the in-app updater. This file is deliberately free of Android
 * framework dependencies so the pure parsing/decision logic can be unit-tested on
 * the JVM.
 */
object UpdateConstants {

    /** GitHub API host. HTTPS only – the updater never sends credentials. */
    const val API_BASE = "https://api.github.com"

    /**
     * Github "owner/repo" identifier, injected at build time from
     * `BuildConfig.GITHUB_REPO`. Must be configured once when the project is pushed
     * to its GitHub repository (see README / GITHUB_RELEASE_GUIDE). The app never
     * guesses this value.
     */
    const val GITHUB_REPO_UNSET = ""

    /**
     * Deterministic Android versionCode mapping used by the release pipeline:
     * major * 1_000_000 + minor * 1_000 + patch (e.g. 1.4.2 -> 1004002).
     * The app mirrors this exactly so installed vs release comparison is integer-based.
     */
    fun versionCodeOf(major: Int, minor: Int, patch: Int): Int = major * 1_000_000 + minor * 1_000 + patch

    /**
     * Parses a semantic version string ("1.4.2", optional leading 'v') into a
     * versionCode integer. Returns null when the format is not strictly
     * "v?<d>.<d>.<d>" (rejects prerelease suffixes like "1.4.2-beta").
     *
     * Guard: minor/patch are capped at 999 so two distinct versions can never
     * overflow into the same versionCode (e.g. "1.1.1000" would otherwise collide
     * with "1.2.0"). This mirrors the cap enforced by the release workflow.
     */
    fun versionCodeOfOrNull(version: String): Int? {
        val clean = version.trim().removePrefix("v")
        val parts = clean.split(".")
        if (parts.size != 3) return null
        if (parts.any { it.isEmpty() || it.length > 3 || !it.all(Char::isDigit) }) return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers[0] <= 0 || numbers[1] > 999 || numbers[2] > 999) return null
        val code = versionCodeOf(numbers[0], numbers[1], numbers[2])
        return if (code > 0) code else null
    }

    /** update.json metadata asset name uploaded alongside a release APK. */
    const val UPDATE_METADATA_FILE = "update.json"

    /** APK asset filename prefix used by the release pipeline. */
    const val APK_FILE_PREFIX = "MoneyManager-"

    const val APK_MIME = "application/vnd.android.package-archive"
}

/**
 * GitHub asset object from the Releases API.
 */
data class GitHubAsset(
    val name: String = "",
    val size: Long = 0L,
    /** SHA-256 digest of the asset content, published by the Releases API. */
    @SerializedName("digest") val digest: String = "",
    @SerializedName("browser_download_url") val browserDownloadUrl: String = ""
) {
    /** "sha256:<hex>" as reported by GitHub, or null when absent/other algorithm. */
    val sha256Digest: String?
        get() {
            val value = digest.trim()
            if (!value.startsWith("sha256:", ignoreCase = true)) return null
            val hex = value.substringAfter(':').trim()
            return if (hex.isNotEmpty()) hex else null
        }
}

/**
 * GitHub release object from the Releases API.
 */
data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String = "",
    val name: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerializedName("html_url") val htmlUrl: String = "",
    @SerializedName("published_at") val publishedAt: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

/**
 * Machine-readable update metadata published by the release pipeline alongside the
 * APK (`update.json`). Mirrors the Gradle-exported values; never hand-maintained.
 */
data class UpdateMetadata(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val tag: String = "",
    val apk: String = "",
    val sha256: String = "",
    val mandatory: Boolean = false
) {
    val isComplete: Boolean
        get() = versionCode > 0 && apk.isNotBlank() && sha256.isNotBlank() && versionName.isNotBlank()
}

/** Result of checking the latest stable public release. */
sealed class UpdateCheckResult {
    /** No newer stable release than the installed app. */
    data object UpToDate : UpdateCheckResult()

    /** A download-eligible release exists. */
    data class Available(val update: UpdateInfo) : UpdateCheckResult()

    /** No suitable release/source could be reached or parsed. */
    data class Failed(val reason: UpdateFailure) : UpdateCheckResult()
}

/**
 * Fully-validated remote update that the app can download and install.
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val tag: String,
    val apkName: String,
    val apkUrl: String,
    val expectedSha256: String?,
    val downloadSizeBytes: Long,
    val whatIsNew: String,
    val mandatory: Boolean
) {
    val expectedSha256Lower: String? get() = expectedSha256?.trim()?.lowercase()
}

/** Machine-readable failure category; maps to a user-facing state with recovery. */
enum class UpdateFailure {
    NETWORK_UNAVAILABLE,     // offline or no connectivity
    GITHUB_UNAVAILABLE,      // server/HTTP error or timeout
    INVALID_RELEASE,         // release exists but malformed (no APK, bad version)
    UNSUPPORTED_UPDATE       // theoretically newer but unusable for this install
}

/** Stable public state machine surfaced to the Update Center UI. */
sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class UpdateAvailable(val info: UpdateInfo, val installedVersionName: String) : UpdateUiState()
    data class Downloading(
        val info: UpdateInfo,
        val fraction: Float,
        val bytesRead: Long,
        val bytesTotal: Long
    ) : UpdateUiState()

    data class Verifying(val info: UpdateInfo) : UpdateUiState()
    data class ReadyToInstall(val info: UpdateInfo, val localFilePath: String) : UpdateUiState()
    data class Installing(val info: UpdateInfo, val localFilePath: String) : UpdateUiState()

    data class Cancelled(val info: UpdateInfo) : UpdateUiState()
    data class DownloadFailed(val info: UpdateInfo, val message: String) : UpdateUiState()
    data class VerificationFailed(val info: UpdateInfo, val message: String) : UpdateUiState()
    data class UpdateFailed(val info: UpdateInfo, val message: String) : UpdateUiState()
}

/**
 * Local download tracking used across process lifetime. Kept tiny: file path,
 * size and the expected digest of the *currently selected* update.
 */
data class PendingUpdate(
    val info: UpdateInfo,
    val localFile: java.io.File
)

/**
 * Pure post-install state resolution, unit-testable on the JVM:
 * - a state whose pending update has already been installed (versionCode <=
 *   installed) collapses to a clean [UpdateUiState.Idle];
 * - any state carrying [UpdateInfo] with a still-newer version is preserved
 *   (the caller decides how to surface "installation cancelled" recovery);
 * - transient states (Idle/Checking/UpToDate) pass through untouched.
 */
internal fun resolvePostInstallState(state: UpdateUiState, installedVersionCode: Long): UpdateUiState {
    val pending = when (state) {
        is UpdateUiState.UpdateAvailable -> state.info
        is UpdateUiState.Downloading -> state.info
        is UpdateUiState.Verifying -> state.info
        is UpdateUiState.ReadyToInstall -> state.info
        is UpdateUiState.Installing -> state.info
        is UpdateUiState.Cancelled -> state.info
        is UpdateUiState.DownloadFailed -> state.info
        is UpdateUiState.VerificationFailed -> state.info
        is UpdateUiState.UpdateFailed -> state.info
        is UpdateUiState.Idle, is UpdateUiState.Checking, is UpdateUiState.UpToDate -> return state
    }
    return if (pending.versionCode <= installedVersionCode) {
        // The pending update is already installed (or was replaced): start clean.
        UpdateUiState.Idle
    } else {
        state
    }
}
