package com.moneymanager.app.data.updater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Validates a downloaded APK before installation. Requires an Android [Context]
 * only for [PackageManager] access; the checksum comparison is pure and exposed
 * via [checksumVerification] for JVM unit tests.
 *
 * Every check fails closed: an unverifiable APK is never offered for installation.
 */
class UpdateVerifier(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    /**
     * SHA-256 hex digest of [file] (lowercase hex, no separators).
     */
    fun sha256Hex(file: File): String = UpdateHash.sha256Hex(file)

    /**
     * Checks the downloaded APK is a valid APK with the expected application ID.
     */
    fun validatePackage(apk: File, installedApplicationId: String): VerificationResult {
        if (!apk.exists() || apk.length() == 0L) {
            return VerificationResult.Failed("Downloaded file is missing or empty")
        }
        val info = pm.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return VerificationResult.Failed("The file is not a valid Android package")
        if (info.packageName != installedApplicationId) {
            return VerificationResult.Failed(
                "Package mismatch: expected '$installedApplicationId', got '${info.packageName}'"
            )
        }
        return VerificationResult.Success
    }

    /**
     * Confirms the downloaded APK's version code is strictly newer than the installed app.
     */
    fun validateVersionCode(apk: File, installedVersionCode: Long): VerificationResult {
        val info = pm.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: return VerificationResult.Failed("Package info could not be read")
        val remoteCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        if (remoteCode <= installedVersionCode) {
            return VerificationResult.Failed(
                "APK version ($remoteCode) is not newer than installed ($installedVersionCode)"
            )
        }
        return VerificationResult.Success
    }

    /**
     * Verifies the downloaded file matches the published SHA-256 digest.
     * Delegates to the pure [checksumVerification]; see that for the contract.
     */
    fun validateChecksum(apk: File, expectedSha256: String): VerificationResult =
        checksumVerification(apk, expectedSha256)

    /**
     * Confirms the downloaded APK was signed with the same certificate as the installed app.
     *
     * Fails closed: if either signature cannot be read, the update is rejected.
     * This asserts certificate *identity*; Android's installer still performs the
     * full signature verification before completing any update.
     */
    fun validateSigning(apk: File, installedApplicationId: String): VerificationResult {
        val installedCert = installedSigningCert(installedApplicationId)
            ?: return VerificationResult.Failed(
                "Could not read the installed app's signing certificate; update rejected"
            )
        val downloadedCert = apkSigningCert(apk)
            ?: return VerificationResult.Failed(
                "Could not read the update's signing certificate; update rejected"
            )
        if (installedCert != downloadedCert) {
            return VerificationResult.Failed(
                "Signing identity mismatch – the update was not signed with the same key as the installed app"
            )
        }
        return VerificationResult.Success
    }

    /** Convenience: run all checks in order and fail-fast. */
    fun fullValidation(
        apk: File,
        expectedSha256: String?,
        installedApplicationId: String,
        installedVersionCode: Long
    ): VerificationResult {
        return validatePackage(apk, installedApplicationId)
            .and { validateVersionCode(apk, installedVersionCode) }
            .and { validateSigning(apk, installedApplicationId) }
            .and {
                val expected = expectedSha256?.takeIf { it.isNotBlank() }
                    ?: return@and VerificationResult.Success
                validateChecksum(apk, expected)
            }
    }

    private fun installedSigningCert(appId: String): String? {
        val info = try {
            pm.getPackageInfo(
                appId,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    PackageManager.GET_SIGNING_CERTIFICATES
                else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w(e, "Installed package not found: %s", appId)
            return null
        }
        return extractCertHash(info)
    }

    private fun apkSigningCert(apk: File): String? {
        val info = pm.getPackageArchiveInfo(
            apk.absolutePath,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        ) ?: return null
        return extractCertHash(info)
    }

    @Suppress("DEPRECATION")
    private fun extractCertHash(info: android.content.pm.PackageInfo): String? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        } ?: return null
        if (signatures.isEmpty()) return null
        val derBytes = signatures[0].toByteArray()
        val cert = certFromDer(derBytes) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString("") { "%02X".format(it) }
    }

    private fun certFromDer(derBytes: ByteArray): X509Certificate? = try {
        val certFactory = CertificateFactory.getInstance("X.509")
        certFactory.generateCertificate(derBytes.inputStream()) as? X509Certificate
    } catch (e: Exception) {
        Timber.w(e, "Could not parse signing certificate")
        null
    }

    companion object {
        /**
         * Pure, JVM-testable SHA-256 verification of a downloaded file against the
         * published digest.
         *
         * Contract (fail closed):
         * - blank/absent expected digest → Success (nothing to verify against;
         *   the release pipeline always publishes one)
         * - malformed expected digest (not 64 hex chars) → Failed
         * - missing/empty file → Failed
         * - hash mismatch → Failed (no digest values in the message; not sensitive,
         *   but errors stay user-oriented)
         */
        fun checksumVerification(apk: File, expectedSha256: String): VerificationResult {
            val expected = expectedSha256.trim().lowercase()
            if (expected.isEmpty()) return VerificationResult.Success
            if (expected.length != 64 || expected.any { it !in '0'..'9' && it !in 'a'..'f' }) {
                return VerificationResult.Failed(
                    "The published checksum is malformed; refusing to install this update"
                )
            }
            if (!apk.exists() || apk.length() == 0L) {
                return VerificationResult.Failed("Downloaded file is missing or empty")
            }
            val actual = UpdateHash.sha256Hex(apk)
            return if (actual == expected) {
                VerificationResult.Success
            } else {
                VerificationResult.Failed(
                    "Checksum mismatch – the downloaded file does not match the published digest"
                )
            }
        }
    }
}

sealed class VerificationResult {
    data object Success : VerificationResult()
    data class Failed(val message: String) : VerificationResult()
    val isSuccess: Boolean get() = this is Success
    fun and(other: () -> VerificationResult): VerificationResult {
        if (this is Failed) return this
        return other()
    }
}
