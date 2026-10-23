package com.dearly.app.ui.library

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.DialogFolderEditBinding
import com.dearly.app.model.Folder
import com.dearly.app.model.Tag
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.FolderColors
import com.dearly.app.ui.common.dp
import com.dearly.app.utils.TextUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Create or edit a folder: name, optional description, pastel colour and tags. */
object FolderEditorDialog {

    fun show(activity: BaseActivity, existing: Folder?, onSaved: (Long) -> Unit) {
        val b = DialogFolderEditBinding.inflate(activity.layoutInflater)
        var color = existing?.color ?: 0
        val tagIds = existing?.tags?.map { it.id }?.toMutableSet() ?: mutableSetOf()
        var tagNames: List<String> = existing?.tags?.map { it.name } ?: emptyList()

        b.etFolderName.setText(existing?.name.orEmpty())
        b.etFolderDesc.setText(existing?.description.orEmpty())

        fun renderTags() {
            b.tvFolderTags.text = if (tagNames.isEmpty()) activity.getString(R.string.no_tags) else tagNames.joinToString("  ") { "#$it" }
        }
        renderTags()

        val swatches = ArrayList<View>()
        fun renderColors() {
            swatches.forEachIndexed { i, v ->
                val d = GradientDrawable()
                d.shape = GradientDrawable.OVAL
                d.setColor(FolderColors.color(activity, i))
                d.setStroke(activity.dp(if (i == color) 3 else 1), activity.getColor(if (i == color) R.color.dearly_primary else R.color.dearly_outline))
                v.background = d
                v.isSelected = i == color
            }
        }
        for (i in FolderColors.RES.indices) {
            val v = View(activity)
            val lp = LinearLayout.LayoutParams(activity.dp(40), activity.dp(40))
            lp.marginEnd = activity.dp(10)
            v.layoutParams = lp
            v.contentDescription = activity.getString(R.string.folder_color_n, i + 1)
            v.isClickable = true
            v.setOnClickListener { color = i; renderColors() }
            swatches.add(v)
            b.colorRow.addView(v)
        }
        renderColors()

        b.btnFolderTags.setOnClickListener {
            Pickers.chooseTags(activity, tagIds) { chosen ->
                tagIds.clear()
                tagIds.addAll(chosen)
                activity.background(work = { ServiceLocator.tags.all() }) { all: List<Tag> ->
                    tagNames = all.filter { it.id in tagIds }.map { it.name }
                    renderTags()
                }
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) R.string.new_folder else R.string.edit_folder)
            .setView(b.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val name = TextUtils.cleanName(b.etFolderName.text.toString(), 40)
                if (name.isEmpty()) {
                    b.tilFolderName.error = activity.getString(R.string.error_folder_name_required)
                    return@setOnClickListener
                }
                positive.isEnabled = false // prevents a double tap creating two folders
                val desc = b.etFolderDesc.text.toString()
                val ids = tagIds.toList()
                activity.background(
                    onError = {
                        positive.isEnabled = true
                        activity.toast(R.string.error_generic)
                    },
                    work = {
                        if (existing == null) ServiceLocator.folders.create(name, desc, color, ids)
                        else {
                            ServiceLocator.folders.update(existing.id, name, desc, color, ids)
                            existing.id
                        }
                    }
                ) { id ->
                    dialog.dismiss()
                    onSaved(id)
                }
            }
        }
        dialog.show()
    }
}
