package com.dearly.app.ui.library

import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.model.Folder
import com.dearly.app.model.NoteSummary
import com.dearly.app.ui.BaseActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Long-press menus and delete confirmations shared by Home, Library, Folder and Search. */
object ItemActions {

    fun noteMenu(activity: BaseActivity, note: NoteSummary, onChanged: () -> Unit) {
        val items = arrayOf(
            activity.getString(if (note.pinned) R.string.unpin else R.string.pin),
            activity.getString(R.string.move_to_folder),
            activity.getString(R.string.edit_tags),
            activity.getString(R.string.delete)
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(note.title.ifBlank { activity.getString(R.string.untitled) })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> activity.background(work = { ServiceLocator.notes.setPinned(note.id, !note.pinned) }) { onChanged() }
                    1 -> Pickers.chooseFolder(activity, note.folderId) { folderId ->
                        activity.background(work = { ServiceLocator.notes.moveToFolder(note.id, folderId) }) { onChanged() }
                    }
                    2 -> activity.background(work = { ServiceLocator.notes.tagIds(note.id) }) { current ->
                        Pickers.chooseTags(activity, current.toSet()) { chosen ->
                            activity.background(work = { ServiceLocator.notes.setTags(note.id, chosen.toList()) }) { onChanged() }
                        }
                    }
                    3 -> confirmDeleteNote(activity, note, onChanged)
                }
            }
            .show()
    }

    fun confirmDeleteNote(activity: BaseActivity, note: NoteSummary, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_note_title)
            .setMessage(R.string.delete_note_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                activity.background(work = { ServiceLocator.trash.deleteNote(note.id) }) {
                    activity.toast(R.string.moved_to_recently_deleted)
                    onDeleted()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun folderMenu(activity: BaseActivity, folder: Folder, onChanged: () -> Unit) {
        val items = arrayOf(
            activity.getString(R.string.edit_folder),
            activity.getString(if (folder.pinned) R.string.unpin else R.string.pin),
            activity.getString(R.string.delete)
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(folder.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> FolderEditorDialog.show(activity, folder) { onChanged() }
                    1 -> activity.background(work = { ServiceLocator.folders.setPinned(folder.id, !folder.pinned) }) { onChanged() }
                    2 -> confirmDeleteFolder(activity, folder, onChanged)
                }
            }
            .show()
    }

    fun confirmDeleteFolder(activity: BaseActivity, folder: Folder, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_folder_title)
            .setMessage(activity.resources.getQuantityString(R.plurals.delete_folder_message, folder.noteCount, folder.noteCount))
            .setPositiveButton(R.string.delete) { _, _ ->
                activity.background(work = { ServiceLocator.trash.deleteFolder(folder.id) }) {
                    activity.toast(R.string.moved_to_recently_deleted)
                    onDeleted()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
