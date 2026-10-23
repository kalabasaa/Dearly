package com.dearly.app.utils

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * A wall-clock date and time without a time zone ("9:30 on 21 Oct 2026").
 * Reminders store this rather than an instant so they keep firing at the same local
 * time after a time-zone change.
 */
data class WallTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {

    fun toMillis(tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = GregorianCalendar(tz)
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, 0)
        return cal.timeInMillis
    }

    fun serialize(): String =
        String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)

    companion object {
        private val REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})$""")

        fun parse(value: String?): WallTime? {
            val m = REGEX.matchEntire(value ?: return null) ?: return null
            val g = m.groupValues.drop(1).map { it.toInt() }
            if (g[1] !in 1..12 || g[2] !in 1..31 || g[3] !in 0..23 || g[4] !in 0..59) return null
            return WallTime(g[0], g[1], g[2], g[3], g[4])
        }

        fun fromMillis(millis: Long, tz: TimeZone = TimeZone.getDefault()): WallTime {
            val cal = GregorianCalendar(tz)
            cal.timeInMillis = millis
            return WallTime(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
            )
        }
    }
}
