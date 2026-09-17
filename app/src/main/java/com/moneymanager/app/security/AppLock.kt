package com.moneymanager.app.security

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real PIN-based app lock backed by [PinHasher]. The share of work here is small but explicit:
 * enabling the lock requires setting a PIN, and only the hash + salt are ever persisted.
 *
 * The app relocks after [LOCK_AFTER_BACKGROUND_MILLIS] in the background, so briefly leaving the
 * app (for the photo picker, a share sheet, or a permission dialog) does not force a re-entry.
 */
@Singleton
class AppLock @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false) && hasPin()

    fun hasPin(): Boolean =
        !prefs.getString(KEY_HASH, null).isNullOrBlank() &&
            !prefs.getString(KEY_SALT, null).isNullOrBlank()

    /** Stores a new PIN and enables the lock. Returns false if the PIN is not acceptable. */
    fun setPin(pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false
        val salt = PinHasher.newSalt()
        prefs.edit {
            putString(KEY_SALT, salt)
            putString(KEY_HASH, PinHasher.hash(pin, salt))
            putBoolean(KEY_ENABLED, true)
        }
        return true
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val hash = prefs.getString(KEY_HASH, null) ?: return false
        return PinHasher.verify(pin, salt, hash)
    }

    /** Turns the lock off and forgets the PIN. Only call after verifying the current PIN. */
    fun disable() {
        prefs.edit {
            remove(KEY_SALT)
            remove(KEY_HASH)
            putBoolean(KEY_ENABLED, false)
        }
    }

    fun relockAfterMillis(): Long = LOCK_AFTER_BACKGROUND_MILLIS

    private companion object {
        const val PREFS_NAME = "moneymanager_security"
        const val KEY_ENABLED = "lock_enabled"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val LOCK_AFTER_BACKGROUND_MILLIS = 30_000L
    }
}
