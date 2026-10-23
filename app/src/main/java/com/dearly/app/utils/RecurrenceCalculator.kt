package com.dearly.app.utils

import com.dearly.app.model.RepeatType
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** Pure reminder-schedule maths. Occurrence n is always computed from the original start (no drift). */
object RecurrenceCalculator {
    private const val DAY_MS = 86_400_000L

    fun occurrence(start: WallTime, repeat: RepeatType, n: Int, tz: TimeZone): Long {
        val cal = GregorianCalendar(tz)
        cal.clear()
        cal.set(start.year, start.month - 1, start.day, start.hour, start.minute, 0)
        when (repeat) {
            RepeatType.NONE -> Unit
            RepeatType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, n)
            RepeatType.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7 * n)
            RepeatType.MONTHLY -> cal.add(Calendar.MONTH, n) // clamps e.g. 31 Jan -> 28/29 Feb, then back to 31 Mar
        }
        return cal.timeInMillis
    }

    private fun approxPeriodMs(repeat: RepeatType): Long = when (repeat) {
        RepeatType.NONE -> DAY_MS
        RepeatType.DAILY -> DAY_MS
        RepeatType.WEEKLY -> 7 * DAY_MS
        RepeatType.MONTHLY -> 2_629_800_000L
    }

    /** First occurrence strictly after [afterMillis], or null (one-time reminder already in the past). */
    fun nextOccurrenceAfter(start: WallTime, repeat: RepeatType, afterMillis: Long, tz: TimeZone): Long? {
        val first = occurrence(start, repeat, 0, tz)
        if (repeat == RepeatType.NONE) return if (first > afterMillis) first else null
        if (first > afterMillis) return first
        var n = maxOf(0L, (afterMillis - first) / approxPeriodMs(repeat) - 2).toInt()
        while (n > 0 && occurrence(start, repeat, n - 1, tz) > afterMillis) n--
        while (occurrence(start, repeat, n, tz) <= afterMillis) n++
        return occurrence(start, repeat, n, tz)
    }

    /** All occurrences within [fromMillis, toMillis). Used to mark days on the calendar. */
    fun occurrencesBetween(start: WallTime, repeat: RepeatType, fromMillis: Long, toMillis: Long, tz: TimeZone): List<Long> {
        val out = ArrayList<Long>()
        var t = nextOccurrenceAfter(start, repeat, fromMillis - 1, tz)
        while (t != null && t < toMillis && out.size < 400) {
            out.add(t)
            t = nextOccurrenceAfter(start, repeat, t, tz)
        }
        return out
    }

    /**
     * The next time an alarm should fire, or null if nothing is pending.
     * An unfired one-time reminder whose time has passed (e.g. device was off) is returned as-is
     * so the scheduler can deliver it immediately.
     */
    fun nextTrigger(
        start: WallTime, repeat: RepeatType, completed: Boolean, lastFiredAt: Long?, now: Long, tz: TimeZone
    ): Long? {
        if (completed) return null
        return if (repeat == RepeatType.NONE) {
            if (lastFiredAt != null) null else occurrence(start, repeat, 0, tz)
        } else {
            nextOccurrenceAfter(start, repeat, now, tz)
        }
    }
}
