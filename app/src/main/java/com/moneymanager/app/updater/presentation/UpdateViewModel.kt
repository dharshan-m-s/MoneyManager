package com.moneymanager.app.updater.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.data.AppUpdateChecker
import com.moneymanager.app.updater.data.UpdateRepository
import com.moneymanager.app.updater.data.UpdateWorker
import com.moneymanager.app.updater.domain.UpdateScheduler
import com.moneymanager.app.updater.model.UpdateError
import com.moneymanager.app.updater.model.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Release checking is done by the app's normal repository, while the actual
 * APK download/install path is BitChord's AppUpdateChecker copied verbatim.
 * The checker exposes its own download progress flow for the update screens.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UpdateRepository(app)
    private val prefs = app.getSharedPreferences(UpdateWorker.PREFS, 0)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    val downloadState = AppUpdateChecker.download
    private var job: Job? = null

    init {
        // BitChord's cold-start clearCache + check, once per ViewModel lifetime.
        checkForUpdates()
    }

    fun checkForUpdates() {
        job?.cancel()
        AppUpdateChecker.resetDownload()
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
            AppUpdateChecker.clearCache(getApplication())
            AppUpdateChecker.check()
            when (val result = repository.checkForUpdate()) {
                is UpdateRepository.CheckResult.Available ->
                    _state.value = UpdateState.UpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE.toLong(),
                        result.update
                    )
                is UpdateRepository.CheckResult.UpToDate ->
                    _state.value = UpdateState.UpToDate(result.versionName)
                is UpdateRepository.CheckResult.Error ->
                    _state.value = if (result.error == UpdateError.NoInternet)
                        UpdateState.Offline
                    else
                        UpdateState.Error(result.error)
            }
        }
    }

    fun downloadUpdate() {
        viewModelScope.launch { AppUpdateChecker.downloadApk(getApplication()) }
    }

    fun cancelDownload() = AppUpdateChecker.cancelDownload()

    fun installUpdate() {
        val ready = AppUpdateChecker.download.value as? AppUpdateChecker.DownloadState.Ready
        ready?.let { AppUpdateChecker.installApk(getApplication(), it.file) }
    }

    fun resetDownload() = AppUpdateChecker.resetDownload()

    fun autoUpdateEnabled(): Boolean = prefs.getBoolean(UpdateWorker.AUTO_UPDATE_KEY, false)

    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(UpdateWorker.AUTO_UPDATE_KEY, enabled).apply()
        UpdateScheduler.setEnabled(getApplication(), enabled)
    }
}
