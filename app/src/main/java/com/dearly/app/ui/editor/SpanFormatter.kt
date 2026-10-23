package com.dearly.app.ui.editor

import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.dearly.app.model.richtext.SpanData
import com.dearly.app.model.richtext.SpanKind
import com.dearly.app.model.richtext.SpanOps
import com.dearly.app.model.richtext.TextSizes

/**
 * Bridges Android spans and the pure [SpanData] model. Only Dearly's own marker span classes are read
 * or removed, so keyboard/IME spans (composing text, suggestions) can never leak into saved notes.
 */
object SpanFormatter {
    interface Own
    class BoldSpan : StyleSpan(Typeface.BOLD), Own
    class ItalicSpan : StyleSpan(Typeface.ITALIC), Own
    class UnderSpan : UnderlineSpan(), Own
    class SizeSpan(val level: Int) : RelativeSizeSpan(TextSizes.factor(level)), Own
    class HighlightSpan(color: Int) : BackgroundColorSpan(color), Own

    fun create(data: SpanData, highlightColor: Int): Any = when (data.kind) {
        SpanKind.BOLD -> BoldSpan()
        SpanKind.ITALIC -> ItalicSpan()
        SpanKind.UNDERLINE -> UnderSpan()
        SpanKind.SIZE -> SizeSpan(data.value)
        SpanKind.HIGHLIGHT -> HighlightSpan(highlightColor)
    }

    fun read(text: Spanned): List<SpanData> {
        val out = ArrayList<SpanData>()
        for (span in text.getSpans(0, text.length, Own::class.java)) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end <= start) continue
            when (span) {
                is BoldSpan -> out.add(SpanData(SpanKind.BOLD, start, end))
                is ItalicSpan -> out.add(SpanData(SpanKind.ITALIC, start, end))
                is UnderSpan -> out.add(SpanData(SpanKind.UNDERLINE, start, end))
                is SizeSpan -> out.add(SpanData(SpanKind.SIZE, start, end, span.level))
                is HighlightSpan -> out.add(SpanData(SpanKind.HIGHLIGHT, start, end))
            }
        }
        return SpanOps.normalize(out, text.length)
    }

    fun clearOwn(text: Editable) {
        for (span in text.getSpans(0, text.length, Own::class.java)) text.removeSpan(span)
    }

    /**
     * Replaces all Dearly spans with [spans]. Spans extend when typing at their end, except a span ending
     * exactly at [noExtendAt] (used when the user switches a style off at the cursor).
     */
    fun write(text: Editable, spans: List<SpanData>, highlightColor: Int, noExtendAt: Int? = null) {
        clearOwn(text)
        for (s in SpanOps.normalize(spans, text.length)) {
            val flag = if (noExtendAt != null && s.end == noExtendAt) Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            else Spanned.SPAN_EXCLUSIVE_INCLUSIVE
            text.setSpan(create(s, highlightColor), s.start, s.end, flag)
        }
    }

    /** Adds a zero-length span at [pos] so the next typed characters get the style. */
    fun addPending(text: Editable, data: SpanData, highlightColor: Int) {
        text.setSpan(create(data, highlightColor), data.start, data.start, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }

    /** Styles that newly typed text at [pos] would receive (including pending zero-length spans). */
    fun typingState(text: Spanned, pos: Int): Pair<Set<SpanKind>, Int> {
        val kinds = HashSet<SpanKind>()
        var size = TextSizes.DEFAULT_LEVEL
        for (span in text.getSpans(0, text.length, Own::class.java)) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            val flags = text.getSpanFlags(span) and Spanned.SPAN_POINT_MARK_MASK
            val endInclusive = flags == Spanned.SPAN_INCLUSIVE_INCLUSIVE || flags == Spanned.SPAN_EXCLUSIVE_INCLUSIVE
            val active = (start < pos && end > pos) || (end == pos && start < end && endInclusive) ||
                (start == pos && end == pos)
            if (!active) continue
            when (span) {
                is BoldSpan -> kinds.add(SpanKind.BOLD)
                is ItalicSpan -> kinds.add(SpanKind.ITALIC)
                is UnderSpan -> kinds.add(SpanKind.UNDERLINE)
                is HighlightSpan -> kinds.add(SpanKind.HIGHLIGHT)
                is SizeSpan -> { kinds.add(SpanKind.SIZE); size = span.level }
            }
        }
        return kinds to size
    }
}
