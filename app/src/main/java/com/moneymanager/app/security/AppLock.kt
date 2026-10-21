package com.moneymanager.app.security

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** How the app lock verifies the user. */
enum class LockMethod {
    /** The app's own 4-8 digit PIN, hashed with PBKDF2 before it is persisted. */
    IN_APP_PIN,

    /**
     * Android's own screen lock (fingerprint, face or device PIN/pattern/password) shown through
     * the system [androidx.biometric.BiometricPrompt] - no separate app secret to remember.
     */
    SYSTEM_BIOMETRIC
}

/**
 * Real PIN-based app lock backed by [PinHasher], with an optional system-lock method.
 *
 * The app relocks after [LOCK_AFTER_BACKGROUND_MILLIS] in the background, so briefly leaving the
 * app (for the photo picker, a share sheet, or a permission dialog) does not force a re-entry.
 *
 * Enabling the lock goes through the method chooser: the in-app method requires setting a PIN
 * (only the hash + salt are ever persisted), while the system method verifies through Android's
 * own screen-lock prompt and stores no secret. A PIN can still be set later as a recovery code
 * for the system method - it is optional, not required, because Android owns that secret.
 */
@Singleton
class AppLock @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnabled(): Boolean {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return false
        return when (method()) {
            // Fail-closed: if the enabled flag is set, the gate stays up regardless of whether
            // the credential is still usable. A PIN method with a missing/corrupt hash must show
            // the recovery screen (which can reset the lock), never silently open the ledger.
            LockMethod.IN_APP_PIN -> true
            // The system prompt is provided by Android itself, so the method works without any
            // app-side secret. If the device later loses its screen lock AND no recovery PIN was
            // set, the lock stays engaged (fail-closed, never silently unprotected): the gate
            // shows a recovery screen explaining that re-adding a screen lock restores access.
            LockMethod.SYSTEM_BIOMETRIC -> true
        }
    }

    fun hasPin(): Boolean =
        !prefs.getString(KEY_HASH, null).isNullOrBlank() &&
            !prefs.getString(KEY_SALT, null).isNullOrBlank()

    /** The configured verification method. Only meaningful when [isEnabled] is true. */
    fun method(): LockMethod = runCatching {
        prefs.getString(KEY_METHOD, null)?.let(LockMethod::valueOf)
    }.getOrNull() ?: LockMethod.IN_APP_PIN

    fun setMethod(method: LockMethod) {
        prefs.edit { putString(KEY_METHOD, method.name) }
    }

    /** Stores a new PIN and enables the lock. Returns false if the PIN is not acceptable. */
    fun setPin(pin: String): Boolean {
        if (!PinHasher.isValidPin(pin)) return false
        val salt = PinHasher.newSalt()
        prefs.edit {
            putString(KEY_SALT, salt)
            putString(KEY_HASH, PinHasher.hash(pin, salt))
            // The unlock screen draws one dot per digit, so it needs the real length.
            putInt(KEY_PIN_LENGTH, pin.length)
            putBoolean(KEY_ENABLED, true)
            // A freshly set credential starts from a clean slate.
            remove(KEY_FAILURES)
            remove(KEY_LOCKED_UNTIL)
        }
        return true
    }

    /** Digit count of the configured PIN (4 by default for pins set before this was stored). */
    fun pinLength(): Int = prefs.getInt(KEY_PIN_LENGTH, 4).coerceIn(4, 8)

    fun verify(pin: String): Boolean {
        // Not method-gated on purpose: with the system method this verifies the optional
        // recovery PIN, which is exactly how the PIN fallback screen lets the user in.
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val hash = prefs.getString(KEY_HASH, null) ?: return false
        return PinHasher.verify(pin, salt, hash)
    }

    // --- Failed-attempt policy (persisted so it survives an activity/process restart) ---

    /** The persisted failure count / active cooldown. */
    fun failedAttemptState(): FailedAttemptPolicy.State = FailedAttemptPolicy.State(
        failures = prefs.getInt(KEY_FAILURES, 0),
        lockedUntilMillis = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
    )

    /** True while unlock attempts must be refused; the caller surfaces [lockoutRemainingMillis]. */
    fun isLockedOut(nowMillis: Long): Boolean =
        FailedAttemptPolicy.isLockedOut(failedAttemptState(), nowMillis)

    fun lockoutRemainingMillis(nowMillis: Long): Long =
        FailedAttemptPolicy.remainingLockoutMillis(failedAttemptState(), nowMillis)

    /** Records one failed unlock and persists the (possibly armed) cooldown. */
    fun recordFailedAttempt(nowMillis: Long) {
        val next = FailedAttemptPolicy.onFailure(failedAttemptState(), nowMillis)
        prefs.edit {
            putInt(KEY_FAILURES, next.failures)
            putLong(KEY_LOCKED_UNTIL, next.lockedUntilMillis)
        }
    }

    /** Clears the failure counter and any cooldown after a successful verification. */
    fun clearFailedAttempts() {
        prefs.edit {
            remove(KEY_FAILURES)
            remove(KEY_LOCKED_UNTIL)
        }
    }

    /** True when the system prompt can be used: a device screen lock is enrolled. */
    fun canUseSystemLock(): Boolean {
        val manager = androidx.core.content.ContextCompat.getSystemService(
            context,
            android.app.KeyguardManager::class.java
        )
        return manager?.isKeyguardSecure == true
    }

    /** Turns the lock on with the given method, for methods that need no app-side secret. */
    fun enable(method: LockMethod) {
        prefs.edit {
            putString(KEY_METHOD, method.name)
            putBoolean(KEY_ENABLED, true)
        }
    }

    /** Turns the lock off and forgets the PIN. Only call after verifying the current PIN. */
    fun disable() {
        prefs.edit {
            remove(KEY_SALT)
            remove(KEY_HASH)
            remove(KEY_METHOD)
            remove(KEY_PIN_LENGTH)
            remove(KEY_FAILURES)
            remove(KEY_LOCKED_UNTIL)
            putBoolean(KEY_ENABLED, false)
        }
    }

    fun relockAfterMillis(): Long = LOCK_AFTER_BACKGROUND_MILLIS

    private companion object {
        const val PREFS_NAME = "moneymanager_security"
        const val KEY_ENABLED = "lock_enabled"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_METHOD = "lock_method"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_FAILURES = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_until_millis"
        const val LOCK_AFTER_BACKGROUND_MILLIS = 30_000L
    }
}
