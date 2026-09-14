package com.moneymanager.app.data.updater

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    private val gson = Gson()

    private fun repo(client: OkHttpClient = OkHttpClient(), ghRepo: String = "acme/money-manager"): UpdateRepository =
        UpdateRepository(ghRepo, client, gson)

    private fun release(
        tag: String,
        body: String = "Changes...",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<GitHubAsset> = emptyList()
    ): GitHubRelease = GitHubRelease(
        tagName = tag,
        name = tag,
        draft = draft,
        prerelease = prerelease,
        htmlUrl = "https://github.com/acme/money-manager/releases/tag/$tag",
        publishedAt = "2026-01-01T00:00:00Z",
        body = body,
        assets = assets
    )

    private fun apk(name: String, size: Long = 37_000_000): GitHubAsset =
        GitHubAsset(name = name, size = size, browserDownloadUrl = "https://github.com/acme/money-manager/releases/download/updategroup$name")

    private fun apkAsset(name: String): GitHubAsset =
        GitHubAsset(name = name, size = 40_000_000, browserDownloadUrl = "https://github.com/acme/money-manager/releases/download/vX/$name")

    @Test
    fun `stable release with matching apk yields Available update`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.5.0", assets = listOf(apk("MoneyManager-1.5.0.apk")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { null })
        assertTrue(result is UpdateCheckResult.Available)
        val info = (result as UpdateCheckResult.Available).update
        assertEquals("1.5.0", info.versionName)
        assertEquals(1_005_000L, info.versionCode)
        assertEquals("v1.5.0", info.tag)
        assertEquals("MoneyManager-1.5.0.apk", info.apkName)
        assertEquals(UpdateConstants.versionCodeOfOrNull(info.versionName)?.toLong(), info.versionCode)
    }

    @Test
    fun `same version is not offered as an update`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.4.2", assets = listOf(apk("MoneyManager-1.4.2.apk")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { null })
        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `older release is not offered as an update`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.4.2", assets = listOf(apk("MoneyManager-1.4.2.apk"))),
            release("v1.3.0", assets = listOf(apk("MoneyManager-1.3.0.apk")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_005_000L, metadataFetcher = { null })
        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `newer prerelease-only repo does not offer update`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.6.0-rc1", prerelease = true, assets = listOf(apk("MoneyManager-1.6.0.apk"))),
            release("v1.4.2", assets = listOf(apk("MoneyManager-1.4.2.apk")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { null })
        // prerelease skipped; stable v1.4.2 == installed -> up to date
        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `draft releases are never offered`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v2.0.0", draft = true, assets = listOf(apk("MoneyManager-2.0.0.apk"))),
            release("v1.0.0", assets = listOf(apk("MoneyManager-1.0.0.apk")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_000_000L, metadataFetcher = { null })
        // v2.0.0 is a draft; newest stable is v1.0.0 == installed
        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `release without production apk is rejected`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.5.0", assets = listOf(GitHubAsset(name = "Source code (zip)", size = 10, browserDownloadUrl = "https://x")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { null })
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `debug apk is not selected as the production update`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release(
                "v1.5.0",
                assets = listOf(
                    apk("MoneyManager-1.5.0-debug.apk"),
                    apk("MoneyManager-1.5.0.apk")
                )
            )
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { null })
        val info = (result as UpdateCheckResult.Available).update
        assertEquals("MoneyManager-1.5.0.apk", info.apkName)
        assertTrue(!info.apkName.contains("debug", ignoreCase = true))
    }

    @Test
    fun `version 1 dot 10 beats 1 dot 9 using versionCode comparison`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.9.0", assets = listOf(apk("MoneyManager-1.9.0.apk"))),
            release("v1.10.0", assets = listOf(apk("MoneyManager-1.10.0.apk")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { null })
        val info = (result as UpdateCheckResult.Available).update
        assertEquals("1.10.0", info.versionName)
        assertEquals(1_010_000L, info.versionCode)
    }

    @Test
    fun `prefer sparse newer checksummed update from metadata`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.5.0", assets = listOf(apk("MoneyManager-1.5.0.apk")))
        )
        val metadata = UpdateMetadata("1.5.0", 1_005_000L, "v1.5.0", "MoneyManager-1.5.0.apk", "a".repeat(64), false)
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { metadata })
        val info = (result as UpdateCheckResult.Available).update
        assertEquals("a".repeat(64), info.expectedSha256)
    }

    @Test
    fun `asset digest takes precedence over manifest checksum`() = runBlocking {
        val r = repo()
        val assetDigest = "b".repeat(64)
        val releases = listOf(
            release(
                "v1.5.0",
                assets = listOf(
                    GitHubAsset(
                        name = "MoneyManager-1.5.0.apk",
                        size = 40_000_000,
                        digest = "sha256:$assetDigest",
                        browserDownloadUrl = "https://github.com/acme/money-manager/releases/download/v1.5.0/MoneyManager-1.5.0.apk"
                    )
                )
            )
        )
        val metadata = UpdateMetadata("1.5.0", 1_005_000L, "v1.5.0", "MoneyManager-1.5.0.apk", "a".repeat(64), false)
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { metadata })
        val info = (result as UpdateCheckResult.Available).update
        assertEquals(assetDigest, info.expectedSha256)
    }

    @Test
    fun `metadata describing another apk fails the release`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.5.0", assets = listOf(apk("MoneyManager-1.5.0.apk")))
        )
        // Manifest claims a different APK than the release actually ships.
        val metadata = UpdateMetadata("1.5.0", 1_005_000L, "v1.5.0", "MoneyManager-9.9.9.apk", "a".repeat(64), false)
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { metadata })
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `metadata with mismatched versionCode fails the release`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.5.0", assets = listOf(apk("MoneyManager-1.5.0.apk")))
        )
        val metadata = UpdateMetadata("1.5.0", 1_006_000L, "v1.5.0", "MoneyManager-1.5.0.apk", "a".repeat(64), false)
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { metadata })
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `consistent metadata is accepted`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.5.0", assets = listOf(apk("MoneyManager-1.5.0.apk")))
        )
        val metadata = UpdateMetadata("1.5.0", 1_005_000L, "v1.5.0", "MoneyManager-1.5.0.apk", "a".repeat(64), true)
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { metadata })
        val info = (result as UpdateCheckResult.Available).update
        assertTrue(info.mandatory)
        assertEquals("a".repeat(64), info.expectedSha256)
    }

    @Test
    fun `non-https download url is rejected`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release(
                "v1.5.0",
                assets = listOf(
                    GitHubAsset(
                        name = "MoneyManager-1.5.0.apk",
                        size = 40_000_000,
                        browserDownloadUrl = "http://insecure.example.com/MoneyManager-1.5.0.apk"
                    )
                )
            )
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { null })
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `release with unparseable tag is rejected`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("not-a-version", assets = listOf(apk("MoneyManager-1.5.0.apk")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { null })
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `empty release list is rejected as invalid release`() = runBlocking {
        val r = repo()
        val result = r.interpretLatestReleases(emptyList(), installedVersionCode = 1_004_002L, metadataFetcher = { null })
        assertTrue(result is UpdateCheckResult.Failed)
        assertEquals(UpdateFailure.INVALID_RELEASE, (result as UpdateCheckResult.Failed).reason)
    }

    @Test
    fun `throwing metadata fetcher does not crash the check`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.5.0", assets = listOf(apk("MoneyManager-1.5.0.apk")))
        )
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { error("boom") })
        // Falls back to tag/asset-name-derived versioning.
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(1_005_000L, (result as UpdateCheckResult.Available).update.versionCode)
    }

    @Test
    fun `parseReleases decodes the GitHub API payload shape`() {
        val json = """
            [
              {"tag_name":"v1.5.0","name":"v1.5.0","draft":false,"prerelease":false,
               "html_url":"https://github.com/acme/money-manager/releases/tag/v1.5.0",
               "published_at":"2026-02-01T00:00:00Z","body":"Fixed things",
               "assets":[
                 {"name":"MoneyManager-1.5.0.apk","size":38000000,"browser_download_url":"https://github.com/acme/money-manager/releases/download/v1.5.0/MoneyManager-1.5.0.apk"}
               ]}
            ]
        """.trimIndent()
        val list: List<GitHubRelease> = gson.fromJson(json, object : TypeToken<List<GitHubRelease>>() {}.type)
        assertEquals(1, list.size)
        assertEquals("v1.5.0", list[0].tagName)
        assertEquals("MoneyManager-1.5.0.apk", list[0].assets[0].name)
    }

    @Test
    fun `parseReleases throws on malformed json instead of failing silently`() {
        val r = repo()
        assertThrows(Exception::class.java) { r.parseReleases("{not json") }
        assertThrows(Exception::class.java) { r.parseReleases("<html>rate limited</html>") }
    }

    @Test
    fun `parseReleases returns empty list for valid non-array payloads`() {
        val r = repo()
        // "null" is valid JSON: upstream treats an empty list as an invalid release.
        assertTrue(r.parseReleases("null").isEmpty())
        assertTrue(r.parseReleases("{}").isEmpty())
    }

    @Test
    fun `parseMetadata decodes the published update json shape`() {
        val r = repo()
        val meta = r.parseMetadata(
            """{"versionName":"1.5.0","versionCode":1005000,"tag":"v1.5.0","apk":"MoneyManager-1.5.0.apk","sha256":"abc123","mandatory":false}"""
        )
        assertEquals(1_005_000L, meta.versionCode)
        assertEquals("abc123", meta.sha256)
        assertEquals("MoneyManager-1.5.0.apk", meta.apk)
        assertTrue(meta.isComplete)
    }

    @Test
    fun `updateMetadata gson round-trips the published shape`() {
        val json = """{"versionName":"1.5.0","versionCode":1005000,"tag":"v1.5.0","apk":"MoneyManager-1.5.0.apk","sha256":"abc123","mandatory":false}"""
        val meta = gson.fromJson(json, UpdateMetadata::class.java)
        assertEquals(1_005_000L, meta.versionCode)
        assertEquals("abc123", meta.sha256)
        assertEquals("MoneyManager-1.5.0.apk", meta.apk)
        assertTrue(meta.isComplete)
    }

    @Test
    fun `incomplete metadata never blocks the tag-derived fallback`() = runBlocking {
        val r = repo()
        val releases = listOf(
            release("v1.5.0", assets = listOf(apk("MoneyManager-1.5.0.apk")))
        )
        // sha256 blank → isComplete false → cross-check skipped, tag fallback used.
        val metadata = UpdateMetadata("1.5.0", 1_005_000L, "v1.5.0", "", "", false)
        val result = r.interpretLatestReleases(releases, installedVersionCode = 1_004_002L, metadataFetcher = { metadata })
        assertTrue(result is UpdateCheckResult.Available)
        assertNull((result as UpdateCheckResult.Available).update.expectedSha256)
    }
}
