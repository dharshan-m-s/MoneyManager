package com.moneymanager.app.updater.presentation

import android.app.Application
import android.content.Context
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
 * Release checking is done by the app's normal repository, which resolves the published
 * SHA-256 before handing the candidate to AppUpdateChecker. The checker downloads, verifies,
 * and installs that exact candidate and exposes its progress flow for the update screens.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UpdateRepository(app)
    private val prefs = app.getSharedPreferences(UpdateWorker.PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    val downloadState = AppUpdateChecker.download
    private var job: Job? = null
    private var downloadJob: Job? = null

    init {
        // Clear any stale update APK and check once when the update screen is opened.
        // This must stay here instead of moving to Application.onCreate().
        checkForUpdates()
    }

    fun checkForUpdates() {
        job?.cancel()
        downloadJob?.cancel()
        AppUpdateChecker.resetDownload()
        AppUpdateChecker.clearPrepared()
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
            AppUpdateChecker.clearCache(getApplication())
            when (val result = repository.checkForUpdate()) {
                is UpdateRepository.CheckResult.Available -> {
                    // The repository already resolved the published SHA-256; the installer uses
                    // that exact candidate instead of issuing its own unverified release request.
                    AppUpdateChecker.prepare(result.update)
                    _state.value = UpdateState.UpdateAvailable(
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE.toLong(),
                        result.update
                    )
                }
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
        downloadJob?.cancel()
        AppUpdateChecker.cancelDownload()
        downloadJob = viewModelScope.launch { AppUpdateChecker.downloadApk(getApplication()) }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        AppUpdateChecker.cancelDownload()
    }

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
