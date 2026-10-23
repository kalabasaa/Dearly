package com.dearly.app.notifications

import android.content.Context
import com.dearly.app.data.ServiceLocator
import com.dearly.app.model.Reminder

/** Keeps the reminders table and the alarms in step. Call from a background thread. */
object ReminderService {

    /** Saves and (re)schedules. Returns the reminder id and the scheduled time (null = nothing pending). */
    fun save(context: Context, reminder: Reminder): Pair<Long, Long?> {
        val id = ServiceLocator.reminders.save(reminder)
        NotificationHelper.cancel(context, id)
        val saved = ServiceLocator.reminders.get(id) ?: return id to null
        return id to ReminderScheduler.schedule(context, saved)
    }

    fun delete(context: Context, id: Long) {
        ReminderScheduler.cancel(context, id)
        NotificationHelper.cancel(context, id)
        ServiceLocator.reminders.delete(id)
    }

    fun setCompleted(context: Context, id: Long, completed: Boolean) {
        ServiceLocator.reminders.setCompleted(id, completed)
        NotificationHelper.cancel(context, id)
        val r = ServiceLocator.reminders.get(id) ?: return
        if (completed) ReminderScheduler.cancel(context, id) else ReminderScheduler.schedule(context, r)
    }
}
