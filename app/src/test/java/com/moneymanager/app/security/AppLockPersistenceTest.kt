package com.moneymanager.app.security

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Section 10: the real [AppLock] store. Verifies the PIN is only ever persisted as a salted
 * PBKDF2 hash, that the failed-attempt counter survives a "process restart" (a fresh AppLock
 * instance over the same prefs), and that the fail-closed / recovery behaviours hold.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppLockPersistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("moneymanager_security", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun newLock() = AppLock(context)

    @Test
    fun setPinNeverStoresThePlaintextPinAndVerificationWorks() {
        val lock = newLock()
        assertTrue(lock.setPin("1234"))

        val prefs = context.getSharedPreferences("moneymanager_security", Context.MODE_PRIVATE)
        val all = prefs.all.values.joinToString(" ") { it.toString() }
        assertFalse("plaintext PIN must never be persisted", all.contains("1234"))
        assertNotNull(prefs.getString("pin_salt", null))
        assertNotNull(prefs.getString("pin_hash", null))

        assertTrue(lock.verify("1234"))
        assertFalse(lock.verify("4321"))
        assertTrue(lock.isEnabled())
        assertEquals(4, lock.pinLength())
    }

    @Test
    fun failedAttemptCounterPersistsAcrossAProcessRestart() {
        val first = newLock()
        first.setPin("1234")
        val now = System.currentTimeMillis()

        repeat(FailedAttemptPolicy.FREE_ATTEMPTS + 1) { first.recordFailedAttempt(now) }

        // A brand-new instance simulates the app being killed and relaunched: the cooldown must
        // still be armed, so relaunching cannot trivially clear the lock out.
        val restarted = newLock()
        assertTrue(restarted.isLockedOut(now))
        assertTrue(restarted.lockoutRemainingMillis(now) > 0L)
    }

    @Test
    fun successfulUnlockClearsTheCounterAndCooldown() {
        val lock = newLock()
        lock.setPin("1234")
        val now = System.currentTimeMillis()
        repeat(FailedAttemptPolicy.FREE_ATTEMPTS + 1) { lock.recordFailedAttempt(now) }
        assertTrue(lock.isLockedOut(now))

        lock.clearFailedAttempts()
        assertFalse(lock.isLockedOut(now))
        assertEquals(0, lock.failedAttemptState().failures)
    }

    @Test
    fun settingANewPinResetsTheFailureState() {
        val lock = newLock()
        lock.setPin("1234")
        repeat(FailedAttemptPolicy.FREE_ATTEMPTS + 1) { lock.recordFailedAttempt(0L) }
        assertTrue(lock.isLockedOut(0L))

        lock.setPin("5678")
        assertFalse(lock.isLockedOut(0L))
    }

    @Test
    fun enabledFlagWithNoUsableHashStaysFailClosed() {
        val lock = newLock()
        // Only the enabled flag is set but no hash/salt exist (corrupt/lost credential columns).
        context.getSharedPreferences("moneymanager_security", Context.MODE_PRIVATE)
            .edit().putBoolean("lock_enabled", true).putString("lock_method", LockMethod.IN_APP_PIN.name).commit()

        // Fail-closed: the gate must stay up rather than silently opening the ledger.
        assertTrue(lock.isEnabled())
        assertFalse(lock.hasPin())
    }

    @Test
    fun disablingForgetsThePinAndFailureState() {
        val lock = newLock()
        lock.setPin("1234")
        repeat(FailedAttemptPolicy.FREE_ATTEMPTS + 1) { lock.recordFailedAttempt(0L) }

        lock.disable()
        assertFalse(lock.isEnabled())
        assertFalse(lock.hasPin())
        assertFalse(lock.isLockedOut(0L))
        assertNull(lock.failedAttemptState().let { if (it.failures == 0 && it.lockedUntilMillis == 0L) null else it })
    }

    @Test
    fun invalidPinsAreRejected() {
        val lock = newLock()
        assertFalse(lock.setPin("12"))
        assertFalse(lock.setPin("123456789"))
        assertFalse(lock.setPin("abcd"))
        assertFalse(lock.isEnabled())
    }
}
