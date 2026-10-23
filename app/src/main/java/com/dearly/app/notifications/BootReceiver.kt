package com.dearly.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dearly.app.data.ServiceLocator

/** Rebuilds every pending alarm after reboot, app update, clock change or time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val app = context.applicationContext
        val pending = goAsync()
        ServiceLocator.io.execute {
            try {
                ReminderScheduler.rescheduleAll(app)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Reschedule after $action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
