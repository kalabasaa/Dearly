package com.dearly.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.dearly.app.data.ServiceLocator
import com.dearly.app.model.Reminder
import com.dearly.app.utils.RecurrenceCalculator
import java.util.TimeZone

/**
 * Schedules one alarm per reminder (the next occurrence only; the receiver schedules the one after).
 *
 * Timing: exact alarms need a special permission on Android 12+, so there the alarm is
 * `setAndAllowWhileIdle` and Android may deliver it a few minutes late. Before Android 12 the
 * alarm is exact. All schedule data lives in SQLite, so [rescheduleAll] can rebuild everything
 * after a reboot, app update, clock change or time-zone change.
 */
object ReminderScheduler {
    const val ACTION = "com.dearly.app.action.REMINDER"
    const val EXTRA_ID = "reminder_id"

    private fun intentFor(context: Context, id: Long): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION)
            .setData(Uri.parse("dearly://reminder/$id"))
            .putExtra(EXTRA_ID, id)

    /** Replaces any existing alarm for this reminder. Returns the scheduled time or null if nothing is pending. */
    fun schedule(context: Context, reminder: Reminder, now: Long = System.currentTimeMillis()): Long? {
        cancel(context, reminder.id)
        val trigger = RecurrenceCalculator.nextTrigger(
            reminder.start, reminder.repeat, reminder.completed, reminder.lastFiredAt, now, TimeZone.getDefault()
        ) ?: return null
        val at = if (trigger <= now) now + 2_000L else trigger // overdue (e.g. device was off): deliver now
        val pi = PendingIntent.getBroadcast(
            context, reminder.id.toInt(), intentFor(context, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        return at
    }

    fun cancel(context: Context, reminderId: Long) {
        val existing = PendingIntent.getBroadcast(
            context, reminderId.toInt(), intentFor(context, reminderId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) {
            context.getSystemService(AlarmManager::class.java).cancel(existing)
            existing.cancel()
        }
    }

    /** Must run off the main thread (reads SQLite). */
    fun rescheduleAll(context: Context) {
        for (r in ServiceLocator.reminders.all()) schedule(context, r)
    }
}
