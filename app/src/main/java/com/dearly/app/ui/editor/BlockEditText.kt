package com.dearly.app.ui.editor

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/**
 * One paragraph/list item. Reports selection changes and Backspace at the very start of the field
 * (needed to merge paragraphs or leave a list), for both soft and hardware keyboards.
 */
class BlockEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var onBackspaceAtStart: (() -> Unit)? = null
    var onSelectionChange: ((Int, Int) -> Unit)? = null

    private fun caretAtStart() = selectionStart == 0 && selectionEnd == 0

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChange?.invoke(selStart, selEnd)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && caretAtStart() && onBackspaceAtStart != null) {
            onBackspaceAtStart?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL &&
                    caretAtStart() && onBackspaceAtStart != null
                ) {
                    onBackspaceAtStart?.invoke()
                    return true
                }
                return super.sendKeyEvent(event)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0 && caretAtStart() && onBackspaceAtStart != null) {
                    onBackspaceAtStart?.invoke()
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
    }
}
