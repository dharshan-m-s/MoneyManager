package com.moneymanager.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Static helpers for the Update Center UI to check/request the system
 * "install unknown apps" permission without a ViewModel dependency.
 */
object UpdaterAccess {

    fun canRequestInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent()
        }
    }
}