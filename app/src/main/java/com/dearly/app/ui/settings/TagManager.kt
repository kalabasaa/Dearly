package com.dearly.app.ui.settings

import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.model.RenameResult
import com.dearly.app.model.Tag
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.InputDialog
import com.dearly.app.utils.TextUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** List, rename and delete tags. Deleting a tag only removes it from notes/folders; they are untouched. */
object TagManager {

    fun show(activity: BaseActivity) {
        activity.background(work = { ServiceLocator.tags.all() to ServiceLocator.tags.usageCounts() }) { (tags, counts) ->
            if (tags.isEmpty()) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.settings_manage_tags)
                    .setMessage(R.string.tags_none_yet)
                    .setPositiveButton(R.string.done, null)
                    .show()
                return@background
            }
            val labels = tags.map { "#${it.name}  (${counts[it.id] ?: 0})" }.toTypedArray()
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.settings_manage_tags)
                .setItems(labels) { _, which -> options(activity, tags[which]) }
                .setPositiveButton(R.string.done, null)
                .show()
        }
    }

    private fun options(activity: BaseActivity, tag: Tag) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("#" + tag.name)
            .setItems(arrayOf(activity.getString(R.string.rename), activity.getString(R.string.delete))) { _, which ->
                if (which == 0) rename(activity, tag) else confirmDelete(activity, tag)
            }
            .show()
    }

    private fun rename(activity: BaseActivity, tag: Tag) {
        InputDialog.show(activity, activity.getString(R.string.rename), tag.name, activity.getString(R.string.hint_new_tag), 30) { text ->
            if (TextUtils.cleanName(text.trimStart('#'), 30).isEmpty()) return@show activity.getString(R.string.error_tag_invalid)
            // Renames run on the database thread; the result decides whether to reopen the list or show an error.
            activity.background(work = { ServiceLocator.tags.rename(tag.id, text) }) { result ->
                when (result) {
                    RenameResult.OK -> show(activity)
                    RenameResult.DUPLICATE -> activity.toast(R.string.error_tag_duplicate)
                    RenameResult.INVALID -> activity.toast(R.string.error_tag_invalid)
                }
            }
            null
        }
    }

    private fun confirmDelete(activity: BaseActivity, tag: Tag) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.tag_delete_title)
            .setMessage(activity.getString(R.string.tag_delete_message, tag.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                activity.background(work = { ServiceLocator.tags.delete(tag.id) }) { show(activity) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
