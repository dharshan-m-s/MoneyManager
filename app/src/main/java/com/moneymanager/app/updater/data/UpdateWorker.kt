package com.moneymanager.app.updater.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.moneymanager.app.updater.model.ReleaseUpdate
import java.io.File

class UpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        if (!prefs.getBoolean(AUTO_UPDATE_KEY, false)) return Result.success()
        return when (val result = UpdateRepository(applicationContext).checkForUpdate()) {
            is UpdateRepository.CheckResult.Available -> {
                prefs.edit()
                    .putString(PENDING_UPDATE_KEY, Gson().toJson(result.update))
                    .putLong(LAST_CHECKED_KEY, System.currentTimeMillis())
                    .apply()
                Result.success()
            }
            is UpdateRepository.CheckResult.UpToDate -> {
                prefs.edit().remove(PENDING_UPDATE_KEY).putLong(LAST_CHECKED_KEY, System.currentTimeMillis()).apply()
                val dir = File(applicationContext.cacheDir, "updates")
                dir.listFiles()?.forEach { it.delete() }
                Result.success()
            }
            is UpdateRepository.CheckResult.Error -> {
                prefs.edit().putString(LAST_ERROR_KEY, result.error::class.java.simpleName).putLong(LAST_CHECKED_KEY, System.currentTimeMillis()).apply()
                Result.success()
            }
        }
    }

    companion object {
        const val PREFS = "moneymanager_updates"
        const val AUTO_UPDATE_KEY = "auto_update"
        const val PENDING_UPDATE_KEY = "pending_update"
        const val LAST_CHECKED_KEY = "last_checked"
        const val INSTALLED_UPDATE_KEY = "installed_update_version"
        const val LAST_ERROR_KEY = "last_error"
    }
}
