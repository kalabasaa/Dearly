package com.dearly.app.ui.library

import android.view.ContextThemeWrapper
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.DialogTagsBinding
import com.dearly.app.model.FolderSort
import com.dearly.app.model.NoteSort
import com.dearly.app.model.NoteSummary
import com.dearly.app.model.Tag
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.show
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Small reusable selection dialogs. */
object Pickers {

    fun chooseFolder(activity: BaseActivity, currentFolderId: Long?, onPicked: (Long?) -> Unit) {
        activity.background(work = { ServiceLocator.folders.list(FolderSort.NAME) }) { folders ->
            val names = (listOf(activity.getString(R.string.no_folder)) + folders.map { it.name }).toTypedArray()
            val checked = if (currentFolderId == null) 0 else folders.indexOfFirst { it.id == currentFolderId } + 1
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.move_to_folder)
                .setSingleChoiceItems(names, checked) { dialog, which ->
                    dialog.dismiss()
                    onPicked(if (which == 0) null else folders[which - 1].id)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    fun chooseNote(activity: BaseActivity, onPicked: (NoteSummary?) -> Unit) {
        activity.background(work = { ServiceLocator.notes.list(NoteSort.UPDATED, pinnedFirst = false, limit = 60) }) { notes ->
            val names = (listOf(activity.getString(R.string.no_linked_note)) +
                notes.map { it.title.ifBlank { activity.getString(R.string.untitled) } }).toTypedArray()
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.link_note)
                .setItems(names) { _, which -> onPicked(if (which == 0) null else notes[which - 1]) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /** Multi-select tag chips with an inline "new tag" field. Returns the chosen tag ids via [onDone]. */
    fun chooseTags(activity: BaseActivity, selected: Set<Long>, onDone: (Set<Long>) -> Unit) {
        activity.background(work = { ServiceLocator.tags.all() }) { loaded ->
            val tags = loaded.toMutableList()
            val chosen = selected.toMutableSet()
            val b = DialogTagsBinding.inflate(activity.layoutInflater)
            // Chips pick up the Dearly chip style (flat, rounded, no icons) through this theme overlay.
            val chipContext = ContextThemeWrapper(activity, R.style.ThemeOverlay_Dearly_Chip)

            fun rebuild() {
                b.chipGroup.removeAllViews()
                b.tvNoTags.show(tags.isEmpty())
                for (tag in tags) {
                    val chip = Chip(chipContext)
                    chip.text = "#" + tag.name
                    chip.isCheckable = true
                    chip.isChecked = tag.id in chosen
                    chip.setOnCheckedChangeListener { _, checked -> if (checked) chosen.add(tag.id) else chosen.remove(tag.id) }
                    b.chipGroup.addView(chip)
                }
            }

            fun addTag() {
                val name = b.etNewTag.text.toString()
                if (name.isBlank()) return
                activity.background(work = { ServiceLocator.tags.findOrCreate(name) }) { tag: Tag? ->
                    if (tag == null) {
                        b.tilNewTag.error = activity.getString(R.string.error_tag_invalid)
                    } else {
                        b.tilNewTag.error = null
                        if (tags.none { it.id == tag.id }) tags.add(tag)
                        tags.sortBy { it.name.lowercase() }
                        chosen.add(tag.id)
                        b.etNewTag.text?.clear()
                        rebuild()
                    }
                }
            }
            b.btnAddTag.setOnClickListener { addTag() }
            b.etNewTag.setOnEditorActionListener { _, _, _ -> addTag(); true }
            rebuild()

            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tags)
                .setView(b.root)
                .setPositiveButton(R.string.done) { _, _ -> onDone(chosen) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
