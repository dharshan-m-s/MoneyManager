package com.moneymanager.app.updater.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.data.UpdateRepository
import com.moneymanager.app.updater.data.UpdateWorker
import com.moneymanager.app.updater.domain.ApkVerifier
import com.moneymanager.app.updater.domain.UpdateScheduler
import com.moneymanager.app.updater.model.ReleaseUpdate
import com.moneymanager.app.updater.model.UpdateDiagnostics
import com.moneymanager.app.updater.model.UpdateError
import com.moneymanager.app.updater.model.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UpdateRepository(app)
    private val verifier = ApkVerifier(app)
    private val prefs = app.getSharedPreferences(UpdateWorker.PREFS, 0)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _diagnostics = MutableStateFlow<UpdateDiagnostics>(repository.lastDiagnostics)
    val diagnostics: StateFlow<UpdateDiagnostics> = _diagnostics.asStateFlow()
    private var job: Job? = null
    private var pendingDownloadedFile: File? = null

    init {
        restorePending()
    }

    fun checkForUpdates() {
        job?.cancel()
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
            when (val result = repository.checkForUpdate()) {
                is UpdateRepository.CheckResult.Available -> _state.value = UpdateState.UpdateAvailable(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), result.update)
                is UpdateRepository.CheckResult.UpToDate -> _state.value = UpdateState.UpToDate(result.versionName)
                is UpdateRepository.CheckResult.Error -> _state.value = if (result.error == UpdateError.NoInternet) UpdateState.Offline else UpdateState.Error(result.error)
            }
            _diagnostics.value = repository.lastDiagnostics.copy(lastCheckedAt = System.currentTimeMillis())
        }
    }

    fun download(update: ReleaseUpdate) {
        job?.cancel()
        job = viewModelScope.launch {
            repository.download(update) { progress, downloaded, total ->
                _state.value = UpdateState.Downloading(update, progress, downloaded, total)
            }.onSuccess { file ->
                repository.deleteOldVersions(file)
                val verification = verifier.verify(file, update.versionCode)
                if (verification.isFailure) {
                    file.delete()
                    _state.value = UpdateState.Error(UpdateError.VerificationFailed)
                } else {
                    pendingDownloadedFile = file
                    _state.value = UpdateState.Downloaded(update, file.absolutePath)
                    prefs.edit().putString(UpdateWorker.PENDING_UPDATE_KEY, Gson().toJson(update)).apply()
                }
            }.onFailure {
                _state.value = UpdateState.Error(UpdateError.DownloadFailed)
            }
        }
    }

    fun markInstalling() {
        _state.value = UpdateState.Installing
    }

    fun pendingFile(): File? = pendingDownloadedFile?.takeIf { it.exists() }

    fun verifyPostInstall() {
        val pending = runCatching { Gson().fromJson(prefs.getString(UpdateWorker.PENDING_UPDATE_KEY, null), ReleaseUpdate::class.java) }.getOrNull() ?: return
        if (BuildConfig.VERSION_CODE.toLong() >= pending.versionCode) {
            prefs.edit().remove(UpdateWorker.PENDING_UPDATE_KEY).putString(UpdateWorker.INSTALLED_UPDATE_KEY, pending.versionName).apply()
            repository.cleanCache()
            pendingDownloadedFile = null
            _state.value = UpdateState.UpToDate(BuildConfig.VERSION_NAME)
            return
        }
        val file = pendingDownloadedFile?.takeIf { it.exists() }
            ?: File(getApplication<Application>().cacheDir, "updates/${pending.apkName}").takeIf { it.exists() }
        pendingDownloadedFile = file
        if (file != null) {
            _state.value = UpdateState.Downloaded(pending, file.absolutePath)
        } else if (_state.value is UpdateState.Installing) {
            _state.value = UpdateState.UpdateAvailable(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), pending)
        }
    }

    fun autoUpdateEnabled(): Boolean = prefs.getBoolean(UpdateWorker.AUTO_UPDATE_KEY, false)

    fun setAutoUpdateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(UpdateWorker.AUTO_UPDATE_KEY, enabled).apply()
        UpdateScheduler.setEnabled(getApplication(), enabled)
    }

    private fun restorePending() {
        val json = prefs.getString(UpdateWorker.PENDING_UPDATE_KEY, null) ?: return
        val update = runCatching { Gson().fromJson(json, ReleaseUpdate::class.java) }.getOrNull() ?: run {
            prefs.edit().remove(UpdateWorker.PENDING_UPDATE_KEY).apply()
            return
        }
        if (BuildConfig.VERSION_CODE.toLong() >= update.versionCode) {
            prefs.edit().remove(UpdateWorker.PENDING_UPDATE_KEY).apply()
            repository.cleanCache()
            return
        }
        val file = File(getApplication<Application>().cacheDir, "updates/${update.apkName}")
        if (file.exists()) {
            pendingDownloadedFile = file
            _state.value = UpdateState.Downloaded(update, file.absolutePath)
        } else {
            prefs.edit().remove(UpdateWorker.PENDING_UPDATE_KEY).apply()
            _state.value = UpdateState.UpdateAvailable(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), update)
        }
    }
}
