package com.dearly.app.ui.library

import android.content.res.ColorStateList
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.DialogTagsBinding
import com.dearly.app.model.FolderSort
import com.dearly.app.model.NoteSort
import com.dearly.app.model.NoteSummary
import com.dearly.app.model.Tag
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.dp
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

            // removeTag -> rebuild -> addChip -> removeTag is circular, so rebuild is assigned further down.
            var rebuild: () -> Unit = {}

            // Deleting a tag removes it from the list and from every note and folder using it.
            fun removeTag(tag: Tag) {
                activity.background(work = { ServiceLocator.tags.usageCounts()[tag.id] ?: 0 }) { uses ->
                    val delete = {
                        activity.background(work = { ServiceLocator.tags.delete(tag.id) }) {
                            tags.removeAll { it.id == tag.id }
                            chosen.remove(tag.id)
                            rebuild()
                        }
                    }
                    if (uses == 0) {
                        delete()
                    } else {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.tag_delete_title)
                            .setMessage(activity.getString(R.string.tag_delete_message, tag.name))
                            .setPositiveButton(R.string.delete) { _, _ -> delete() }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
            }

            fun addChip(tag: Tag) {
                val chip = Chip(chipContext)
                chip.text = "#" + tag.name
                chip.isCheckable = true
                chip.isChecked = tag.id in chosen
                chip.setEnsureMinTouchTargetSize(false)
                chip.setOnCheckedChangeListener { _, checked -> if (checked) chosen.add(tag.id) else chosen.remove(tag.id) }

                // The X: a small round badge centred on the chip's top-right corner.
                val badge = activity.dp(BADGE_DP)
                val remove = ImageView(activity)
                remove.setImageResource(R.drawable.ic_close)
                remove.imageTintList = ColorStateList.valueOf(activity.getColor(R.color.dearly_on_primary))
                remove.setBackgroundResource(R.drawable.bg_tag_remove)
                remove.setPadding(activity.dp(5), activity.dp(5), activity.dp(5), activity.dp(5))
                remove.contentDescription = activity.getString(R.string.tag_remove, tag.name)
                remove.setOnClickListener { removeTag(tag) }

                val overhang = badge / 2
                val frame = FrameLayout(activity)
                frame.addView(
                    chip,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = overhang
                        marginEnd = overhang
                    }
                )
                frame.addView(remove, FrameLayout.LayoutParams(badge, badge, Gravity.TOP or Gravity.END))
                b.chipGroup.addView(frame)
            }

            rebuild = {
                b.chipGroup.removeAllViews()
                b.tvNoTags.show(tags.isEmpty())
                for (tag in tags) addChip(tag)
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

    private const val BADGE_DP = 20
}
