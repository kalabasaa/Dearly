package com.dearly.app

import com.dearly.app.model.RepeatType
import com.dearly.app.utils.RecurrenceCalculator
import com.dearly.app.utils.WallTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RecurrenceCalculatorTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val ny = TimeZone.getTimeZone("America/New_York")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0, tz: TimeZone = utc) = WallTime(y, m, d, h, min).toMillis(tz)

    @Test fun oneTimeInFutureReturnsStart() {
        val s = WallTime(2026, 10, 25, 9, 0)
        assertEquals(ms(2026, 10, 25), RecurrenceCalculator.nextOccurrenceAfter(s, RepeatType.NONE, ms(2026, 10, 20), utc))
    }

    @Test fun oneTimeInPastReturnsNull() {
        val s = WallTime(2026, 10, 1, 9, 0)
        assertNull(RecurrenceCalculator.nextOccurrenceAfter(s, RepeatType.NONE, ms(2026, 10, 20), utc))
    }

    @Test fun dailyFindsNextDay() {
        val s = WallTime(2026, 1, 1, 9, 0)
        assertEquals(ms(2026, 10, 21), RecurrenceCalculator.nextOccurrenceAfter(s, RepeatType.DAILY, ms(2026, 10, 20, 9, 0), utc))
        assertEquals(ms(2026, 10, 20), RecurrenceCalculator.nextOccurrenceAfter(s, RepeatType.DAILY, ms(2026, 10, 20, 8, 59), utc))
    }

    @Test fun weeklyKeepsWeekday() {
        val s = WallTime(2026, 10, 5, 9, 0) // a Monday
        assertEquals(ms(2026, 10, 26), RecurrenceCalculator.nextOccurrenceAfter(s, RepeatType.WEEKLY, ms(2026, 10, 20), utc))
    }

    @Test fun monthlyClampsShortMonthsWithoutDrift() {
        val s = WallTime(2027, 1, 31, 9, 0)
        assertEquals(ms(2027, 2, 28), RecurrenceCalculator.occurrence(s, RepeatType.MONTHLY, 1, utc))
        assertEquals(ms(2027, 3, 31), RecurrenceCalculator.occurrence(s, RepeatType.MONTHLY, 2, utc))
        assertEquals(ms(2027, 4, 30), RecurrenceCalculator.occurrence(s, RepeatType.MONTHLY, 3, utc))
    }

    @Test fun farFutureStillCorrect() {
        val s = WallTime(2020, 1, 31, 9, 0)
        val after = ms(2040, 6, 15)
        val next = RecurrenceCalculator.nextOccurrenceAfter(s, RepeatType.MONTHLY, after, utc)!!
        assertTrue(next > after)
        var n = 0
        while (RecurrenceCalculator.occurrence(s, RepeatType.MONTHLY, n, utc) <= after) n++
        assertEquals(RecurrenceCalculator.occurrence(s, RepeatType.MONTHLY, n, utc), next)
    }

    @Test fun dailyKeepsLocalTimeAcrossDst() {
        val s = WallTime(2026, 3, 7, 9, 0) // US DST starts 8 Mar 2026
        val day2 = RecurrenceCalculator.occurrence(s, RepeatType.DAILY, 2, ny)
        assertEquals(WallTime(2026, 3, 9, 9, 0), WallTime.fromMillis(day2, ny))
    }

    @Test fun sameWallTimeFiresAtDifferentInstantsInDifferentZones() {
        val s = WallTime(2026, 10, 25, 9, 0)
        assertTrue(s.toMillis(utc) != s.toMillis(ny))
    }

    @Test fun occurrencesBetweenListsMonthDays() {
        val s = WallTime(2026, 10, 1, 9, 0)
        val list = RecurrenceCalculator.occurrencesBetween(s, RepeatType.WEEKLY, ms(2026, 10, 1, 0), ms(2026, 11, 1, 0), utc)
        assertEquals(5, list.size) // 1, 8, 15, 22, 29
        assertEquals(1, RecurrenceCalculator.occurrencesBetween(s, RepeatType.NONE, ms(2026, 10, 1, 0), ms(2026, 11, 1, 0), utc).size)
        assertEquals(0, RecurrenceCalculator.occurrencesBetween(s, RepeatType.NONE, ms(2026, 11, 1, 0), ms(2026, 12, 1, 0), utc).size)
    }

    @Test fun nextTriggerRules() {
        val s = WallTime(2026, 10, 1, 9, 0)
        val now = ms(2026, 10, 20)
        assertNull(RecurrenceCalculator.nextTrigger(s, RepeatType.DAILY, true, null, now, utc))
        assertNull(RecurrenceCalculator.nextTrigger(s, RepeatType.NONE, false, 5L, now, utc))
        // unfired, overdue one-time reminder is still returned so it can be delivered after a reboot
        assertEquals(ms(2026, 10, 1), RecurrenceCalculator.nextTrigger(s, RepeatType.NONE, false, null, now, utc))
        assertEquals(ms(2026, 10, 21), RecurrenceCalculator.nextTrigger(s, RepeatType.DAILY, false, null, now, utc))
    }

    @Test fun wallTimeSerializationRoundTrip() {
        val w = WallTime(2026, 2, 3, 4, 5)
        assertEquals("2026-02-03 04:05", w.serialize())
        assertEquals(w, WallTime.parse(w.serialize()))
        assertNull(WallTime.parse("nonsense")); assertNull(WallTime.parse("2026-13-01 00:00")); assertNull(WallTime.parse(null))
    }
}
