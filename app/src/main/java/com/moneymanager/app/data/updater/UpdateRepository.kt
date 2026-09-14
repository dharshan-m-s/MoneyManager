package com.moneymanager.app.data.updater

import com.google.gson.Gson
import com.google.gson.JsonParser
import timber.log.Timber
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Fetches and interprets the latest stable GitHub Release for this project.
 *
 * Pure decision logic lives in [interpretLatestReleases] so it can be unit-tested
 * without the network. The only purpose of the I/O path is HTTPS retrieval of the
 * release list + the update.json metadata asset (unauthenticated, public API).
 */
class UpdateRepository(
    private val repo: String,
    private val client: OkHttpClient,
    private val gson: Gson
) {

    /** True when a real "owner/name" repository is configured at build time. */
    val repoConfigured: Boolean get() = repo.isNotBlank()

    /** "owner/name" path, or an empty string when unset. */
    fun repoName(): String = repo

    private val metadataFetcher: suspend (String) -> UpdateMetadata? = { tag ->
        fetchMetadataForRelease(tag)
    }

    /**
     * Requests the latest releases (per_page=10 gives enough history to skip
     * drafts/prereleases that might be published newest-first) and returns the
     * newest stable, parseable update, or a failure.
     */
    suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult {
        if (repo.isBlank()) {
            return UpdateCheckResult.Failed(UpdateFailure.INVALID_RELEASE)
        }
        val releasesJson = try {
            fetch(
                url = "${UpdateConstants.API_BASE}/repos/$repo/releases?per_page=10",
                accept = "application/vnd.github+json"
            )
        } catch (e: IOException) {
            Timber.w(e, "GitHub release check failed")
            return UpdateCheckResult.Failed(UpdateFailure.GITHUB_UNAVAILABLE)
        }
        val releases = try {
            parseReleases(releasesJson)
        } catch (e: Exception) {
            Timber.w(e, "GitHub releases payload is malformed")
            return UpdateCheckResult.Failed(UpdateFailure.INVALID_RELEASE)
        }
        return interpretLatestReleases(releases, installedVersionCode, metadataFetcher)
    }

    private fun fetch(url: String, accept: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body?.string() ?: throw IOException("empty body from $url")
            if (body.isBlank()) throw IOException("blank body from $url")
            return body
        }
    }

    /** @throws Exception when the payload is not a JSON array of releases. */
    internal fun parseReleases(json: String): List<GitHubRelease> {
        val element = JsonParser.parseString(json)
        if (!element.isJsonArray) return emptyList()
        val array = element.asJsonArray
        val type = object : com.google.gson.reflect.TypeToken<List<GitHubRelease>>() {}.type
        return gson.fromJson(array, type) ?: emptyList()
    }

    /** @throws Exception when the payload is not a valid update.json object. */
    internal fun parseMetadata(json: String): UpdateMetadata = gson.fromJson(json, UpdateMetadata::class.java)

    /**
     * Collapses a fetched release list to one [UpdateCheckResult].
     * Comparisons are integer versionCode based – never naive string comparison.
     */
    internal suspend fun interpretLatestReleases(
        releases: List<GitHubRelease>,
        installedVersionCode: Long,
        metadataFetcher: suspend (String) -> UpdateMetadata?
    ): UpdateCheckResult {
        // Only stable, published releases: drafts and prereleases are skipped entirely.
        val stable = releases
            .filterNot { it.draft || it.prerelease }
            .sortedByDescending { UpdateConstants.versionCodeOfOrNull(it.tagName) ?: 0 }

        if (stable.isEmpty()) {
            return UpdateCheckResult.Failed(UpdateFailure.INVALID_RELEASE)
        }

        val latest = stable.first()
        val versionFromTag = UpdateConstants.versionCodeOfOrNull(latest.tagName)
        if (versionFromTag == null) {
            return UpdateCheckResult.Failed(UpdateFailure.INVALID_RELEASE)
        }

        // Locate the production APK asset (reject source archives, debug builds,
        // path traversals, and anything that is not a plain deterministic filename).
        val apkAsset = latest.assets
            .filter { it.name.endsWith(".apk", ignoreCase = true) }
            .filterNot { it.name.contains("-debug", ignoreCase = true) }
            .filter { isPlainFileName(it.name) }
            .sortedByDescending { parseApkVersionCode(it.name) }
            .firstOrNull()
        if (apkAsset == null) {
            return UpdateCheckResult.Failed(UpdateFailure.INVALID_RELEASE)
        }

        val metadata = try {
            metadataFetcher(latest.tagName)
        } catch (e: Exception) {
            Timber.w(e, "update.json for ${latest.tagName} could not be parsed")
            null
        }

        // Cross-check: the manifest must describe the exact release/APK it ships with.
        if (metadata != null && metadata.isComplete) {
            val consistent = metadata.apk.equals(apkAsset.name, ignoreCase = true) &&
                metadata.versionCode == versionFromTag.toLong()
            if (!consistent) {
                Timber.w(
                    "update.json for %s does not match the release assets; ignoring it",
                    latest.tagName
                )
                return UpdateCheckResult.Failed(UpdateFailure.INVALID_RELEASE)
            }
        }

        val versionName = metadata?.versionName?.takeIf { it.isNotBlank() }
            ?: apkAsset.name.removePrefix(UpdateConstants.APK_FILE_PREFIX).removeSuffix(".apk")
        val versionCode = metadata?.versionCode?.takeIf { it > 0 }
            ?: UpdateConstants.versionCodeOfOrNull(versionName)?.toLong()
            ?: versionFromTag.toLong()

        val apkUrl = apkAsset.browserDownloadUrl
        // Fail closed: never trust or expose a non-HTTPS download URL.
        if (!apkUrl.startsWith("https://", ignoreCase = true)) {
            return UpdateCheckResult.Failed(UpdateFailure.INVALID_RELEASE)
        }

        val isNewer = versionCode > installedVersionCode
        return if (isNewer) {
            // Prefer the API-published asset digest; fall back to the manifest digest.
            val expectedSha = apkAsset.sha256Digest ?: metadata?.sha256?.takeIf { it.isNotBlank() }
            UpdateCheckResult.Available(
                UpdateInfo(
                    versionName = versionName,
                    versionCode = versionCode,
                    tag = latest.tagName,
                    apkName = apkAsset.name,
                    apkUrl = apkUrl,
                    expectedSha256 = expectedSha,
                    downloadSizeBytes = apkAsset.size,
                    whatIsNew = latest.body.ifBlank { latest.name },
                    mandatory = metadata?.mandatory ?: false
                )
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /** Parses the versionCode from an APK asset filename ("MoneyManager-1.4.2.apk"). */
    internal fun parseApkVersionCode(apkName: String): Int {
        val base = apkName.removePrefix(UpdateConstants.APK_FILE_PREFIX).removeSuffix(".apk")
        return UpdateConstants.versionCodeOfOrNull(base) ?: 0
    }

    /** True when [name] is a plain file name (no directories, no traversal, non-blank). */
    internal fun isPlainFileName(name: String): Boolean {
        if (name.isBlank()) return false
        if (name != File(name).name) return false // normalizes "../x", "a/b", etc.
        return !name.contains('\\') && !name.contains("..")
    }

    private suspend fun fetchMetadataForRelease(tag: String): UpdateMetadata? {
        val url = "${UpdateConstants.API_BASE}/repos/$repo/releases/tags/$tag"
        val json = try {
            fetch(url, "application/vnd.github+json")
        } catch (e: IOException) {
            Timber.w(e, "Could not fetch release metadata for %s", tag)
            return null
        }
        val obj = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            Timber.w(e, "Malformed release payload for %s", tag)
            return null
        }
        if (!obj.isJsonObject) return null

        val assetUrl = obj.asJsonObject.getAsJsonArray("assets")?.firstOrNull {
            it.isJsonObject && it.asJsonObject.get("name")?.asString == UpdateConstants.UPDATE_METADATA_FILE
        }?.asJsonObject?.get("browser_download_url")?.asString

        if (assetUrl.isNullOrBlank()) return null
        if (!assetUrl.startsWith("https://", ignoreCase = true)) return null
        val metaJson = try {
            fetch(assetUrl, "application/json")
        } catch (e: IOException) {
            Timber.w(e, "Could not fetch update.json for %s", tag)
            return null
        }
        return try {
            parseMetadata(metaJson)
        } catch (e: Exception) {
            Timber.w(e, "update.json for %s is malformed", tag)
            null
        }
    }
}
