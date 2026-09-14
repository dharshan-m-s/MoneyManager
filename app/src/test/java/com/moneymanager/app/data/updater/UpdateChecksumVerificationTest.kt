package com.moneymanager.app.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * JVM tests for the pure checksum verification gate (UpdateVerifier companion).
 * The Android-dependent checks (package/signing) are exercised on-device.
 */
class UpdateChecksumVerificationTest {

    private fun tempFile(content: ByteArray): File =
        File.createTempFile("apk-verify", ".bin").apply { writeBytes(content) }

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `matching checksum succeeds`() {
        val content = "apk-bytes".toByteArray()
        val file = tempFile(content)
        try {
            val result = UpdateVerifier.checksumVerification(file, sha256Of(content))
            assertTrue(result.isSuccess)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `mismatched checksum fails`() {
        val file = tempFile("real-bytes".toByteArray())
        try {
            val result = UpdateVerifier.checksumVerification(file, "f".repeat(64))
            assertTrue(result is VerificationResult.Failed)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `uppercase expected digest is accepted case-insensitively`() {
        val content = "payload".toByteArray()
        val file = tempFile(content)
        try {
            val result = UpdateVerifier.checksumVerification(file, sha256Of(content).uppercase())
            assertTrue(result.isSuccess)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `malformed expected digest is rejected, never installed`() {
        val file = tempFile("x".toByteArray())
        try {
            listOf(
                "abc123",                 // too short
                "z".repeat(64),           // non-hex
                ("a".repeat(63) + "g")    // 64 chars but not hex
            ).forEach { bad ->
                val result = UpdateVerifier.checksumVerification(file, bad)
                assertTrue("digest '$bad' must fail", result is VerificationResult.Failed)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `missing or empty file fails verification`() {
        val missing = File("/nonexistent/path/update.apk")
        assertTrue(UpdateVerifier.checksumVerification(missing, "a".repeat(64)) is VerificationResult.Failed)

        val empty = File.createTempFile("empty-apk", ".bin")
        try {
            assertTrue(UpdateVerifier.checksumVerification(empty, "a".repeat(64)) is VerificationResult.Failed)
        } finally {
            empty.delete()
        }
    }

    @Test
    fun `blank digest short-circuits to success (nothing to verify)`() {
        val file = tempFile("content".toByteArray())
        try {
            assertTrue(UpdateVerifier.checksumVerification(file, "") .isSuccess)
            assertTrue(UpdateVerifier.checksumVerification(file, "   ").isSuccess)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `failure messages never leak digest values`() {
        val file = tempFile("secret-content".toByteArray())
        try {
            val result = UpdateVerifier.checksumVerification(file, "a".repeat(64))
            val message = (result as VerificationResult.Failed).message
            assertTrue(!message.contains("secret-content"))
            assertTrue(!message.contains("a".repeat(64)))
        } finally {
            file.delete()
        }
    }
}
