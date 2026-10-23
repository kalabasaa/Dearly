package com.dearly.app.security

data class AttemptState(val failures: Int = 0, val lockUntil: Long = 0L)

/**
 * Rate limiting for PIN and recovery-answer guessing.
 * Every [FREE_ATTEMPTS] consecutive failures starts a lockout: 30s, 60s, 120s ... capped at 30 minutes.
 */
object AttemptPolicy {
    const val FREE_ATTEMPTS = 5
    private const val BASE_LOCK_MS = 30_000L
    const val MAX_LOCK_MS = 30 * 60_000L

    fun lockDurationMs(failures: Int): Long {
        if (failures <= 0 || failures % FREE_ATTEMPTS != 0) return 0L
        val round = failures / FREE_ATTEMPTS
        val shift = (round - 1).coerceAtMost(11)
        return (BASE_LOCK_MS shl shift).coerceAtMost(MAX_LOCK_MS)
    }

    /** Milliseconds left in the current lockout (0 = not locked). Clamped in case the clock was moved back. */
    fun remainingLockMs(state: AttemptState, now: Long): Long {
        val remaining = state.lockUntil - now
        return if (remaining <= 0) 0L else remaining.coerceAtMost(MAX_LOCK_MS)
    }

    fun onFailure(state: AttemptState, now: Long): AttemptState {
        val failures = state.failures + 1
        val duration = lockDurationMs(failures)
        return AttemptState(failures, if (duration > 0) now + duration else state.lockUntil)
    }

    fun onSuccess(): AttemptState = AttemptState()

    /** Attempts left before the next lockout begins. */
    fun attemptsBeforeLock(state: AttemptState): Int = FREE_ATTEMPTS - (state.failures % FREE_ATTEMPTS)
}
