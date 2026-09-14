package com.moneymanager.app.data.repository

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import dagger.hilt.android.EntryPointAccessors

/** Durable background backup. WorkManager retries it when the app process is gone or the provider is temporarily unavailable. */
class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): ListenableWorker.Result {
        val manager = EntryPointAccessors.fromApplication(
            applicationContext,
            BackupWorkerEntryPoint::class.java
        ).backupManager()
        return manager.backupNow().fold(
            onSuccess = { ListenableWorker.Result.success() },
            onFailure = { error ->
                val message = error.message.orEmpty().lowercase()
                val permanent = listOf(
                    "choose a backup folder",
                    "backup folder is unavailable",
                    "backup folder is not writable",
                    "did not grant permission",
                    "unable to create"
                ).any(message::contains)
                if (permanent) ListenableWorker.Result.failure()
                else ListenableWorker.Result.retry()
            }
        )
    }
}
