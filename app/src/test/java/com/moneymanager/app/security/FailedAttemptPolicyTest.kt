package com.moneymanager.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 10: the failed-attempt policy that gates the app lock. Pure maths, no Android, so the
 * rate limiting and its bounds are pinned down exactly.
 */
class FailedAttemptPolicyTest {

    @Test
    fun firstFewFailuresCarryNoCooldown() {
        var state = FailedAttemptPolicy.State()
        val now = 1_000L
        repeat(FailedAttemptPolicy.FREE_ATTEMPTS) { index ->
            state = FailedAttemptPolicy.onFailure(state, now)
            assertEquals(index + 1, state.failures)
            assertEquals(0L, state.lockedUntilMillis)
            assertFalse(FailedAttemptPolicy.isLockedOut(state, now))
        }
    }

    @Test
    fun exceedingTheFreeAttemptsArmsTheBaseCooldown() {
        var state = FailedAttemptPolicy.State(failures = FailedAttemptPolicy.FREE_ATTEMPTS)
        val now = 1_000L
        state = FailedAttemptPolicy.onFailure(state, now)

        assertEquals(FailedAttemptPolicy.FREE_ATTEMPTS + 1, state.failures)
        assertEquals(now + FailedAttemptPolicy.BASE_COOLDOWN_MILLIS, state.lockedUntilMillis)
        assertTrue(FailedAttemptPolicy.isLockedOut(state, now))
        assertEquals(
            FailedAttemptPolicy.BASE_COOLDOWN_MILLIS,
            FailedAttemptPolicy.remainingLockoutMillis(state, now)
        )
    }

    @Test
    fun eachFurtherFailureDoublesTheCooldown() {
        var state = FailedAttemptPolicy.State(failures = FailedAttemptPolicy.FREE_ATTEMPTS)
        val now = 0L
        state = FailedAttemptPolicy.onFailure(state, now) // 5th -> 30s
        assertEquals(FailedAttemptPolicy.BASE_COOLDOWN_MILLIS, state.lockedUntilMillis)
        state = FailedAttemptPolicy.onFailure(state, now) // 6th -> 60s
        assertEquals(FailedAttemptPolicy.BASE_COOLDOWN_MILLIS * 2, state.lockedUntilMillis)
        state = FailedAttemptPolicy.onFailure(state, now) // 7th -> 120s
        assertEquals(FailedAttemptPolicy.BASE_COOLDOWN_MILLIS * 4, state.lockedUntilMillis)
    }

    @Test
    fun cooldownIsCappedAndNeverOverflows() {
        assertEquals(FailedAttemptPolicy.MAX_COOLDOWN_MILLIS, FailedAttemptPolicy.cooldownFor(1_000_000))
        assertEquals(FailedAttemptPolicy.MAX_COOLDOWN_MILLIS, FailedAttemptPolicy.cooldownFor(60))
        assertTrue(FailedAttemptPolicy.cooldownFor(1_000_000) > 0L)
    }

    @Test
    fun lockoutExpiresWhenTheClockPassesTheDeadline() {
        val now = 5_000L
        val state = FailedAttemptPolicy.onFailure(
            FailedAttemptPolicy.State(failures = FailedAttemptPolicy.FREE_ATTEMPTS),
            now
        )
        assertTrue(FailedAttemptPolicy.isLockedOut(state, now))
        assertFalse(FailedAttemptPolicy.isLockedOut(state, state.lockedUntilMillis))
        assertEquals(
            0L,
            FailedAttemptPolicy.remainingLockoutMillis(state, state.lockedUntilMillis + 10_000L)
        )
    }

    @Test
    fun successClearsTheCounterAndCooldown() {
        var state = FailedAttemptPolicy.State()
        repeat(FailedAttemptPolicy.FREE_ATTEMPTS + 1) { state = FailedAttemptPolicy.onFailure(state, 0L) }
        assertTrue(FailedAttemptPolicy.isLockedOut(state, 0L))

        state = FailedAttemptPolicy.onSuccess()
        assertEquals(0, state.failures)
        assertEquals(0L, state.lockedUntilMillis)
        assertFalse(FailedAttemptPolicy.isLockedOut(state, 0L))
    }
}
