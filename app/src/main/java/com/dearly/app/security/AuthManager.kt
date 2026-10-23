package com.dearly.app.security

import com.dearly.app.utils.TextUtils

sealed class AuthResult {
    object Success : AuthResult()
    data class Wrong(val attemptsLeftBeforeLock: Int) : AuthResult()
    data class LockedOut(val remainingMs: Long) : AuthResult()
    object NotConfigured : AuthResult()
}

/**
 * PIN + recovery-question logic. Secrets are only ever stored as salted PBKDF2 hashes, and both
 * guessing channels (PIN, recovery answer) are rate limited independently.
 */
class AuthManager(
    private val store: SecretStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val iterations: Int = PinHasher.ITERATIONS
) {
    private enum class Channel(val prefix: String) { PIN("pin"), ANSWER("ans") }

    fun isPinSet(): Boolean = store.getString("pin_hash") != null

    fun setPin(pin: String) {
        require(TextUtils.isValidPin(pin)) { "PIN must be 4 digits" }
        val salt = PinHasher.newSalt()
        store.write(
            strings = mapOf(
                "pin_salt" to PinHasher.toHex(salt),
                "pin_hash" to PinHasher.toHex(PinHasher.hash(pin, salt, iterations)),
                "pin_iter" to iterations.toString()
            ),
            longs = mapOf("pin_fails" to 0L, "pin_lock" to 0L)
        )
    }

    fun verifyPin(pin: String): AuthResult = verify(Channel.PIN, pin)

    fun pinLockRemainingMs(): Long = AttemptPolicy.remainingLockMs(state(Channel.PIN), clock())

    // ---- recovery question ----

    fun hasRecovery(): Boolean = store.getString("ans_hash") != null

    fun recoveryQuestion(): String? = store.getString("rq")

    fun setRecovery(question: String, answer: String) {
        val q = question.trim()
        val a = TextUtils.normalizeAnswer(answer)
        require(q.isNotEmpty() && a.isNotEmpty()) { "Question and answer required" }
        val salt = PinHasher.newSalt()
        store.write(
            strings = mapOf(
                "rq" to q,
                "ans_salt" to PinHasher.toHex(salt),
                "ans_hash" to PinHasher.toHex(PinHasher.hash(a, salt, iterations)),
                "ans_iter" to iterations.toString()
            ),
            longs = mapOf("ans_fails" to 0L, "ans_lock" to 0L)
        )
    }

    fun verifyRecoveryAnswer(answer: String): AuthResult = verify(Channel.ANSWER, TextUtils.normalizeAnswer(answer))

    fun answerLockRemainingMs(): Long = AttemptPolicy.remainingLockMs(state(Channel.ANSWER), clock())

    // ---- internals ----

    private fun state(ch: Channel) = AttemptState(
        failures = store.getLong(ch.prefix + "_fails").toInt(),
        lockUntil = store.getLong(ch.prefix + "_lock")
    )

    private fun save(ch: Channel, s: AttemptState) {
        store.write(longs = mapOf(ch.prefix + "_fails" to s.failures.toLong(), ch.prefix + "_lock" to s.lockUntil))
    }

    private fun verify(ch: Channel, candidate: String): AuthResult {
        val hashHex = store.getString(ch.prefix + "_hash")
        val saltHex = store.getString(ch.prefix + "_salt")
        if (hashHex == null || saltHex == null) return AuthResult.NotConfigured

        val now = clock()
        val current = state(ch)
        val locked = AttemptPolicy.remainingLockMs(current, now)
        if (locked > 0) return AuthResult.LockedOut(locked)

        val iter = store.getString(ch.prefix + "_iter")?.toIntOrNull() ?: iterations
        val ok = PinHasher.matches(candidate, PinHasher.fromHex(saltHex), PinHasher.fromHex(hashHex), iter)
        if (ok) {
            save(ch, AttemptPolicy.onSuccess())
            return AuthResult.Success
        }
        val failed = AttemptPolicy.onFailure(current, now)
        save(ch, failed)
        val remaining = AttemptPolicy.remainingLockMs(failed, now)
        return if (remaining > 0) AuthResult.LockedOut(remaining)
        else AuthResult.Wrong(AttemptPolicy.attemptsBeforeLock(failed))
    }
}
