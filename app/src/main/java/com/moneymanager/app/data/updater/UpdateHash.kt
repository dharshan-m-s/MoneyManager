package com.moneymanager.app.data.updater

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Pure SHA-256 helpers for the updater (no Android dependencies – unit-testable).
 */
object UpdateHash {

    /**
     * SHA-256 hex digest of [file] (lowercase hex, no separators).
     */
    fun sha256Hex(file: File): String {
        require(file.exists() && file.length() > 0) { "Cannot hash ${file.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(file).use { stream ->
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** True when [actual] (lowercased, trimmed) equals a non-blank [expected]. */
    fun matches(expected: String, actual: String): Boolean {
        val e = expected.trim().lowercase()
        val a = actual.trim().lowercase()
        return e.isNotEmpty() && e == a
    }
}