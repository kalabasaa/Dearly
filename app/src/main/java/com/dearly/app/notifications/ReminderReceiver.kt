package com.dearly.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dearly.app.data.ServiceLocator

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val app = context.applicationContext
        val pending = goAsync()
        ServiceLocator.io.execute {
            try {
                val reminder = ServiceLocator.reminders.get(id)
                if (reminder != null && !reminder.completed) {
                    val now = System.currentTimeMillis()
                    ServiceLocator.reminders.markFired(id, now)
                    NotificationHelper.show(app, reminder, ServiceLocator.settings.showTitlesInNotifications)
                    // Repeating reminders: arm the next occurrence. One-time: nothing further.
                    ReminderScheduler.schedule(app, reminder.copy(lastFiredAt = now), now)
                }
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Failed to deliver reminder $id", e)
            } finally {
                pending.finish()
            }
        }
    }
}
