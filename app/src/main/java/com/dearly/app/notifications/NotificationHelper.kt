package com.dearly.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.dearly.app.R
import com.dearly.app.model.Reminder
import com.dearly.app.ui.MainActivity
import com.dearly.app.ui.editor.EditorActivity

object NotificationHelper {
    const val CHANNEL_ID = "dearly_reminders"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True only if a notification would really be shown (permission granted, app and channel not blocked). */
    fun canPost(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        val channel = nm.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Posts the reminder. Diary content stays private: by default only a generic message is shown;
     * the reminder title appears only when the user turned that setting on.
     */
    fun show(context: Context, reminder: Reminder, showTitle: Boolean) {
        ensureChannel(context)
        if (!canPost(context)) return // permission denied or notifications blocked: never pretend to deliver
        val nm = context.getSystemService(NotificationManager::class.java)

        val intent = if (reminder.noteId != null) {
            EditorActivity.newIntent(context, reminder.noteId)
        } else {
            MainActivity.newIntent(context, MainActivity.TAB_REMINDERS)
        }
        val tap = PendingIntent.getActivity(
            context, reminder.id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (showTitle) reminder.title else context.getString(R.string.notif_generic_text)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        // One notification id per reminder: a repeated delivery replaces instead of duplicating.
        nm.notify(NOTIF_BASE + reminder.id.toInt(), notification)
    }

    fun cancel(context: Context, reminderId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_BASE + reminderId.toInt())
    }

    private const val NOTIF_BASE = 10_000
}
