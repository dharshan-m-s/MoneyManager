package com.moneymanager.app.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateModelsTest {

    // --- versionCodeOf / versionCodeOfOrNull ---

    @Test
    fun `versionCodeOf uses deterministic major-minor-patch mapping`() {
        assertEquals(1_004_002, UpdateConstants.versionCodeOf(1, 4, 2))
        assertEquals(2_000_000, UpdateConstants.versionCodeOf(2, 0, 0))
        assertEquals(1_010_000, UpdateConstants.versionCodeOf(1, 10, 0))
        assertEquals(1_009_000, UpdateConstants.versionCodeOf(1, 9, 0))
    }

    @Test
    fun `versionCodeOfOrNull parses semantic versions`() {
        assertEquals(1_004_002, UpdateConstants.versionCodeOfOrNull("1.4.2"))
        assertEquals(1_004_002, UpdateConstants.versionCodeOfOrNull("v1.4.2"))
        assertEquals(1_004_002, UpdateConstants.versionCodeOfOrNull(" 1.4.2 "))
        assertEquals(1_010_000, UpdateConstants.versionCodeOfOrNull("1.10.0"))
        assertEquals(2_000_000, UpdateConstants.versionCodeOfOrNull("2.0.0"))
    }

    @Test
    fun `versionCodeOfOrNull correctly orders 1 dot 9 before 1 dot 10 before 2 dot 0`() {
        val v19 = UpdateConstants.versionCodeOfOrNull("1.9")  // malformed, 2 parts
        val v190 = UpdateConstants.versionCodeOfOrNull("1.9.0")
        val v110 = UpdateConstants.versionCodeOfOrNull("1.10.0")
        val v200 = UpdateConstants.versionCodeOfOrNull("2.0.0")
        assertNull(v19)
        assertNotNull(v190)
        val sorted = listOf(v190!!, v110!!, v200!!).sorted()
        assertEquals(listOf(1_009_000, 1_010_000, 2_000_000), sorted)
        assertTrue(v110!! > v190)
        assertTrue(v200!! > v110)
    }

    @Test
    fun `versionCodeOfOrNull rejects malformed and prerelease versions`() {
        assertNull(UpdateConstants.versionCodeOfOrNull("1.4.2-beta"))
        assertNull(UpdateConstants.versionCodeOfOrNull("1.4"))
        assertNull(UpdateConstants.versionCodeOfOrNull("v1"))
        assertNull(UpdateConstants.versionCodeOfOrNull("1.4.2.3"))
        assertNull(UpdateConstants.versionCodeOfOrNull(""))
        assertNull(UpdateConstants.versionCodeOfOrNull("one.four.two"))
        assertNull(UpdateConstants.versionCodeOfOrNull("1.-4.2"))
        assertNull(UpdateConstants.versionCodeOfOrNull("0.0.0"))
    }

    @Test
    fun `versionCodeOfOrNull rejects overflow collisions like 1 dot 1 dot 1000`() {
        // Without the 999 cap, "1.1.1000" would map to the same code as "1.2.0".
        assertNull(UpdateConstants.versionCodeOfOrNull("1.1.1000"))
        assertNull(UpdateConstants.versionCodeOfOrNull("1.1000.0"))
        // leading zeros are digits and within the 3-char cap → valid shape
        assertEquals(1_002_003, UpdateConstants.versionCodeOfOrNull("01.002.003"))
    }

    @Test
    fun `versionCodeOfOrNull accepts boundary values up to 999`() {
        assertEquals(1_001_999, UpdateConstants.versionCodeOfOrNull("1.1.999"))
        assertEquals(1_999_999, UpdateConstants.versionCodeOfOrNull("1.999.999"))
        assertEquals(2_100_000, UpdateConstants.versionCodeOfOrNull("2.100.0"))
    }

    // --- GitHubAsset digest ---

    @Test
    fun `asset digest extracts sha256 hex`() {
        val hex = "a".repeat(64)
        val asset = GitHubAsset(name = "x.apk", digest = "sha256:$hex", browserDownloadUrl = "https://x/x.apk")
        assertEquals(hex, asset.sha256Digest)
    }

    @Test
    fun `asset digest is null for missing or non-sha256 digests`() {
        assertNull(GitHubAsset(name = "x.apk").sha256Digest)
        assertNull(GitHubAsset(name = "x.apk", digest = "md5:abc").sha256Digest)
        assertNull(GitHubAsset(name = "x.apk", digest = "sha256:").sha256Digest)
    }

    // --- UpdateInfo ---

    @Test
    fun `updateInfo echo normalization`() {
        val info = UpdateInfo(
            versionName = "1.5.0",
            versionCode = 1_005_000L,
            tag = "v1.5.0",
            apkName = "MoneyManager-1.5.0.apk",
            apkUrl = "https://example.com/update.apk",
            expectedSha256 = "  ABC123  ",
            downloadSizeBytes = 40_000_000,
            whatIsNew = "- feature",
            mandatory = false
        )
        assertNotNull(info.expectedSha256Lower)
        assertEquals("abc123", info.expectedSha256Lower)
        assertEquals(UpdateConstants.versionCodeOfOrNull(info.versionName)?.toLong(), info.versionCode)
    }

    // --- UpdateMetadata ---

    @Test
    fun `updateMetadata isComplete requires all production fields`() {
        val good = UpdateMetadata(
            versionName = "1.5.0",
            versionCode = 1_005_000L,
            tag = "v1.5.0",
            apk = "MoneyManager-1.5.0.apk",
            sha256 = "a".repeat(64),
            mandatory = false
        )
        assertTrue(good.isComplete)
        assertFalse(good.copy(sha256 = "").isComplete)
        assertFalse(good.copy(versionCode = 0).isComplete)
        assertFalse(good.copy(apk = "").isComplete)
        assertFalse(good.copy(versionName = "").isComplete)
    }

    // --- UpdateUiState sanity ---

    @Test
    fun `uiState carries download progress within bounds`() {
        val info = UpdateInfo("1.5.0", 1_005_000L, "v1.5.0", "M.apk", "https://x/apk", "hash", 1000, "", false)
        val small = UpdateUiState.Downloading(info, 0.3f, 300, 1000)
        assertEquals(0.3f, small.fraction, 0.001f)
        assertTrue(small.fraction in 0f..1f)
    }

    @Test
    fun `installing state carries the apk path for recovery`() {
        val info = UpdateInfo("1.5.0", 1_005_000L, "v1.5.0", "M.apk", "https://x/apk", "hash", 1000, "", false)
        val state = UpdateUiState.Installing(info, "/data/cache/updates/M.apk")
        assertEquals("/data/cache/updates/M.apk", state.localFilePath)
        assertEquals(info, state.info)
    }
}
