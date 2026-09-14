package com.moneymanager.app.data.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateHashTest {

    @Test
    fun `sha256Hex matches java MessageDigest baseline for known content`() {
        val tmp = File.createTempFile("hash-test", ".txt")
        try {
            val content = "MoneyManager release test payload"
            tmp.writeBytes(content.toByteArray())
            val expected = MessageDigest.getInstance("SHA-256")
                .digest(content.toByteArray())
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, UpdateHash.sha256Hex(tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `sha256Hex produces consistent 64-char lowercase hex`() {
        val tmp = File.createTempFile("hash-test", ".apk")
        try {
            tmp.writeBytes(ByteArray(256) { it.toByte() })
            val hex = UpdateHash.sha256Hex(tmp)
            assertEquals(64, hex.length)
            assertTrue(hex.all { it in '0'..'f' })
            assertEquals(hex, UpdateHash.sha256Hex(tmp))
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `sha256Hex fails on missing file`() {
        val tmp = File("/nonexistent/file.apk")
        try {
            UpdateHash.sha256Hex(tmp)
            throw AssertionError("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("nonexistent") ?: false)
        }
    }

    @Test
    fun `sha256Hex fails on empty file`() {
        val tmp = File.createTempFile("empty", ".apk")
        try {
            UpdateHash.sha256Hex(tmp)
            throw AssertionError("Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("empty") ?: false)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `matches compares lowercase trimmed hex`() {
        assertTrue(UpdateHash.matches("ABC123", "  abc123  "))
        assertTrue(UpdateHash.matches("abc123", "ABC123"))
        assertFalse(UpdateHash.matches("", "abc123"))
        assertFalse(UpdateHash.matches("abc123", "abc124"))
    }
}