package com.moneymanager.app.updater.domain

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.moneymanager.app.BuildConfig
import java.io.File
import java.security.MessageDigest

class ApkVerifier(private val context: Context) {
    fun verify(file: File, expectedVersionCode: Long, expectedPackageName: String = BuildConfig.APPLICATION_ID): Result<Unit> {
        return runCatching {
            require(file.exists() && file.length() > 0) { "APK file is missing or empty" }
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } ?: error("Invalid APK")
            require(archiveInfo.packageName == expectedPackageName) { "Package name mismatch" }
            require(archiveInfo.longVersionCode == expectedVersionCode) { "Version code mismatch" }
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(expectedPackageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(expectedPackageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            val installedDigests = certificateDigests(installed.signingInfo)
            val archiveDigests = certificateDigests(archiveInfo.signingInfo)
            require(installedDigests.intersect(archiveDigests).isNotEmpty()) { "Signing certificate mismatch" }
        }
    }

    private fun certificateDigests(signingInfo: android.content.pm.SigningInfo?): Set<String> {
        if (signingInfo == null) return emptySet()
        val signers = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        return signers.mapNotNull { signature ->
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                digest.joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }.toSet()
    }
}
