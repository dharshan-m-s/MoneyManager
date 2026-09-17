package com.moneymanager.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Pure PIN hashing/verification used by the app lock. Deliberately free of Android
 * dependencies so the security-critical part is unit-testable.
 *
 * The PIN is never stored in plain text: only a random per-install salt plus a PBKDF2-HMAC-SHA256
 * digest with a high iteration count.
 */
object PinHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            saltHex.hexToBytes(),
            ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashed = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return hashed.toHex()
    }

    /** Constant-time comparison so a wrong PIN can't be probed by timing. */
    fun verify(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        val actual = hash(pin, saltHex)
        if (actual.length != expectedHashHex.length) return false
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8)
        )
    }

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Salt must be an even-length hex string" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
