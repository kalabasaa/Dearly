package com.dearly.app

import com.dearly.app.security.AttemptPolicy
import com.dearly.app.security.AttemptState
import com.dearly.app.security.PinHasher
import com.dearly.app.utils.TextUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityLogicTest {
    @Test fun hashIsDeterministicPerSaltAndNeverPlaintext() {
        val salt = PinHasher.newSalt()
        val h1 = PinHasher.hash("1234", salt, 1000)
        val h2 = PinHasher.hash("1234", salt, 1000)
        assertArrayEquals(h1, h2)
        assertFalse(PinHasher.toHex(h1).contains("1234"))
        assertEquals(32, h1.size)
    }

    @Test fun differentSaltsGiveDifferentHashes() {
        val a = PinHasher.hash("1234", PinHasher.newSalt(), 1000)
        val b = PinHasher.hash("1234", PinHasher.newSalt(), 1000)
        assertNotEquals(PinHasher.toHex(a), PinHasher.toHex(b))
    }

    @Test fun matchesAcceptsRightRejectsWrong() {
        val salt = PinHasher.newSalt()
        val stored = PinHasher.hash("4821", salt, 1000)
        assertTrue(PinHasher.matches("4821", salt, stored, 1000))
        assertFalse(PinHasher.matches("4822", salt, stored, 1000))
    }

    @Test fun hexRoundTrip() {
        val bytes = PinHasher.newSalt()
        assertArrayEquals(bytes, PinHasher.fromHex(PinHasher.toHex(bytes)))
    }

    @Test fun lockoutStartsAfterFiveFailuresAndEscalates() {
        var s = AttemptState()
        val now = 1_000_000L
        for (i in 1..4) {
            s = AttemptPolicy.onFailure(s, now)
            assertEquals(0L, AttemptPolicy.remainingLockMs(s, now))
        }
        s = AttemptPolicy.onFailure(s, now)
        assertEquals(30_000L, AttemptPolicy.remainingLockMs(s, now))
        assertEquals(0L, AttemptPolicy.remainingLockMs(s, now + 30_000L))
        repeat(5) { s = AttemptPolicy.onFailure(s, now + 40_000L) }
        assertEquals(60_000L, AttemptPolicy.remainingLockMs(s, now + 40_000L))
    }

    @Test fun lockoutIsCapped() {
        assertEquals(AttemptPolicy.MAX_LOCK_MS, AttemptPolicy.lockDurationMs(5 * 40))
        val s = AttemptState(50, 10_000_000_000L)
        assertEquals(AttemptPolicy.MAX_LOCK_MS, AttemptPolicy.remainingLockMs(s, 0L))
    }

    @Test fun successResetsCounters() {
        assertEquals(AttemptState(), AttemptPolicy.onSuccess())
        assertEquals(5, AttemptPolicy.attemptsBeforeLock(AttemptState()))
        assertEquals(1, AttemptPolicy.attemptsBeforeLock(AttemptState(4, 0)))
    }

    @Test fun pinValidation() {
        assertTrue(TextUtils.isValidPin("0123"))
        assertFalse(TextUtils.isValidPin("123")); assertFalse(TextUtils.isValidPin("12345")); assertFalse(TextUtils.isValidPin("12a4"))
        assertFalse(TextUtils.isValidPin("١٢٣٤")) // non-ASCII digits rejected
    }

    @Test fun answerNormalizationIgnoresCaseAndSpacing() {
        assertEquals(TextUtils.normalizeAnswer("  Chicken   Adobo "), TextUtils.normalizeAnswer("chicken adobo"))
    }

    @Test fun likeEscaping() {
        assertEquals("100\\%\\_a\\\\b", TextUtils.escapeLike("100%_a\\b"))
    }
}
