package com.moneymanager.app.updater

import com.moneymanager.app.data.AppUpdateChecker
import com.moneymanager.app.updater.model.ReleaseUpdate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The verified installer must never act on a stale or unverifiable release candidate.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateInstallVerificationTest {

    private val digest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private fun releaseUpdate(checksum: String?) = ReleaseUpdate(
        versionName = "1.2.3",
        versionCode = 1_002_003L,
        tag = "v1.2.3",
        title = "Money Manager 1.2.3",
        releaseNotes = "notes",
        apkUrl = "https://github.com/example/app.apk",
        apkName = "MoneyManager-v1.2.3-release.apk",
        expectedSha256 = checksum,
    )

    @Test
    fun prepareStoresOnlyTheRepositoryResolvedChecksum() {
        AppUpdateChecker.prepare(releaseUpdate("sha256:${digest.uppercase()}"))
        val prepared = AppUpdateChecker.available.value
        assertNotNull(prepared)
        assertEquals("1.2.3", prepared?.version)
        assertEquals("sha256:$digest", prepared?.expectedSha256)
        assertEquals("https://github.com/example/app.apk", prepared?.apkUrl)
    }

    @Test
    fun malformedChecksumIsNeverTreatedAsVerified() {
        AppUpdateChecker.prepare(releaseUpdate("md5:$digest"))
        assertNull(AppUpdateChecker.available.value?.expectedSha256)
    }

    @Test
    fun clearPreparedRemovesTheCandidate() {
        AppUpdateChecker.prepare(releaseUpdate("sha256:$digest"))
        AppUpdateChecker.clearPrepared()
        assertNull(AppUpdateChecker.available.value)
    }

    @Test
    fun downloadWithoutPreparedUpdateFailsClosed() = runBlocking {
        AppUpdateChecker.clearPrepared()
        AppUpdateChecker.resetDownload()
        AppUpdateChecker.downloadApk(android.app.Application())
        val state = AppUpdateChecker.download.value
        assertTrue(state is AppUpdateChecker.DownloadState.Failed)
    }
}
