package com.dearly.app.ui.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ActivityEditorBinding
import com.dearly.app.model.NoteDraft
import com.dearly.app.model.NoteFull
import com.dearly.app.model.richtext.Document
import com.dearly.app.model.richtext.ListType
import com.dearly.app.model.richtext.NoteContentSerializer
import com.dearly.app.model.richtext.SpanKind
import com.dearly.app.model.richtext.TextSizes
import com.dearly.app.model.richtext.UndoHistory
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.TagChips
import com.dearly.app.ui.library.Pickers
import com.dearly.app.ui.reminders.ReminderDialog
import com.dearly.app.ui.stickers.StickerPickerActivity
import com.dearly.app.utils.Formatters
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistent rich-text editor.
 *  - Undo/redo: snapshot history of (title, document); auto-save never touches it.
 *  - Auto-save: debounced; flushed on pause. All saves run in order on the single database thread and
 *    the note id is resolved there, so a slow first insert can never be followed by a duplicate insert.
 *  - Nothing is saved until the note has loaded, so an unfinished load can never overwrite a note with empty content.
 */
class EditorActivity : BaseActivity(), BlockEditor.Callbacks {

    private data class EditorState(val title: String, val doc: Document)
    private enum class Status { SAVED, DIRTY, SAVING, FAILED }

    private lateinit var b: ActivityEditorBinding
    private lateinit var editor: BlockEditor
    private val history = UndoHistory<EditorState>()
    private val handler = Handler(Looper.getMainLooper())

    private val noteId = AtomicLong(0)
    private var folderId: Long? = null
    private var folderName: String? = null
    private var tagIds: Set<Long> = emptySet()
    private var tagNames: List<String> = emptyList()
    private var pinned = false
    private var createdAt = 0L
    private var updatedAt = 0L

    private var loaded = false
    private var deleted = false
    private var suppressTitle = false
    private var snapshotPending = false
    private var lastQueuedFingerprint: String? = null
    private var failureShown = false
    private var retryCount = 0

    private val snapshotTask = Runnable { snapshotPending = false; commitSnapshot() }
    private val saveTask = Runnable { saveNow() }

    private val stickerPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getLongExtra(StickerPickerActivity.EXTRA_STICKER_ID, 0L) ?: 0L
        if (id > 0L) {
            background(work = { ServiceLocator.stickers.all().associate { it.id to it.fileName } }) { files ->
                editor.stickerFiles = files
                editor.insertSticker(id)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        editor = BlockEditor(this, b.blocks, this)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.menu_editor)
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_undo -> undo()
                R.id.action_redo -> redo()
                R.id.action_pin -> togglePin()
                R.id.action_reminder -> addReminder()
                R.id.action_delete -> confirmDelete()
            }
            true
        }
        setupTitle()
        setupFormatBar()
        b.btnFolder.setOnClickListener { chooseFolder() }
        b.btnTags.setOnClickListener { chooseTags() }
        b.tailSpace.setOnClickListener { editor.focusEnd() }

        val id = savedInstanceState?.getLong(KEY_NOTE_ID) ?: intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        noteId.set(id)
        val presetFolder = intent.getLongExtra(EXTRA_FOLDER_ID, 0L).takeIf { it > 0 }
        load(id, presetFolder)
        updateUndoRedo()
        setStatus(Status.SAVED)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_NOTE_ID, noteId.get())
    }

    override fun onPause() {
        super.onPause()
        flushAll()
    }

    override fun onDestroy() {
        handler.removeCallbacks(snapshotTask)
        handler.removeCallbacks(saveTask)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ loading

    private class Loaded(val note: NoteFull?, val stickers: Map<Long, String>, val folderName: String?, val tagNames: List<String>)

    private fun load(id: Long, presetFolder: Long?) {
        background(
            onError = { toast(R.string.error_load_note); finish() },
            work = {
                val note = if (id > 0) ServiceLocator.notes.getFull(id) else null
                val fid = note?.folderId ?: presetFolder
                val tagSet = note?.tagIds?.toSet().orEmpty()
                Loaded(
                    note,
                    ServiceLocator.stickers.all().associate { it.id to it.fileName },
                    fid?.let { ServiceLocator.folders.get(it)?.name },
                    ServiceLocator.tags.all().filter { it.id in tagSet }.map { it.name }
                ).also { if (id > 0 && note == null) throw IllegalStateException("note $id missing") }
            }
        ) { data ->
            editor.stickerFiles = data.stickers
            val note = data.note
            val doc: Document
            if (note != null) {
                createdAt = note.createdAt
                updatedAt = note.updatedAt
                folderId = note.folderId
                tagIds = note.tagIds.toSet()
                pinned = note.pinned
                suppressTitle = true
                b.etTitle.setText(note.title)
                suppressTitle = false
                doc = NoteContentSerializer.fromJson(note.contentJson, note.bodyText)
            } else {
                folderId = presetFolder
                doc = Document.empty()
            }
            folderName = data.folderName
            tagNames = data.tagNames
            editor.render(doc)
            history.reset(EditorState(b.etTitle.text.toString(), editor.readDocument()))
            lastQueuedFingerprint = fingerprint(b.etTitle.text.toString(), editor.readDocument())
            loaded = true
            updateChips()
            updateMeta()
            updatePinIcon()
            updateUndoRedo()
            onFormatStateChanged()
            if (note == null) {
                b.etTitle.requestFocus()
                getSystemService(InputMethodManager::class.java).showSoftInput(b.etTitle, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    // ------------------------------------------------------------------ title + toolbar wiring

    private fun setupTitle() {
        b.etTitle.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (loaded && !suppressTitle) onContentChanged(false)
            }
        })
        b.etTitle.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) { editor.focus(0, 0); true } else false
        }
    }

    private fun setupFormatBar() {
        b.btnBold.setOnClickListener { editor.toggleStyle(SpanKind.BOLD) }
        b.btnItalic.setOnClickListener { editor.toggleStyle(SpanKind.ITALIC) }
        b.btnUnderline.setOnClickListener { editor.toggleStyle(SpanKind.UNDERLINE) }
        b.btnHighlight.setOnClickListener { editor.toggleStyle(SpanKind.HIGHLIGHT) }
        b.btnSize.setOnClickListener { chooseSize() }
        b.btnBullets.setOnClickListener { editor.toggleList(ListType.BULLET) }
        b.btnNumbers.setOnClickListener { editor.toggleList(ListType.NUMBERED) }
        b.btnCheck.setOnClickListener { editor.toggleList(ListType.CHECK) }
        b.btnTable.setOnClickListener { editor.insertTable() }
        b.btnSticker.setOnClickListener { stickerPicker.launch(StickerPickerActivity.pickIntent(this)) }
    }

    private fun chooseSize() {
        val labels = arrayOf(
            R.string.size_xs, R.string.size_s, R.string.size_m, R.string.size_l, R.string.size_xl
        ).map { getString(it) }.toTypedArray()
        val current = editor.formatState().sizeLevel
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.format_size)
            .setSingleChoiceItems(labels, if (current == null) -1 else current - 1) { dialog, which ->
                dialog.dismiss()
                editor.setSize(which + 1)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onFormatStateChanged() {
        if (!::b.isInitialized) return
        val st = editor.formatState()
        b.btnBold.isSelected = st.bold
        b.btnItalic.isSelected = st.italic
        b.btnUnderline.isSelected = st.underline
        b.btnHighlight.isSelected = st.highlight
        val level = st.sizeLevel
        b.btnSize.text = level?.toString() ?: "–"
        b.btnSize.isSelected = level != null && level != TextSizes.DEFAULT_LEVEL
    }

    // ------------------------------------------------------------------ change tracking, undo/redo

    override fun onContentChanged(immediate: Boolean) {
        if (!loaded || deleted) return
        com.dearly.app.security.Session.touch() // typing on the soft keyboard is activity too
        handler.removeCallbacks(snapshotTask)
        if (immediate) {
            snapshotPending = false
            commitSnapshot()
        } else {
            snapshotPending = true
            handler.postDelayed(snapshotTask, SNAPSHOT_DEBOUNCE_MS)
        }
        scheduleSave()
        setStatus(Status.DIRTY)
    }

    private fun commitSnapshot() {
        if (!loaded) return
        history.push(EditorState(b.etTitle.text.toString(), editor.readDocument()))
        updateUndoRedo()
    }

    private fun flushSnapshot() {
        if (snapshotPending) {
            handler.removeCallbacks(snapshotTask)
            snapshotPending = false
            commitSnapshot()
        }
    }

    private fun undo() {
        flushSnapshot()
        history.undo()?.let { applyState(it) }
    }

    private fun redo() {
        flushSnapshot()
        history.redo()?.let { applyState(it) }
    }

    private fun applyState(state: EditorState) {
        val titleFocused = b.etTitle.hasFocus()
        suppressTitle = true
        b.etTitle.setText(state.title)
        b.etTitle.setSelection(b.etTitle.text.length)
        suppressTitle = false
        val focus = if (titleFocused) -1 else editor.lastFocusedIndex.coerceIn(0, state.doc.blocks.lastIndex)
        editor.render(state.doc, focus, Int.MAX_VALUE)
        updateUndoRedo()
        scheduleSave()
        setStatus(Status.DIRTY)
    }

    private fun updateUndoRedo() {
        val undo = b.toolbar.menu.findItem(R.id.action_undo) ?: return
        val redo = b.toolbar.menu.findItem(R.id.action_redo) ?: return
        undo.isEnabled = history.canUndo
        redo.isEnabled = history.canRedo
        undo.icon?.mutate()?.alpha = if (history.canUndo) 255 else 90
        redo.icon?.mutate()?.alpha = if (history.canRedo) 255 else 90
    }

    // ------------------------------------------------------------------ saving

    private fun fingerprint(title: String, doc: Document): String =
        listOf(title, NoteContentSerializer.toJson(doc), folderId, tagIds.sorted(), pinned).joinToString("\u0001")

    private fun scheduleSave() {
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, SAVE_DEBOUNCE_MS)
    }

    private fun flushAll() {
        if (!loaded || deleted) return
        flushSnapshot()
        saveNow()
    }

    private fun saveNow() {
        if (!loaded || deleted) return
        handler.removeCallbacks(saveTask)
        val title = b.etTitle.text.toString()
        val doc = editor.readDocument()
        if (noteId.get() == 0L && title.isBlank() && !doc.hasContent()) return // never create an empty note
        val fp = fingerprint(title, doc)
        if (fp == lastQueuedFingerprint) return
        lastQueuedFingerprint = fp
        val draft = NoteDraft(0L, title, doc, folderId, tagIds.toList(), pinned)
        setStatus(Status.SAVING)
        ServiceLocator.io.execute {
            try {
                val result = ServiceLocator.notes.saveNote(draft.copy(id = noteId.get()))
                noteId.set(result.noteId)
                ServiceLocator.runOnMain {
                    createdAt = result.createdAt
                    updatedAt = result.updatedAt
                    retryCount = 0
                    failureShown = false
                    if (!isDestroyed) {
                        if (lastQueuedFingerprint == fp) setStatus(Status.SAVED)
                        updateMeta()
                    }
                }
            } catch (t: Throwable) {
                Log.e("EditorActivity", "Save failed", t)
                ServiceLocator.runOnMain {
                    if (lastQueuedFingerprint == fp) lastQueuedFingerprint = null // so a retry really writes
                    if (!isDestroyed) {
                        setStatus(Status.FAILED)
                        if (!failureShown) { failureShown = true; toast(R.string.error_save_note) }
                    }
                    if (retryCount++ < MAX_RETRIES) handler.postDelayed(saveTask, RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun setStatus(status: Status) {
        b.tvSaveStatus.setText(
            when (status) {
                Status.SAVED -> R.string.status_saved
                Status.DIRTY -> R.string.status_editing
                Status.SAVING -> R.string.status_saving
                Status.FAILED -> R.string.status_failed
            }
        )
    }

    // ------------------------------------------------------------------ metadata (folder, tags, pin, dates)

    private fun updateMeta() {
        b.tvDates.text = if (noteId.get() == 0L) getString(R.string.editor_new_note)
        else getString(R.string.editor_dates, Formatters.dateTime(this, createdAt), Formatters.dateTime(this, updatedAt))
    }

    private fun updateChips() {
        b.btnFolder.text = folderName ?: getString(R.string.no_folder)
        b.btnTags.setText(if (tagNames.isEmpty()) R.string.add_tags else R.string.edit_tags)
        TagChips.fill(b.llTags, tagNames, 20)
    }

    private fun updatePinIcon() {
        val item = b.toolbar.menu.findItem(R.id.action_pin) ?: return
        item.setIcon(if (pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
        item.setTitle(if (pinned) R.string.unpin else R.string.pin)
    }

    private fun togglePin() {
        if (!loaded) return
        pinned = !pinned
        updatePinIcon()
        scheduleSave()
        saveNow()
    }

    private fun chooseFolder() {
        if (!loaded) return
        Pickers.chooseFolder(this, folderId) { picked ->
            folderId = picked
            background(work = { picked?.let { ServiceLocator.folders.get(it)?.name } }) { name ->
                folderName = name
                updateChips()
            }
            onContentChanged(false)
        }
    }

    private fun chooseTags() {
        if (!loaded) return
        Pickers.chooseTags(this, tagIds) { chosen ->
            tagIds = chosen
            background(work = { ServiceLocator.tags.all().filter { it.id in chosen }.map { it.name } }) { names ->
                tagNames = names
                updateChips()
            }
            onContentChanged(false)
        }
    }

    // ------------------------------------------------------------------ reminder + delete

    private fun addReminder() {
        if (!loaded) return
        flushAll()
        val title = b.etTitle.text.toString()
        // Runs after the queued save on the same single thread, so the id is known if the note had content.
        background(work = { noteId.get() }) { id ->
            if (id == 0L) toast(R.string.reminder_write_first)
            else ReminderDialog.show(this, null, null, id, title.ifBlank { getString(R.string.untitled) }) {}
        }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_note_title)
            .setMessage(R.string.delete_note_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleted = true // stops any further auto-save
                handler.removeCallbacks(saveTask)
                handler.removeCallbacks(snapshotTask)
                background(
                    onError = { deleted = false; toast(R.string.error_generic) },
                    work = { noteId.get().let { if (it > 0) ServiceLocator.trash.deleteNote(it) } }
                ) {
                    toast(R.string.moved_to_recently_deleted)
                    finish()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_FOLDER_ID = "folder_id"
        private const val KEY_NOTE_ID = "state_note_id"
        private const val SNAPSHOT_DEBOUNCE_MS = 700L
        private const val SAVE_DEBOUNCE_MS = 1000L
        private const val RETRY_DELAY_MS = 3000L
        private const val MAX_RETRIES = 5

        fun newIntent(context: Context, noteId: Long, folderId: Long? = null): Intent =
            Intent(context, EditorActivity::class.java)
                .putExtra(EXTRA_NOTE_ID, noteId)
                .apply { if (folderId != null) putExtra(EXTRA_FOLDER_ID, folderId) }
    }
}
