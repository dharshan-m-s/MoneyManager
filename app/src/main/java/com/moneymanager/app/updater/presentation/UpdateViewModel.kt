package com.moneymanager.app.updater.presentation

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.app.BuildConfig
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
 * Checks GitHub for the latest release and exposes its notes. The app never downloads or
 * installs APKs itself: an available update opens the release page so the user downloads and
 * installs it. This avoids requesting REQUEST_INSTALL_PACKAGES, which Play Protect treats as
 * a harmful-app signal for sideloaded apps.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UpdateRepository(app)
    private val prefs = app.getSharedPreferences(UpdateWorker.PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        job?.cancel()
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
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

    fun autoUpdateEnabled(): Boolean = prefs.getBoolean(UpdateWorker.AUTO_UPDATE_KEY, false)

    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(UpdateWorker.AUTO_UPDATE_KEY, enabled).apply()
        UpdateScheduler.setEnabled(getApplication(), enabled)
    }
}
