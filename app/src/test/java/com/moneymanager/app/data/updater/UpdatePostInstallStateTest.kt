package com.moneymanager.app.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure post-install state resolution used after the process
 * restarts (update installed / installation cancelled).
 */
class UpdatePostInstallStateTest {

    private val info = UpdateInfo(
        versionName = "1.5.0",
        versionCode = 1_005_000L,
        tag = "v1.5.0",
        apkName = "MoneyManager-1.5.0.apk",
        apkUrl = "https://example.com/MoneyManager-1.5.0.apk",
        expectedSha256 = "a".repeat(64),
        downloadSizeBytes = 40_000_000,
        whatIsNew = "Fixes",
        mandatory = false
    )

    private fun installed(versionCode: Long): Long = versionCode

    @Test
    fun `installed pending update collapses every carried state to Idle`() {
        val states = listOf(
            UpdateUiState.UpdateAvailable(info, "1.4.2"),
            UpdateUiState.Downloading(info, 0.5f, 100, 200),
            UpdateUiState.Verifying(info),
            UpdateUiState.ReadyToInstall(info, "/cache/updates/MoneyManager-1.5.0.apk"),
            UpdateUiState.Installing(info, "/cache/updates/MoneyManager-1.5.0.apk"),
            UpdateUiState.Cancelled(info),
            UpdateUiState.DownloadFailed(info, "x"),
            UpdateUiState.VerificationFailed(info, "x"),
            UpdateUiState.UpdateFailed(info, "x")
        )
        val installedCode = installed(1_005_000L) // update already installed
        states.forEach { state ->
            assertEquals(
                "state $state must reset to Idle after its update is installed",
                UpdateUiState.Idle,
                resolvePostInstallState(state, installedCode)
            )
        }
    }

    @Test
    fun `pending newer update is preserved across restart`() {
        val installedCode = installed(1_004_002L) // 1.4.2 still installed
        val resolved = resolvePostInstallState(
            UpdateUiState.ReadyToInstall(info, "/cache/updates/MoneyManager-1.5.0.apk"),
            installedCode
        )
        assertTrue(resolved is UpdateUiState.ReadyToInstall)
    }

    @Test
    fun `transient states never carry state and pass through untouched`() {
        val installedCode = installed(1_004_002L)
        assertEquals(UpdateUiState.Idle, resolvePostInstallState(UpdateUiState.Idle, installedCode))
        assertEquals(UpdateUiState.Checking, resolvePostInstallState(UpdateUiState.Checking, installedCode))
        assertEquals(UpdateUiState.UpToDate, resolvePostInstallState(UpdateUiState.UpToDate, installedCode))
    }
}
