package com.dearly.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ActivityRecentlyDeletedBinding
import com.dearly.app.databinding.ItemDeletedBinding
import com.dearly.app.databinding.ItemHeaderBinding
import com.dearly.app.model.DeletedFolder
import com.dearly.app.model.DeletedNote
import com.dearly.app.security.AuthResult
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.PinPrompt
import com.dearly.app.ui.common.lockedMessage
import com.dearly.app.ui.common.show
import com.dearly.app.utils.Formatters
import com.dearly.app.utils.RetentionPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Requires the PIN every time it is opened. The list stays hidden and nothing is loaded until the PIN
 * has been verified; leaving the screen forgets the verification.
 */
class RecentlyDeletedActivity : BaseActivity() {
    private lateinit var b: ActivityRecentlyDeletedBinding
    private var verified = false
    private var prompt: PinPrompt? = null
    private val adapter = RowAdapter()

    private sealed class Row {
        data class Header(val textRes: Int) : Row()
        data class FolderRow(val item: DeletedFolder, val noteCount: Int) : Row()
        data class NoteRow(val item: DeletedNote) : Row()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRecentlyDeletedBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        if (isUnlocked && !verified && prompt == null) askPin()
    }

    override fun onStop() {
        super.onStop()
        verified = false
        b.content.show(false)
        prompt?.dismiss()
        prompt = null
    }

    private fun askPin() {
        prompt = PinPrompt(
            this, getString(R.string.deleted_enter_pin), getString(R.string.deleted_pin_subtitle),
            onPin = { pin, p ->
                background(
                    onError = { p.error(getString(R.string.error_generic)) },
                    work = { ServiceLocator.auth.verifyPin(pin) }
                ) { result ->
                    when (result) {
                        AuthResult.Success -> {
                            verified = true
                            prompt = null
                            p.dismiss()
                            b.content.show(true)
                            load()
                        }
                        is AuthResult.Wrong -> p.error(getString(R.string.pin_wrong_attempts, result.attemptsLeftBeforeLock))
                        is AuthResult.LockedOut -> p.error(lockedMessage(this, result.remainingMs))
                        AuthResult.NotConfigured -> { p.dismiss(); finish() }
                    }
                }
            },
            onCancel = {
                prompt = null
                finish()
            }
        ).show()
    }

    private fun load() {
        if (!verified) return
        background(work = {
            ServiceLocator.trash.purgeExpired() // by stored timestamps, so nothing depends on when the app was last opened
            val folders = ServiceLocator.trash.deletedFolders()
            val notes = ServiceLocator.trash.deletedNotes()
            val rows = ArrayList<Row>()
            if (folders.isNotEmpty()) {
                rows.add(Row.Header(R.string.section_folders))
                folders.forEach { rows.add(Row.FolderRow(it, ServiceLocator.trash.countNotesDeletedWithFolder(it.folder.id))) }
            }
            if (notes.isNotEmpty()) {
                rows.add(Row.Header(R.string.section_notes))
                notes.forEach { rows.add(Row.NoteRow(it)) }
            }
            rows
        }) { rows ->
            adapter.submitList(rows)
            b.tvEmpty.show(rows.isEmpty())
        }
    }

    private fun infoText(deletedAt: Long, extra: String? = null): String {
        val days = RetentionPolicy.daysLeft(deletedAt, System.currentTimeMillis())
        val base = getString(R.string.deleted_on, Formatters.dateTime(this, deletedAt)) + "  •  " +
            resources.getQuantityString(R.plurals.days_left, days, days)
        return if (extra == null) base else "$base\n$extra"
    }

    private fun restore(folderId: Long?, noteId: Long?) {
        background(work = {
            if (folderId != null) ServiceLocator.trash.restoreFolder(folderId)
            if (noteId != null) ServiceLocator.trash.restoreNote(noteId)
        }) {
            toast(R.string.restored)
            load()
        }
    }

    private fun confirmPurge(title: String, folderId: Long?, noteId: Long?) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_forever_title)
            .setMessage(getString(R.string.delete_forever_message, title))
            .setPositiveButton(R.string.delete_forever) { _, _ ->
                background(work = {
                    if (folderId != null) ServiceLocator.trash.purgeFolder(folderId)
                    if (noteId != null) ServiceLocator.trash.purgeNote(noteId)
                }) { load() }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class RowAdapter : ListAdapter<Row, RecyclerView.ViewHolder>(DIFF) {
        override fun getItemViewType(position: Int) = if (getItem(position) is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) object : RecyclerView.ViewHolder(ItemHeaderBinding.inflate(inflater, parent, false).root) {}
            else object : RecyclerView.ViewHolder(ItemDeletedBinding.inflate(inflater, parent, false).root) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is Row.Header -> ItemHeaderBinding.bind(holder.itemView).tvHeader.setText(row.textRes)
                is Row.FolderRow -> {
                    val v = ItemDeletedBinding.bind(holder.itemView)
                    v.tvTitle.text = row.item.folder.name
                    v.tvInfo.text = infoText(
                        row.item.deletedAt,
                        resources.getQuantityString(R.plurals.deleted_folder_contents, row.noteCount, row.noteCount)
                    )
                    v.btnRestore.setOnClickListener { restore(row.item.folder.id, null) }
                    v.btnDeleteForever.setOnClickListener { confirmPurge(row.item.folder.name, row.item.folder.id, null) }
                }
                is Row.NoteRow -> {
                    val v = ItemDeletedBinding.bind(holder.itemView)
                    val title = row.item.note.title.ifBlank { getString(R.string.untitled) }
                    v.tvTitle.text = title
                    v.tvInfo.text = infoText(row.item.deletedAt)
                    v.btnRestore.setOnClickListener { restore(null, row.item.note.id) }
                    v.btnDeleteForever.setOnClickListener { confirmPurge(title, null, row.item.note.id) }
                }
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(a: Row, b: Row) = when {
                a is Row.Header && b is Row.Header -> a.textRes == b.textRes
                a is Row.FolderRow && b is Row.FolderRow -> a.item.folder.id == b.item.folder.id
                a is Row.NoteRow && b is Row.NoteRow -> a.item.note.id == b.item.note.id
                else -> false
            }

            override fun areContentsTheSame(a: Row, b: Row) = a == b
        }
    }
}
