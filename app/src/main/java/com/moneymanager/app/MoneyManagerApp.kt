package com.moneymanager.app

import android.app.Application
import android.util.Log
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.updater.domain.UpdateScheduler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File

@HiltAndroidApp
class MoneyManagerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupTimber()
        val prefs = getSharedPreferences("moneymanager_updates", MODE_PRIVATE)
        UpdateScheduler.setEnabled(this, prefs.getBoolean("auto_update", false))
        cleanStaleDownloads()
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
