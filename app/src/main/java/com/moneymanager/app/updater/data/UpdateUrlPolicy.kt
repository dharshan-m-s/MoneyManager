package com.moneymanager.app.updater.data

import java.net.URI

/**
 * Allowlist for every URL the updater is willing to fetch.
 *
 * Release JSON is attacker-influenced metadata: it can point `browser_download_url` anywhere.
 * TLS alone is not enough, so a URL must be HTTPS, must not carry user info or a fragment, and
 * must resolve to GitHub's API, release site, or official asset CDN before a single byte is read.
 */
internal object UpdateUrlPolicy {

    private val API_HOSTS = setOf("api.github.com")
    private val ASSET_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )
    private val RELEASE_TAG = Regex("^[A-Za-z0-9._-]+$")

    fun isAllowedApiUrl(raw: String?): Boolean = isAllowed(raw, API_HOSTS, false)

    fun isAllowedAssetUrl(raw: String?): Boolean = isAllowed(raw, ASSET_HOSTS, true)

    fun requireAllowedAssetUrl(raw: String?) {
        check(isAllowedAssetUrl(raw)) { "Update URL was rejected: not an official GitHub HTTPS asset URL" }
    }

    /** Builds the release-page URI from validated components so a tag can never alter the path. */
    fun releasePageUrl(owner: String, repository: String, tag: String): String? {
        if (!RELEASE_TAG.matches(tag) || owner.isBlank() || repository.isBlank()) return null
        return URI("https", "github.com", "/$owner/$repository/releases/tag/$tag", null, null).toString()
    }

    private fun isAllowed(raw: String?, exactHosts: Set<String>, allowGithubusercontent: Boolean): Boolean {
        if (raw.isNullOrBlank()) return false
        val uri = runCatching { URI.create(raw) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.userInfo != null || uri.fragment != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        return host in exactHosts || (allowGithubusercontent && host.endsWith(".githubusercontent.com"))
    }
}
