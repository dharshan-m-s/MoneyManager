package com.moneymanager.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Receives the status of a PackageInstaller session committed by [AppUpdateChecker].
 *
 * When a normal (non-privileged) app commits a session, Android does not show the
 * confirmation dialog by itself. It sends `STATUS_PENDING_USER_ACTION` with the
 * dialog intent in [Intent.EXTRA_INTENT]; this receiver must launch it. Without
 * this step the session stays pending and the user never sees the install prompt.
 *
 * Success and failure statuses need no action here: the update screen reconciles
 * the installed version on the next resume.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return

        val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        } ?: return

        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(confirm) }
    }
}
