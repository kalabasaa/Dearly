package com.dearly.app.ui

import android.app.Activity
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.security.AuthResult
import com.dearly.app.ui.common.dp
import com.dearly.app.ui.common.lockedMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** A dialog containing a [PinPadView]. The owner decides what a completed PIN means. */
class PinPrompt(
    private val activity: Activity,
    title: String,
    subtitle: String? = null,
    private val onPin: (String, PinPrompt) -> Unit,
    private val onCancel: () -> Unit = {}
) {
    private val pad = PinPadView(activity)
    private val message = TextView(activity)
    private val dialog: AlertDialog

    init {
        val content = LinearLayout(activity)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        content.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8))
        message.text = subtitle.orEmpty()
        message.gravity = Gravity.CENTER
        message.minHeight = activity.dp(28)
        message.setTextColor(activity.getColor(R.color.dearly_text_secondary))
        content.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(pad)
        pad.onPinComplete = { onPin(it, this) }

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setOnCancelListener { onCancel() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss()
                onCancel()
            }
        }
        dialog.setCanceledOnTouchOutside(false)
    }

    fun show(): PinPrompt {
        dialog.show()
        return this
    }

    fun error(text: String) {
        message.text = text
        pad.showError()
    }

    fun clear() = pad.reset()

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }
}

/** Re-checks the user's PIN before a sensitive action. Hashing runs off the main thread. */
object PinGate {
    fun request(
        activity: BaseActivity,
        title: String = activity.getString(R.string.enter_pin),
        subtitle: String? = null,
        onCancel: () -> Unit = {},
        onSuccess: () -> Unit
    ) {
        PinPrompt(activity, title, subtitle, onPin = { pin, prompt ->
            activity.background(
                onError = { prompt.error(activity.getString(R.string.error_generic)) },
                work = { ServiceLocator.auth.verifyPin(pin) }
            ) { result ->
                when (result) {
                    AuthResult.Success -> {
                        prompt.dismiss()
                        onSuccess()
                    }
                    is AuthResult.Wrong -> prompt.error(activity.getString(R.string.pin_wrong_attempts, result.attemptsLeftBeforeLock))
                    is AuthResult.LockedOut -> prompt.error(lockedMessage(activity, result.remainingMs))
                    AuthResult.NotConfigured -> prompt.dismiss()
                }
            }
        }, onCancel = onCancel).show()
    }
}
