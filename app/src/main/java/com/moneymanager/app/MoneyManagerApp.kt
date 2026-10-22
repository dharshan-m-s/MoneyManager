package com.moneymanager.app

import android.app.Application
import android.util.Log
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.data.repository.BillRecurrenceWorker
import com.moneymanager.app.data.repository.CreditCardBillingService
import com.moneymanager.app.domain.billing.BillRecurrenceService
import com.moneymanager.app.updater.domain.UpdateScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MoneyManagerApp : Application() {

    @Inject lateinit var billRecurrenceService: BillRecurrenceService
    @Inject lateinit var creditCardBillingService: CreditCardBillingService

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        setupTimber()
        val prefs = getSharedPreferences("moneymanager_updates", MODE_PRIVATE)
        UpdateScheduler.setEnabled(this, prefs.getBoolean("auto_update", false))
        cleanStaleDownloads()

        // Safety net for process restarts: catch up any bill occurrences that entered their
        // lead window while the app/worker was not running, then keep a periodic worker for
        // when the app is closed. WorkManager timing is best-effort; foreground reconciliation
        // in MainActivity covers delayed executions.
        appScope.launch {
            runCatching { billRecurrenceService.reconcileAll() }
                .onFailure { Timber.e(it, "Initial bill reconciliation failed") }
            // A card's statement and its linked bill follow the card's billing cycle, so they
            // are refreshed from the card's own transactions on every start.
            runCatching { creditCardBillingService.refreshAllCards() }
                .onFailure { Timber.e(it, "Initial credit card statement refresh failed") }
        }
        BillRecurrenceWorker.schedule(this)
    }

    private fun cleanStaleDownloads() {
        val dir = File(cacheDir, "updates")
        if (!dir.exists()) return
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
    }

    private fun setupTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In release builds, plant a tree that only logs errors and warnings
            // to avoid leaking sensitive information in logs.
            Timber.plant(ReleaseTree())
        }
    }

    /**
     * Minimal tree for release builds — only logs warnings and errors, never verbose/debug.
     * Production should replace this with a crash reporting tree (Firebase Crashlytics, Sentry, etc.).
     */
    private class ReleaseTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean {
            return priority >= Log.WARN
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority < Log.WARN) return

            // Release builds intentionally avoid verbose logging and third-party telemetry.
            if (priority >= Log.ERROR) {
                Log.e(tag, message, t)
            } else {
                Log.w(tag, message)
            }
        }
    }
}
