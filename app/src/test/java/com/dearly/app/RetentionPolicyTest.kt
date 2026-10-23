package com.dearly.app

import com.dearly.app.utils.RetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {
    private val day = 86_400_000L

    @Test fun thirtyDayWindow() {
        val deleted = 1_000_000_000_000L
        assertEquals(30, RetentionPolicy.daysLeft(deleted, deleted))
        assertEquals(30, RetentionPolicy.daysLeft(deleted, deleted + 1))
        assertEquals(1, RetentionPolicy.daysLeft(deleted, deleted + 29 * day + 1))
        assertEquals(0, RetentionPolicy.daysLeft(deleted, deleted + 30 * day))
        assertEquals(0, RetentionPolicy.daysLeft(deleted, deleted + 99 * day))
    }

    @Test fun expiryUsesStoredTimestampNotAppOpenTime() {
        val deleted = 5_000_000_000_000L
        assertFalse(RetentionPolicy.isExpired(deleted, deleted + 30 * day - 1))
        assertTrue(RetentionPolicy.isExpired(deleted, deleted + 30 * day))
    }

    @Test fun cutoffMatchesExpiry() {
        val now = 9_000_000_000_000L
        val cutoff = RetentionPolicy.cutoff(now)
        assertTrue(RetentionPolicy.isExpired(cutoff, now))
        assertFalse(RetentionPolicy.isExpired(cutoff + 1, now))
    }
}
