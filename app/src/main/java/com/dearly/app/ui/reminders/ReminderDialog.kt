package com.dearly.app.ui.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.dearly.app.R
import com.dearly.app.model.Reminder
import com.dearly.app.model.RepeatType
import com.dearly.app.notifications.NotificationHelper
import com.dearly.app.notifications.ReminderService
import com.dearly.app.databinding.DialogReminderBinding
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.library.Pickers
import com.dearly.app.utils.Formatters
import com.dearly.app.utils.TextUtils
import com.dearly.app.utils.WallTime
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

/** Create or edit a reminder; saving schedules (or reschedules) a real alarm. */
object ReminderDialog {

    fun show(
        activity: BaseActivity,
        existing: Reminder?,
        defaultDate: WallTime?,
        presetNoteId: Long?,
        presetNoteTitle: String?,
        onSaved: () -> Unit
    ) {
        val b = DialogReminderBinding.inflate(activity.layoutInflater)
        val soon = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0) }
        var start = existing?.start ?: WallTime(
            defaultDate?.year ?: soon.get(Calendar.YEAR),
            defaultDate?.month ?: (soon.get(Calendar.MONTH) + 1),
            defaultDate?.day ?: soon.get(Calendar.DAY_OF_MONTH),
            soon.get(Calendar.HOUR_OF_DAY), 0
        )
        var repeat = existing?.repeat ?: RepeatType.NONE
        var noteId: Long? = existing?.noteId ?: presetNoteId
        var noteTitle: String? = existing?.noteTitle ?: presetNoteTitle

        b.etTitle.setText(existing?.title.orEmpty())
        b.etDesc.setText(existing?.description.orEmpty())

        fun render() {
            b.btnDate.text = Formatters.shortFullDate(activity, start.toMillis())
            b.btnTime.text = Formatters.time(activity, start.toMillis())
            b.btnNote.text = noteTitle?.let { activity.getString(R.string.linked_note_named, it) } ?: activity.getString(R.string.link_note)
        }
        render()

        val repeatLabels = activity.resources.getStringArray(R.array.repeat_options)
        b.actvRepeat.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, repeatLabels))
        b.actvRepeat.setText(repeatLabels[repeat.code], false)
        b.actvRepeat.setOnItemClickListener { _, _, position, _ -> repeat = RepeatType.fromCode(position) }

        b.btnDate.setOnClickListener {
            DatePickerDialog(activity, { _, y, m, d ->
                start = start.copy(year = y, month = m + 1, day = d)
                render()
            }, start.year, start.month - 1, start.day).show()
        }
        b.btnTime.setOnClickListener {
            TimePickerDialog(activity, { _, h, min ->
                start = start.copy(hour = h, minute = min)
                render()
            }, start.hour, start.minute, DateFormat.is24HourFormat(activity)).show()
        }
        b.btnNote.setOnClickListener {
            Pickers.chooseNote(activity) { note ->
                noteId = note?.id
                noteTitle = note?.title?.ifBlank { activity.getString(R.string.untitled) }
                render()
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) R.string.new_reminder else R.string.edit_reminder)
            .setView(b.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                b.tilTitle.error = null
                b.tvError.text = ""
                val title = TextUtils.cleanName(b.etTitle.text.toString(), 80)
                if (title.isEmpty()) {
                    b.tilTitle.error = activity.getString(R.string.error_reminder_title_required)
                    return@setOnClickListener
                }
                if (repeat == RepeatType.NONE && start.toMillis() <= System.currentTimeMillis()) {
                    b.tvError.text = activity.getString(R.string.error_reminder_in_past)
                    return@setOnClickListener
                }
                positive.isEnabled = false // no duplicate reminders from a double tap
                val reminder = Reminder(
                    id = existing?.id ?: 0L, title = title, description = b.etDesc.text.toString().trim(),
                    start = start, repeat = repeat, completed = false, lastFiredAt = null,
                    noteId = noteId, noteTitle = noteTitle
                )
                activity.ensureNotificationPermission { _ ->
                    activity.background(
                        onError = {
                            positive.isEnabled = true
                            activity.toast(R.string.error_generic)
                        },
                        work = { ReminderService.save(activity.applicationContext, reminder) }
                    ) { (_, scheduledAt) ->
                        dialog.dismiss()
                        onSaved()
                        when {
                            !NotificationHelper.canPost(activity) -> activity.toast(R.string.reminder_saved_notifications_off)
                            scheduledAt != null -> activity.toast(
                                activity.getString(R.string.reminder_set_for, Formatters.dateTime(activity, scheduledAt))
                            )
                            else -> activity.toast(R.string.reminder_saved)
                        }
                    }
                }
            }
        }
        dialog.show()
    }
}
