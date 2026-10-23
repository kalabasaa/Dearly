package com.dearly.app.ui.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ActivityFolderBinding
import com.dearly.app.model.Folder
import com.dearly.app.model.NoteSort
import com.dearly.app.model.NoteSummary
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.TagChips
import com.dearly.app.ui.common.show
import com.dearly.app.ui.editor.EditorActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The notes inside one folder, with folder management actions. */
class FolderActivity : BaseActivity() {
    private lateinit var b: ActivityFolderBinding
    private lateinit var adapter: NoteAdapter
    private var folderId = 0L
    private var folder: Folder? = null
    private var sort = NoteSort.UPDATED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFolderBinding.inflate(layoutInflater)
        setContentView(b.root)
        folderId = intent.getLongExtra(EXTRA_FOLDER_ID, 0L)

        adapter = NoteAdapter(
            onClick = { startActivity(EditorActivity.newIntent(this, it.id)) },
            onLongClick = { ItemActions.noteMenu(this, it) { refresh() } }
        )
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.menu_folder)
        b.toolbar.setOnMenuItemClickListener { item ->
            val f = folder
            when (item.itemId) {
                R.id.action_edit -> if (f != null) FolderEditorDialog.show(this, f) { refresh() }
                R.id.action_pin -> if (f != null) background(work = { ServiceLocator.folders.setPinned(f.id, !f.pinned) }) { refresh() }
                R.id.action_sort -> chooseSort()
                R.id.action_delete -> if (f != null) ItemActions.confirmDeleteFolder(this, f) { finish() }
            }
            true
        }
        b.fabAdd.setOnClickListener { startActivity(EditorActivity.newIntent(this, 0, folderId)) }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (!isUnlocked) return
        val currentSort = sort
        background(work = {
            ServiceLocator.folders.get(folderId) to ServiceLocator.notes.list(currentSort, folderId = folderId)
        }) { (loaded, notes) -> render(loaded, notes) }
    }

    private fun render(loaded: Folder?, notes: List<NoteSummary>) {
        if (loaded == null) { // deleted or restored elsewhere
            finish()
            return
        }
        folder = loaded
        b.toolbar.title = loaded.name
        b.toolbar.menu.findItem(R.id.action_pin)?.setTitle(if (loaded.pinned) R.string.unpin else R.string.pin)
        b.tvDesc.text = loaded.description
        b.tvDesc.show(loaded.description.isNotBlank())
        TagChips.fill(b.tagRow, loaded.tags.map { it.name }, 6)
        adapter.submitList(notes)
        b.tvEmpty.show(notes.isEmpty())
    }

    private fun chooseSort() {
        val labels = arrayOf(R.string.sort_updated, R.string.sort_created, R.string.sort_title).map { getString(it) }.toTypedArray()
        val values = arrayOf(NoteSort.UPDATED, NoteSort.CREATED, NoteSort.TITLE)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort)
            .setSingleChoiceItems(labels, values.indexOf(sort)) { dialog, which ->
                dialog.dismiss()
                sort = values[which]
                refresh()
            }
            .show()
    }

    companion object {
        private const val EXTRA_FOLDER_ID = "folder_id"

        fun newIntent(context: Context, folderId: Long): Intent =
            Intent(context, FolderActivity::class.java).putExtra(EXTRA_FOLDER_ID, folderId)
    }
}
