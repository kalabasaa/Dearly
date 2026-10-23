package com.dearly.app.ui.reminders

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.FragmentRemindersBinding
import com.dearly.app.databinding.ItemDayBinding
import com.dearly.app.databinding.ItemReminderBinding
import com.dearly.app.model.Reminder
import com.dearly.app.model.RepeatType
import com.dearly.app.notifications.NotificationHelper
import com.dearly.app.notifications.ReminderService
import com.dearly.app.security.Session
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.background
import com.dearly.app.ui.common.show
import com.dearly.app.utils.Formatters
import com.dearly.app.utils.RecurrenceCalculator
import com.dearly.app.utils.WallTime
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.TimeZone

/** Monthly calendar with reminder markers and the selected day's reminders underneath. */
class RemindersFragment : Fragment() {
    private var _b: FragmentRemindersBinding? = null
    private val b get() = _b!!

    private val month: Calendar = startOfDay(Calendar.getInstance()).apply { set(Calendar.DAY_OF_MONTH, 1) }
    private var selected: Calendar = startOfDay(Calendar.getInstance())
    private var all: List<Reminder> = emptyList()
    private val cells = ArrayList<ItemDayBinding>()
    private lateinit var adapter: EntryAdapter

    private data class Entry(val reminder: Reminder, val occurrence: Long)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentRemindersBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as BaseActivity
        adapter = EntryAdapter(
            onToggle = { r, done ->
                host.background(work = { ReminderService.setCompleted(host.applicationContext, r.id, done) }) { refresh() }
            },
            onClick = { r -> ReminderDialog.show(host, r, null, null, null) { refresh() } },
            onDelete = { r -> confirmDelete(host, r) }
        )
        b.rv.layoutManager = LinearLayoutManager(requireContext())
        b.rv.adapter = adapter

        buildWeekdayHeader()
        buildGrid()
        b.btnPrev.setOnClickListener { shiftMonth(-1) }
        b.btnNext.setOnClickListener { shiftMonth(1) }
        b.btnToday.setOnClickListener {
            selected = startOfDay(Calendar.getInstance())
            month.timeInMillis = selected.timeInMillis
            month.set(Calendar.DAY_OF_MONTH, 1)
            renderAll()
        }
        b.fabAdd.setOnClickListener {
            val day = WallTime(selected.get(Calendar.YEAR), selected.get(Calendar.MONTH) + 1, selected.get(Calendar.DAY_OF_MONTH), 9, 0)
            ReminderDialog.show(host, null, day, null, null) { refresh() }
        }
        b.btnFixNotifications.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            )
        }
        renderAll()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cells.clear()
        _b = null
    }

    fun refresh() {
        if (_b == null || !Session.unlocked || isHidden) return
        b.cardNotice.show(!NotificationHelper.canPost(requireContext()))
        background(work = { ServiceLocator.reminders.all() }) {
            all = it
            renderAll()
        }
    }

    private fun confirmDelete(host: BaseActivity, r: Reminder) {
        MaterialAlertDialogBuilder(host)
            .setTitle(R.string.delete_reminder_title)
            .setMessage(r.title)
            .setPositiveButton(R.string.delete) { _, _ ->
                host.background(work = { ReminderService.delete(host.applicationContext, r.id) }) { refresh() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ calendar

    private fun shiftMonth(delta: Int) {
        month.add(Calendar.MONTH, delta)
        val today = startOfDay(Calendar.getInstance())
        selected = if (month.get(Calendar.YEAR) == today.get(Calendar.YEAR) && month.get(Calendar.MONTH) == today.get(Calendar.MONTH)) today
        else (month.clone() as Calendar)
        renderAll()
    }

    private fun buildWeekdayHeader() {
        val names = DateFormatSymbols.getInstance().shortWeekdays // index 1 = Sunday
        val first = Calendar.getInstance().firstDayOfWeek
        for (i in 0 until 7) {
            val day = ((first - 1 + i) % 7) + 1
            val tv = TextView(requireContext(), null, 0, R.style.TextAppearance_Dearly_Meta)
            tv.text = names[day]
            tv.gravity = android.view.Gravity.CENTER
            b.weekdays.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun buildGrid() {
        for (row in 0 until 6) {
            val line = LinearLayout(requireContext())
            line.orientation = LinearLayout.HORIZONTAL
            for (col in 0 until 7) {
                val cell = ItemDayBinding.inflate(layoutInflater, line, false)
                line.addView(cell.root)
                cells.add(cell)
            }
            b.calendar.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun dayKey(c: Calendar) = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

    private fun gridStart(): Calendar {
        val start = month.clone() as Calendar
        val offset = (start.get(Calendar.DAY_OF_WEEK) - start.firstDayOfWeek + 7) % 7
        start.add(Calendar.DAY_OF_MONTH, -offset)
        return start
    }

    private fun renderAll() {
        if (_b == null || cells.size < 42) return
        val ctx = requireContext()
        b.tvMonth.text = Formatters.monthYear(ctx, month.timeInMillis)

        val start = gridStart()
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 42) }
        val tz = TimeZone.getDefault()
        val marked = HashSet<Int>()
        val scratch = Calendar.getInstance()
        for (r in all) {
            for (t in RecurrenceCalculator.occurrencesBetween(r.start, r.repeat, start.timeInMillis, end.timeInMillis, tz)) {
                scratch.timeInMillis = t
                marked.add(dayKey(scratch))
            }
        }

        val todayKey = dayKey(Calendar.getInstance())
        val selectedKey = dayKey(selected)
        val cursor = start.clone() as Calendar
        for (i in 0 until 42) {
            val cell = cells[i]
            val key = dayKey(cursor)
            val inMonth = cursor.get(Calendar.MONTH) == month.get(Calendar.MONTH)
            val dayDate = cursor.clone() as Calendar
            cell.tvDay.text = cursor.get(Calendar.DAY_OF_MONTH).toString()
            cell.tvDay.alpha = if (inMonth) 1f else 0.4f
            cell.tvDay.setBackgroundResource(
                when {
                    key == selectedKey -> R.drawable.bg_day_selected
                    key == todayKey -> R.drawable.bg_day_today
                    else -> 0
                }
            )
            cell.tvDay.setTextColor(ctx.getColor(if (key == selectedKey) R.color.dearly_on_primary else R.color.dearly_text))
            cell.dot.show(key in marked)
            cell.root.contentDescription = Formatters.fullDate(ctx, dayDate.timeInMillis) +
                (if (key in marked) ", " + getString(R.string.has_reminders) else "") +
                (if (key == todayKey) ", " + getString(R.string.today) else "")
            cell.root.setOnClickListener {
                selected = startOfDay(dayDate)
                if (!inMonth) {
                    month.timeInMillis = selected.timeInMillis
                    month.set(Calendar.DAY_OF_MONTH, 1)
                }
                renderAll()
            }
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        renderList(tz)
    }

    private fun renderList(tz: TimeZone) {
        val ctx = requireContext()
        b.tvSelectedDate.text = Formatters.fullDate(ctx, selected.timeInMillis)
        val from = selected.timeInMillis
        val to = (selected.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        val entries = all.flatMap { r ->
            RecurrenceCalculator.occurrencesBetween(r.start, r.repeat, from, to, tz).map { Entry(r, it) }
        }.sortedBy { it.occurrence }
        adapter.submitList(entries)
        b.tvEmpty.show(entries.isEmpty())
    }

    private fun startOfDay(c: Calendar): Calendar = (c.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    // ------------------------------------------------------------------ list adapter

    private inner class EntryAdapter(
        val onToggle: (Reminder, Boolean) -> Unit,
        val onClick: (Reminder) -> Unit,
        val onDelete: (Reminder) -> Unit
    ) : ListAdapter<Entry, EntryAdapter.VH>(DIFF) {

        inner class VH(val b: ItemReminderBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = getItem(position)
            val r = entry.reminder
            val ctx = holder.b.root.context
            holder.b.tvTitle.text = r.title
            val repeatLabel = when (r.repeat) {
                RepeatType.NONE -> ""
                else -> "  •  " + ctx.resources.getStringArray(R.array.repeat_options)[r.repeat.code]
            }
            holder.b.tvTime.text = DateUtils.formatDateTime(ctx, entry.occurrence, DateUtils.FORMAT_SHOW_TIME) + repeatLabel
            holder.b.tvNote.show(r.noteTitle != null)
            holder.b.tvNote.text = r.noteTitle?.let { ctx.getString(R.string.linked_note_named, it) }.orEmpty()
            holder.b.cbDone.setOnCheckedChangeListener(null)
            holder.b.cbDone.isChecked = r.completed
            holder.b.cbDone.setOnCheckedChangeListener { _, checked -> onToggle(r, checked) }
            holder.b.root.alpha = if (r.completed) 0.6f else 1f
            holder.b.root.setOnClickListener { onClick(r) }
            holder.b.btnDelete.setOnClickListener { onDelete(r) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Entry>() {
            override fun areItemsTheSame(a: Entry, b: Entry) = a.reminder.id == b.reminder.id && a.occurrence == b.occurrence
            override fun areContentsTheSame(a: Entry, b: Entry) = a == b
        }
    }
}
