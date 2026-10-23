package com.dearly.app.model.richtext

/** Pure, Android-free operations on formatting ranges. Heavily unit-tested. */
object SpanOps {

    /** Clips to [0, length], drops empty spans, and merges overlapping/adjacent spans of the same kind+value. */
    fun normalize(spans: List<SpanData>, length: Int): List<SpanData> {
        val clipped = spans.mapNotNull { s ->
            val a = s.start.coerceIn(0, length)
            val b = s.end.coerceIn(0, length)
            if (a < b) s.copy(start = a, end = b) else null
        }
        val result = ArrayList<SpanData>()
        for ((_, group) in clipped.groupBy { it.kind to it.value }) {
            val sorted = group.sortedBy { it.start }
            var cur = sorted.first()
            for (i in 1 until sorted.size) {
                val next = sorted[i]
                if (next.start <= cur.end) {
                    cur = cur.copy(end = maxOf(cur.end, next.end))
                } else {
                    result.add(cur)
                    cur = next
                }
            }
            result.add(cur)
        }
        return result.sortedWith(compareBy({ it.start }, { it.kind.ordinal }, { it.end }, { it.value }))
    }

    /** True if every character of [start, end) carries [kind] (optionally with a specific [value]). */
    fun isFullyCovered(spans: List<SpanData>, kind: SpanKind, start: Int, end: Int, value: Int? = null): Boolean {
        if (start >= end) return false
        var pos = start
        val relevant = spans
            .filter { it.kind == kind && (value == null || it.value == value) && it.end > start && it.start < end }
            .sortedBy { it.start }
        for (s in relevant) {
            if (s.start > pos) return false
            if (s.end > pos) pos = s.end
            if (pos >= end) return true
        }
        return pos >= end
    }

    /** Removes [kind] from [start, end), keeping the parts of partially overlapping spans outside it. */
    fun remove(spans: List<SpanData>, kind: SpanKind, start: Int, end: Int): List<SpanData> {
        if (start >= end) return spans
        val out = ArrayList<SpanData>()
        for (s in spans) {
            if (s.kind != kind || s.end <= start || s.start >= end) {
                out.add(s)
                continue
            }
            if (s.start < start) out.add(s.copy(end = start))
            if (s.end > end) out.add(s.copy(start = end))
        }
        return out
    }

    fun add(spans: List<SpanData>, kind: SpanKind, start: Int, end: Int, value: Int = 0, length: Int = Int.MAX_VALUE): List<SpanData> {
        if (start >= end) return spans
        val base = if (kind == SpanKind.SIZE || kind == SpanKind.HIGHLIGHT) remove(spans, kind, start, end) else spans
        return normalize(base + SpanData(kind, start, end, value), length)
    }

    /** Bold/italic/underline/highlight toggle: removes when the whole range is already styled, otherwise applies. */
    fun toggle(spans: List<SpanData>, kind: SpanKind, start: Int, end: Int, length: Int = Int.MAX_VALUE): List<SpanData> {
        if (start >= end) return spans
        return if (isFullyCovered(spans, kind, start, end)) {
            normalize(remove(spans, kind, start, end), length)
        } else {
            add(spans, kind, start, end, 0, length)
        }
    }

    /** Applies a size level. Level 3 (the default) simply clears any size formatting. */
    fun setSize(spans: List<SpanData>, start: Int, end: Int, level: Int, length: Int = Int.MAX_VALUE): List<SpanData> {
        if (start >= end) return spans
        val cleared = remove(spans, SpanKind.SIZE, start, end)
        return if (level == TextSizes.DEFAULT_LEVEL) {
            normalize(cleared, length)
        } else {
            normalize(cleared + SpanData(SpanKind.SIZE, start, end, level.coerceIn(TextSizes.MIN_LEVEL, TextSizes.MAX_LEVEL)), length)
        }
    }

    /** The uniform size level of a selection, or null when the selection mixes several sizes. */
    fun sizeLevel(spans: List<SpanData>, start: Int, end: Int): Int? {
        val overlapping = spans.filter { it.kind == SpanKind.SIZE && it.end > start && it.start < end }
        if (overlapping.isEmpty()) return TextSizes.DEFAULT_LEVEL
        for (v in overlapping.map { it.value }.distinct()) {
            if (isFullyCovered(spans, SpanKind.SIZE, start, end, v)) return v
        }
        return null
    }

    /** Spans clipped to [from, to) and shifted so they are relative to [from]. */
    fun slice(spans: List<SpanData>, from: Int, to: Int): List<SpanData> = spans.mapNotNull { s ->
        val a = maxOf(s.start, from)
        val b = minOf(s.end, to)
        if (a < b) s.copy(start = a - from, end = b - from) else null
    }

    fun shift(spans: List<SpanData>, by: Int): List<SpanData> =
        spans.map { it.copy(start = it.start + by, end = it.end + by) }
}
