package com.dearly.app.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.dearly.app.R
import com.dearly.app.ui.common.dp

/**
 * Four-dot PIN entry with a large numeric keypad (4 rows x 3 columns: 1-9, then blank / 0 / delete).
 * After the fourth digit [onPinComplete] fires once and input stays locked until the owner calls
 * [reset], so a double tap can never submit twice.
 */
class PinPadView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    var onPinComplete: ((String) -> Unit)? = null
    private val pin = StringBuilder()
    private val dots = ArrayList<View>()
    private val dotRow = LinearLayout(context)
    private var locked = false
    private val cells = ArrayList<View>()
    private var keyWidthPx = 0

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        // --- the four PIN dots
        dotRow.orientation = HORIZONTAL
        dotRow.gravity = Gravity.CENTER
        val dotSize = resources.getDimensionPixelSize(R.dimen.pin_dot_size)
        val dotSpacing = resources.getDimensionPixelSize(R.dimen.pin_dot_spacing)
        repeat(LENGTH) {
            val dot = View(context)
            dot.setBackgroundResource(R.drawable.bg_pin_dot)
            val lp = LayoutParams(dotSize, dotSize)
            lp.setMargins(dotSpacing, 0, dotSpacing, 0)
            dotRow.addView(dot, lp)
            dots.add(dot)
        }
        dotRow.contentDescription = context.getString(R.string.pin_entry_description)
        addView(dotRow, LayoutParams(LayoutParams.WRAP_CONTENT, resources.getDimensionPixelSize(R.dimen.pin_dot_row_height)))

        // --- the keypad: plain numbers on a fixed grid. Every cell has the same size (set per screen
        // size in dimens), so rows and columns stay evenly spaced and nothing can be clipped.
        val keyWidth = resources.getDimensionPixelSize(R.dimen.pin_key_width)
        keyWidthPx = keyWidth
        val keyHeight = resources.getDimensionPixelSize(R.dimen.pin_key_height)
        val keyTextPx = resources.getDimension(R.dimen.pin_key_text)
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )
        for (row in rows) {
            val line = LinearLayout(context)
            line.orientation = HORIZONTAL
            line.gravity = Gravity.CENTER
            for (key in row) {
                val cell = TextView(context, null, 0, R.style.Widget_Dearly_PinKey)
                cell.setTextSize(TypedValue.COMPLEX_UNIT_PX, keyTextPx)
                when (key) {
                    "" -> cell.visibility = INVISIBLE
                    "DEL" -> {
                        cell.text = "⌫"
                        cell.contentDescription = context.getString(R.string.pin_delete)
                        cell.setOnClickListener { backspace() }
                    }
                    else -> {
                        cell.text = key
                        cell.setOnClickListener { append(key) }
                    }
                }
                line.addView(cell, LayoutParams(keyWidth, keyHeight))
                cells.add(cell)
            }
            addView(line, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }

    /**
     * The pad is always exactly three keys wide, whatever width the parent offers. A parent that hands
     * over a narrower AT_MOST width (as happened when the pad was clipped to the width of the PIN dots)
     * can therefore no longer cut off the third column.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        for (cell in cells) {
            val lp = cell.layoutParams
            if (lp.width != keyWidthPx) {
                lp.width = keyWidthPx
                cell.layoutParams = lp
            }
        }
        val exactWidth = MeasureSpec.makeMeasureSpec(keyWidthPx * COLUMNS, MeasureSpec.EXACTLY)
        super.onMeasure(exactWidth, heightMeasureSpec)
    }

    private fun append(digit: String) {
        if (locked || pin.length >= LENGTH) return
        pin.append(digit)
        render()
        if (pin.length == LENGTH) {
            locked = true
            val entered = pin.toString()
            post { onPinComplete?.invoke(entered) }
        }
    }

    private fun backspace() {
        if (locked || pin.isEmpty()) return
        pin.deleteCharAt(pin.length - 1)
        render()
    }

    private fun render() {
        dots.forEachIndexed { i, d -> d.isActivated = i < pin.length }
    }

    /** Clears the entry and re-enables input. */
    fun reset() {
        pin.setLength(0)
        locked = false
        render()
    }

    /** Clears the entry, re-enables input and gives haptic + visual feedback. */
    fun showError() {
        performHapticFeedback(HapticFeedbackConstants.REJECT)
        dotRow.animate().translationX(px(10)).setDuration(60).withEndAction {
            dotRow.animate().translationX(-px(10)).setDuration(60).withEndAction {
                dotRow.animate().translationX(0f).setDuration(60).start()
            }.start()
        }.start()
        reset()
    }

    /** Disables input (e.g. during a lockout) without clearing state. */
    fun setInputLocked(value: Boolean) {
        locked = value
        alpha = if (value) 0.5f else 1f
    }

    private fun px(v: Int): Float = context.dp(v).toFloat()

    companion object {
        const val LENGTH = 4
        private const val COLUMNS = 3
    }
}