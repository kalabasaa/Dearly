package com.dearly.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Security metadata (PIN hash, recovery-answer hash, attempt counters) is kept in
 * EncryptedSharedPreferences backed by the Android Keystore. Only salted PBKDF2 hashes are stored,
 * never the PIN or the recovery answer.
 *
 * If the Keystore is unusable on a device the store falls back to private plain SharedPreferences
 * (still hashes only) and [isEncrypted] reports false.
 */
class SecureStore(context: Context) : SecretStore {
    private val prefs: SharedPreferences
    val isEncrypted: Boolean

    init {
        var created: SharedPreferences? = null
        try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            created = EncryptedSharedPreferences.create(
                context, FILE, key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Encrypted preferences unavailable, using private fallback", e)
        }
        isEncrypted = created != null
        prefs = created ?: context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)

    override fun write(strings: Map<String, String?>, longs: Map<String, Long>) {
        val editor = prefs.edit()
        for ((k, v) in strings) if (v == null) editor.remove(k) else editor.putString(k, v)
        for ((k, v) in longs) editor.putLong(k, v)
        // commit() (not apply()) so a PIN change is durable before the UI reports success.
        if (!editor.commit()) throw IllegalStateException("Could not persist security settings")
    }

    private companion object {
        const val TAG = "SecureStore"
        const val FILE = "dearly_secure"
        const val FALLBACK_FILE = "dearly_secure_fallback"
    }
}
