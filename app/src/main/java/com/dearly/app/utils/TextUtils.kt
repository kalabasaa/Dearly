package com.dearly.app.utils

import java.util.Locale

object TextUtils {
    /** Escapes LIKE wildcards; use with `ESCAPE '\'`. */
    fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Trims, collapses inner whitespace and caps the length. */
    fun cleanName(raw: String, maxLength: Int): String =
        raw.trim().replace(Regex("\\s+"), " ").take(maxLength)

    fun tagKey(name: String): String = name.lowercase(Locale.ROOT)

    fun normalizeAnswer(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }
}
