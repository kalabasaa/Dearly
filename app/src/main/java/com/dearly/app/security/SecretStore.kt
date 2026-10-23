package com.dearly.app.security

/** Minimal storage abstraction for security metadata so [AuthManager] can be unit-tested without Android. */
interface SecretStore {
    fun getString(key: String): String?
    fun getLong(key: String, default: Long = 0L): Long

    /** Writes everything in one atomic commit. A null string value removes that key. */
    fun write(strings: Map<String, String?> = emptyMap(), longs: Map<String, Long> = emptyMap())
}
