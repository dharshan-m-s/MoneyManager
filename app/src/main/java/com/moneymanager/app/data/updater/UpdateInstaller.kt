package com.moneymanager.app.data.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

/**
 * Initiates Android's standard package installation flow for an APK on local storage.
 * Respects Android's installation security model: the user sees the system confirmation
 * dialog. REQUEST_INSTALL_PACKAGES permission must be declared in the manifest (API 26+).
 */
class UpdateInstaller(private val context: Context) {

    companion object {
        const val REQUEST_CODE_INSTALL = 1903001
        const val REQUEST_CODE_INSTALL_RESULT = 1903002
        private const val PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    }

    private val authority: String
        get() = "${context.packageName}$PROVIDER_AUTHORITY_SUFFIX"

    /** Does Android grant the app permission to request APK installation? */
    fun canRequestInstall(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Returns the system Intent to open the "Install unknown apps" settings for this app.
     */
    fun installSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        } else {
            Intent()
        }
    }

    /**
     * Launches the Android package installer to update the app with the APK at [apk].
     * Returns true if the intent was dispatched, false if the file is invalid.
     */
    fun launchInstaller(apk: File): Boolean {
        if (!apk.exists() || apk.length() == 0L) {
            Timber.w("Attempted to install missing APK: ${apk.absolutePath}")
            return false
        }
        val contentUri = try {
            FileProvider.getUriForFile(context, authority, apk)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "FileProvider cannot expose ${apk.absolutePath}")
            return false
        }
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = contentUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        return try {
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch installer for ${apk.name}")
            false
        }
    }

    /**
     * Deletes the APK file and any associated status marker.
     */
    fun cleanupDownloads(vararg files: File?) {
        files.forEach { file ->
            file?.let { f ->
                if (f.exists()) {
                    val deleted = f.delete()
                    if (!deleted) Timber.w("Could not delete ${f.absolutePath}")
                }
            }
        }
    }
}