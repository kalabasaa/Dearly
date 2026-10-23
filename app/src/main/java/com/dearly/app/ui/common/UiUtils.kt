package com.dearly.app.ui.common

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.dearly.app.R
import kotlin.math.ceil
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun lockedMessage(context: Context, remainingMs: Long): String {
    val seconds = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(1)
    return if (seconds < 60) context.getString(R.string.try_again_seconds, seconds)
    else context.getString(R.string.try_again_minutes, ceil(seconds / 60.0).toInt())
}

object TagChips {
    /** Fills [parent] with small pastel chips (max [max], then "+N"). */
    fun fill(parent: LinearLayout, tags: List<String>, max: Int = 3) {
        parent.removeAllViews()
        parent.show(tags.isNotEmpty())
        val ctx = parent.context
        tags.take(max).forEach { parent.addView(chip(ctx, "#$it")) }
        if (tags.size > max) parent.addView(chip(ctx, "+${tags.size - max}"))
    }

    fun chip(context: Context, text: String): TextView {
        val tv = TextView(context, null, 0, R.style.Widget_Dearly_TagChip)
        tv.text = text
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = context.dp(6)
        tv.layoutParams = lp
        return tv
    }
}

object FolderColors {
    val RES = intArrayOf(
        R.color.folder_0, R.color.folder_1, R.color.folder_2, R.color.folder_3,
        R.color.folder_4, R.color.folder_5, R.color.folder_6, R.color.folder_7
    )

    fun color(context: Context, index: Int): Int = context.getColor(RES[index.coerceIn(0, RES.size - 1)])
}
