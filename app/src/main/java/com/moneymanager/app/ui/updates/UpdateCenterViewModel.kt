package com.moneymanager.app.ui.updates

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.data.updater.UpdateManager
import com.moneymanager.app.data.updater.UpdateInfo
import com.moneymanager.app.data.updater.UpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateCenterViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _lastChecked = MutableStateFlow<Long?>(null)
    val lastChecked: StateFlow<Long?> = _lastChecked.asStateFlow()

    /** Installed version info captured once from the platform. */
    private val installedPackageInfo: android.content.pm.PackageInfo? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            null
        }
    }

    val installedVersionCode: Long
        get() = installedPackageInfo?.longVersionCode
            ?: com.moneymanager.app.BuildConfig.VERSION_CODE.toLong()

    val installedVersionName: String
        get() = installedPackageInfo?.versionName?.takeIf { it.isNotBlank() }
            ?: com.moneymanager.app.BuildConfig.VERSION_NAME

    val uiState: StateFlow<UpdateUiState> = updateManager.uiState

    /**
     * First composition entry: reconcile state with the actually-installed version
     * (post-install / cancelled-install detection), then auto-check unless an
     * in-progress flow already owns the screen (e.g. a background download).
     */
    fun onScreenOpen() {
        updateManager.syncInstallationOutcome()
        maybeAutoCheck()
    }

    /**
     * Every resume (e.g. returning from the Android installer): reconcile first —
     * if the update was installed while we were away, the state drops to Idle and
     * the auto-check below refreshes against the new version.
     */
    fun onScreenResume() {
        updateManager.syncInstallationOutcome()
        maybeAutoCheck()
    }

    private fun maybeAutoCheck() {
        val state = uiState.value
        val busy = state is UpdateUiState.Downloading ||
            state is UpdateUiState.Verifying ||
            state is UpdateUiState.Installing ||
            state is UpdateUiState.ReadyToInstall ||
            updateManager.isDownloadInProgress
        if (!busy) checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            updateManager.checkForUpdate(
                installedVersionCode = installedVersionCode,
                installedVersionName = installedVersionName
            )
            _lastChecked.value = System.currentTimeMillis()
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        viewModelScope.launch {
            updateManager.downloadAndVerify(info)
        }
    }

    fun installUpdate(info: UpdateInfo, apkPath: String) {
        updateManager.installUpdate(info, apkPath)
    }

    fun cancelDownload() {
        updateManager.cancelDownload()
    }
}
