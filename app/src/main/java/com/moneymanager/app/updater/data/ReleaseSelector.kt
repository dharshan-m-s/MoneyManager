package com.moneymanager.app.updater.data

import com.moneymanager.app.updater.model.GitHubAsset
import com.moneymanager.app.updater.model.GitHubRelease

/**
 * Picks the update candidate from the GitHub Releases API response.
 *
 * - [selectLatestStable] ignores drafts and prereleases and returns the highest
 *   semantic version among the stable releases.
 * - [selectApkAsset] prefers the deterministic asset name produced by the release
 *   workflow (`MoneyManager-v<version>-release.apk`), falling back to any APK asset
 *   that matches the release version.
 */
object ReleaseSelector {

    fun selectLatestStable(releases: List<GitHubRelease>): GitHubRelease? =
        releases
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { release ->
                VersionComparator.parse(release.tagName)?.let { version -> version to release }
            }
            .maxByOrNull { it.first }
            ?.second

    fun selectApkAsset(release: GitHubRelease, version: SemVersion): GitHubAsset? {
        val deterministicName = "MoneyManager-v$version-release.apk"
        return release.assets.firstOrNull { it.name == deterministicName }
            ?: release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) &&
                    VersionComparator.parse(release.tagName) == version
            }
    }
}