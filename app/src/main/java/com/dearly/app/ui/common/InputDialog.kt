package com.dearly.app.ui.common

import android.text.InputFilter
import androidx.appcompat.app.AlertDialog
import com.dearly.app.R
import com.dearly.app.databinding.DialogInputBinding
import com.dearly.app.ui.BaseActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object InputDialog {
    /**
     * Single-line text prompt. [onOk] returns an error message to keep the dialog open, or null to accept.
     */
    fun show(
        activity: BaseActivity,
        title: String,
        initial: String,
        hint: String,
        maxLength: Int,
        onOk: (String) -> String?
    ) {
        val b = DialogInputBinding.inflate(activity.layoutInflater)
        b.tilInput.hint = hint
        b.etInput.filters = arrayOf(InputFilter.LengthFilter(maxLength))
        b.etInput.setText(initial)
        b.etInput.setSelection(initial.length)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(b.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = onOk(b.etInput.text.toString())
                if (error == null) dialog.dismiss() else b.tilInput.error = error
            }
        }
        dialog.show()
    }
}
