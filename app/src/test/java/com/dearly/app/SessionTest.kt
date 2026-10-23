package com.dearly.app

import com.dearly.app.security.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val t0 = 1_000_000_000L
    private val minute = 60_000L

    @Test fun lockedSessionIsNeverReportedAsTimedOut() {
        Session.lock()
        assertFalse(Session.isTimedOut(t0 + 999 * minute))
    }

    @Test fun staysUnlockedWhenOnlyPausedBriefly() {
        Session.unlock(t0)
        assertFalse(Session.isTimedOut(t0 + 29 * minute + 59_000))
        assertTrue(Session.unlocked)
    }

    @Test fun locksExactlyAtThirtyMinutesOfInactivity() {
        Session.unlock(t0)
        assertFalse(Session.isTimedOut(t0 + 30 * minute - 1))
        assertTrue(Session.isTimedOut(t0 + 30 * minute))
    }

    @Test fun activityRestartsTheClock() {
        Session.unlock(t0)
        Session.touch(t0 + 20 * minute)
        assertFalse(Session.isTimedOut(t0 + 49 * minute))
        assertTrue(Session.isTimedOut(t0 + 50 * minute))
    }

    @Test fun touchIsIgnoredWhileLocked() {
        Session.lock()
        Session.touch(t0)
        Session.unlock(t0 + 5 * minute)
        assertEquals(t0 + 5 * minute, Session.lastActivityAt)
    }

    @Test fun msUntilTimeoutCountsDown() {
        Session.unlock(t0)
        assertEquals(30 * minute, Session.msUntilTimeout(t0))
        assertEquals(10 * minute, Session.msUntilTimeout(t0 + 20 * minute))
        assertEquals(0L, Session.msUntilTimeout(t0 + 45 * minute))
    }
}
