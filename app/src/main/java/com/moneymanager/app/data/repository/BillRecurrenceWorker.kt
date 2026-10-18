package com.moneymanager.app.data.repository

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneymanager.app.domain.billing.BillRecurrenceService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BillRecurrenceEntryPoint {
    fun billRecurrenceService(): BillRecurrenceService
}

/**
 * Persistent background safety net for recurring bills. The OS may delay periodic work, so the
 * app also reconciles on every foreground entry; together they guarantee catch-up without
 * relying on an exact execution minute.
 */
class BillRecurrenceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                BillRecurrenceEntryPoint::class.java
            ).billRecurrenceService().reconcileAll()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val UNIQUE_NAME = "bill_recurrence_reconcile"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BillRecurrenceWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
