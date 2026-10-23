package com.dearly.app

import com.dearly.app.security.AuthManager
import com.dearly.app.security.AuthResult
import com.dearly.app.security.SecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore : SecretStore {
    val strings = HashMap<String, String>()
    val longs = HashMap<String, Long>()
    override fun getString(key: String) = strings[key]
    override fun getLong(key: String, default: Long) = longs[key] ?: default
    override fun write(strings: Map<String, String?>, longs: Map<String, Long>) {
        strings.forEach { (k, v) -> if (v == null) this.strings.remove(k) else this.strings[k] = v }
        this.longs.putAll(longs)
    }
}

class AuthManagerTest {
    private var now = 1_000_000L
    private val store = FakeStore()
    private val auth = AuthManager(store, { now }, iterations = 1000)

    @Test fun notConfiguredBeforeSetup() {
        assertFalse(auth.isPinSet())
        assertEquals(AuthResult.NotConfigured, auth.verifyPin("1234"))
    }

    @Test fun correctPinSucceedsWrongPinFails() {
        auth.setPin("2468")
        assertTrue(auth.isPinSet())
        assertEquals(AuthResult.Success, auth.verifyPin("2468"))
        assertEquals(AuthResult.Wrong(4), auth.verifyPin("1111"))
    }

    @Test fun pinIsNeverStoredInPlainText() {
        auth.setPin("2468")
        assertTrue(store.strings.values.none { it == "2468" || it.contains("2468") })
    }

    @Test fun temporaryLockoutAfterFiveWrongPinsEvenForCorrectPin() {
        auth.setPin("2468")
        repeat(4) { auth.verifyPin("0000") }
        val fifth = auth.verifyPin("0000")
        assertTrue(fifth is AuthResult.LockedOut)
        assertTrue(auth.verifyPin("2468") is AuthResult.LockedOut) // correct PIN rejected while locked
        now += 30_001
        assertEquals(AuthResult.Success, auth.verifyPin("2468"))
        assertEquals(0L, auth.pinLockRemainingMs())
    }

    @Test fun successResetsFailureCounter() {
        auth.setPin("2468")
        repeat(3) { auth.verifyPin("0000") }
        auth.verifyPin("2468")
        assertEquals(AuthResult.Wrong(4), auth.verifyPin("0000"))
    }

    @Test fun invalidPinRejectedOnSet() {
        var threw = false
        try { auth.setPin("12") } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        assertFalse(auth.isPinSet())
    }

    @Test fun recoveryAnswerIsCaseAndSpaceInsensitive() {
        auth.setRecovery("Favorite food?", "Chicken  Adobo")
        assertEquals("Favorite food?", auth.recoveryQuestion())
        assertEquals(AuthResult.Success, auth.verifyRecoveryAnswer("  chicken adobo "))
        assertTrue(auth.verifyRecoveryAnswer("pizza") is AuthResult.Wrong)
    }

    @Test fun recoveryAnswerIsRateLimitedSeparatelyFromPin() {
        auth.setPin("2468")
        auth.setRecovery("Pet?", "rex")
        repeat(5) { auth.verifyRecoveryAnswer("nope") }
        assertTrue(auth.verifyRecoveryAnswer("rex") is AuthResult.LockedOut)
        assertEquals(AuthResult.Success, auth.verifyPin("2468"))
    }

    @Test fun recoveryAnswerNotStoredInPlainText() {
        auth.setRecovery("Pet?", "secretrex")
        assertTrue(store.strings.filterKeys { it != "rq" }.values.none { it.contains("secretrex") })
    }

    @Test fun resettingPinAfterRecoveryReplacesOldPin() {
        auth.setPin("1111")
        repeat(5) { auth.verifyPin("0000") } // locked
        auth.setPin("9999") // done only after the recovery answer was verified
        assertEquals(AuthResult.Success, auth.verifyPin("9999"))
        assertTrue(auth.verifyPin("1111") is AuthResult.Wrong)
    }

}
