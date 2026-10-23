package com.dearly.app.security

/**
 * In-memory authentication state, never persisted: a killed process or a fully closed app always
 * starts locked. While unlocked, the app locks again only after [TIMEOUT_MS] without activity
 * (counted from the last touch/typing, whether the app is open or in the background).
 */
object Session {
    const val TIMEOUT_MS = 30L * 60_000L

    @Volatile
    var unlocked: Boolean = false
        private set

    @Volatile
    var lockScreenShowing: Boolean = false

    @Volatile
    var lastActivityAt: Long = 0L
        private set

    fun unlock(now: Long = System.currentTimeMillis()) {
        unlocked = true
        lastActivityAt = now
    }

    fun lock() {
        unlocked = false
    }

    /** Records user activity (touch, typing, leaving the app). Ignored while locked. */
    fun touch(now: Long = System.currentTimeMillis()) {
        if (unlocked) lastActivityAt = now
    }

    fun isTimedOut(now: Long = System.currentTimeMillis()): Boolean =
        unlocked && now - lastActivityAt >= TIMEOUT_MS

    /** Milliseconds until the inactivity lock (0 if already due). */
    fun msUntilTimeout(now: Long = System.currentTimeMillis()): Long =
        (TIMEOUT_MS - (now - lastActivityAt)).coerceAtLeast(0L)
}
