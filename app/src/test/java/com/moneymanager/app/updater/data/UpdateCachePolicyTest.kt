package com.moneymanager.app.updater.data

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Section 14: the update cache policy is explicit and tested. `deleteApksExcept` must keep exactly
 * the APK for the version about to be installed, remove every other APK, and prune abandoned
 * `.part` downloads without ever deleting a pending valid installation or its in-flight partial.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UpdateCachePolicyTest {

    private lateinit var repo: UpdateRepository
    private lateinit var dir: File

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        repo = UpdateRepository(context)
        dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun file(name: String): File = File(dir, name).apply { writeText("x") }

    @Test
    fun keepsOnlyTheTargetVersionAndPrunesStaleParts() {
        val target = file("MoneyManager-v2.4.0-release.apk")
        val oldApk = file("MoneyManager-v2.3.0-release.apk")
        val stalePart = file("MoneyManager-v2.3.0-release.apk.part")
        val targetPart = file("MoneyManager-v2.4.0-release.apk.part")

        repo.deleteApksExcept("2.4.0")

        assertTrue("target APK must be kept", target.exists())
        assertFalse("old APK must be removed", oldApk.exists())
        assertFalse("stale .part for another version must be removed", stalePart.exists())
        assertTrue("in-flight .part for the target version must be kept", targetPart.exists())
    }
}
