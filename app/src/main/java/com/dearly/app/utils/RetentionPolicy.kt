package com.dearly.app.utils

/** The 30-day Recently Deleted policy, based only on stored deletion timestamps. */
object RetentionPolicy {
    const val RETENTION_DAYS = 30
    private const val DAY_MS = 86_400_000L
    private const val RETENTION_MS = RETENTION_DAYS * DAY_MS

    fun expiresAt(deletedAt: Long): Long = deletedAt + RETENTION_MS

    fun isExpired(deletedAt: Long, now: Long): Boolean = now >= expiresAt(deletedAt)

    /** Rows with deleted_at <= this value are expired. */
    fun cutoff(now: Long): Long = now - RETENTION_MS

    /** Whole days remaining, rounded up, never negative. */
    fun daysLeft(deletedAt: Long, now: Long): Int {
        val remaining = expiresAt(deletedAt) - now
        if (remaining <= 0) return 0
        return ((remaining + DAY_MS - 1) / DAY_MS).toInt()
    }
}
