package com.moneymanager.app

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.moneymanager.app.BuildConfig
import com.moneymanager.app.data.updater.UpdateManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MoneyManagerApp : Application() {

    @Inject
    lateinit var updateManager: UpdateManager

    override fun onCreate() {
        super.onCreate()
        setupTimber()
        // Post-update handling: if the app was restarted after installing (or
        // cancelling) an update, reconcile the updater state and purge stale
        // downloads before any UI is shown.
        updateManager.syncInstallationOutcome()
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

        // Deliberately the terminal printing tree, so raw Log is the correct backend here.
        @SuppressLint("LogNotTimber")
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
