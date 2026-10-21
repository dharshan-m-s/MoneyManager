package com.moneymanager.app.security

/**
 * Deterministic failed-unlock policy for the app lock.
 *
 * The first [FREE_ATTEMPTS] wrong PINs are answered immediately. After that, verification is
 * refused for an increasing cooldown that starts at [BASE_COOLDOWN_MILLIS] and doubles on every
 * further failure, capped at [MAX_COOLDOWN_MILLIS]. A correct PIN clears the counter.
 *
 * Kept free of Android dependencies so the rate-limit maths is unit-testable. [AppLock] persists
 * the returned values, so the cooldown also survives an activity/process restart and cannot be
 * bypassed by relaunching the app.
 */
object FailedAttemptPolicy {

    /** Wrong PINs allowed back-to-back before any delay is imposed. */
    const val FREE_ATTEMPTS = 4

    /** First cooldown once [FREE_ATTEMPTS] is exceeded. */
    const val BASE_COOLDOWN_MILLIS = 30_000L

    /** Upper bound so a hostile user cannot lock themselves out forever. */
    const val MAX_COOLDOWN_MILLIS = 30 * 60_000L

    /** Immutable snapshot of the persisted counter/lockout, safe to round-trip through prefs. */
    data class State(
        val failures: Int = 0,
        val lockedUntilMillis: Long = 0L
    )

    /** True while verification must be refused without even hashing the PIN. */
    fun isLockedOut(state: State, nowMillis: Long): Boolean =
        nowMillis < state.lockedUntilMillis

    /** Milliseconds still to wait, or 0 when verification is allowed. */
    fun remainingLockoutMillis(state: State, nowMillis: Long): Long =
        (state.lockedUntilMillis - nowMillis).coerceAtLeast(0L)

    /**
     * Records a failed attempt. Returns the next [State]; when the failure count exceeds
     * [FREE_ATTEMPTS] a cooldown is armed (base duration for the first over-limit failure,
     * doubling thereafter up to [MAX_COOLDOWN_MILLIS]).
     */
    fun onFailure(state: State, nowMillis: Long): State {
        val failures = state.failures + 1
        val lockedUntil = if (failures > FREE_ATTEMPTS) {
            nowMillis + cooldownFor(failures)
        } else {
            state.lockedUntilMillis
        }
        return State(failures = failures, lockedUntilMillis = lockedUntil)
    }

    /** A correct PIN clears everything. */
    fun onSuccess(): State = State()

    /**
     * Cooldown for a failure count, as a pure function: attempts 1..[FREE_ATTEMPTS] carry no
     * delay; attempt FREE_ATTEMPTS+1 gives [BASE_COOLDOWN_MILLIS]; each further attempt doubles.
     */
    fun cooldownFor(failures: Int): Long {
        if (failures <= FREE_ATTEMPTS) return 0L
        val step = failures - FREE_ATTEMPTS - 1
        // Guard the shift: a huge failure count must clamp, not overflow.
        val cooldown = if (step >= 40) MAX_COOLDOWN_MILLIS else BASE_COOLDOWN_MILLIS shl step
        return cooldown.coerceAtMost(MAX_COOLDOWN_MILLIS)
    }
}
