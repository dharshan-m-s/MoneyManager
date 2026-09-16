package com.moneymanager.app.updater

import com.moneymanager.app.updater.data.ReleaseSelector
import com.moneymanager.app.updater.data.VersionComparator
import com.moneymanager.app.updater.model.GitHubAsset
import com.moneymanager.app.updater.model.GitHubRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateLogicTest {
    @Test
    fun versionComparisonHandlesTenAfterNine() {
        val nine = VersionComparator.parse("1.9.0")!!
        val ten = VersionComparator.parse("1.10.0")!!
        assertEquals(-1, VersionComparator.compare(nine, ten))
        assertEquals(1, VersionComparator.compare(ten, nine))
    }

    @Test
    fun versionCodeMatchesReleasePipelineFormula() {
        val version = VersionComparator.parse("1.2.3")!!
        assertEquals(1_002_003L, VersionComparator.toVersionCode(version))
    }

    @Test
    fun latestStableIgnoresDraftAndPrerelease() {
        val releases = listOf(
            GitHubRelease(tagName = "v2.0.0", prerelease = true, publishedAt = "2026-09-10T10:00:00Z"),
            GitHubRelease(tagName = "v1.1.0", draft = true, publishedAt = "2026-09-09T10:00:00Z"),
            GitHubRelease(tagName = "v1.0.1", publishedAt = "2026-09-08T10:00:00Z")
        )
        assertEquals("v1.0.1", ReleaseSelector.selectLatestStable(releases)?.tagName)
    }

    @Test
    fun apkAssetSelectionPrefersDeterministicName() {
        val release = GitHubRelease(
            tagName = "v1.2.0",
            assets = listOf(
                GitHubAsset(name = "notes.txt", browserDownloadUrl = "https://example/notes"),
                GitHubAsset(name = "MoneyManager-v1.2.0-release.apk", browserDownloadUrl = "https://example/app"),
                GitHubAsset(name = "MoneyManager-v1.2.0.sha256", browserDownloadUrl = "https://example/hash")
            )
        )
        val version = VersionComparator.parse("1.2.0")!!
        val apk = ReleaseSelector.selectApkAsset(release, version)
        assertNotNull(apk)
        assertEquals("MoneyManager-v1.2.0-release.apk", apk?.name)
    }

    @Test
    fun apkAssetSelectionReturnsNullWhenMissing() {
        val release = GitHubRelease(tagName = "v1.2.0", assets = listOf(GitHubAsset(name = "notes.txt")))
        assertNull(ReleaseSelector.selectApkAsset(release, VersionComparator.parse("1.2.0")!!))
    }
}
