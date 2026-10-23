package com.dearly.app.repository

import com.dearly.app.database.DearlyDatabase

/** Key/value app settings stored in SQLite. Security secrets live in [com.dearly.app.security.SecureStore]. */
class SettingsRepository(private val dbh: DearlyDatabase) {

    fun get(key: String): String? =
        dbh.readableDatabase.rawQuery("SELECT value FROM settings WHERE key = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun set(key: String, value: String) {
        dbh.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO settings(key, value) VALUES (?, ?)", arrayOf<Any?>(key, value)
        )
    }

    var userName: String
        get() = get(KEY_NAME).orEmpty()
        set(value) = set(KEY_NAME, value)

    var isOnboarded: Boolean
        get() = get(KEY_ONBOARDED) == "1"
        set(value) = set(KEY_ONBOARDED, if (value) "1" else "0")

    var darkMode: Boolean
        get() = get(KEY_THEME) == "dark"
        set(value) = set(KEY_THEME, if (value) "dark" else "light")

    var showTitlesInNotifications: Boolean
        get() = get(KEY_NOTIF_TITLES) == "1"
        set(value) = set(KEY_NOTIF_TITLES, if (value) "1" else "0")

    companion object {
        const val KEY_NAME = "user_name"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_THEME = "theme"
        const val KEY_NOTIF_TITLES = "notif_show_titles"
    }
}
