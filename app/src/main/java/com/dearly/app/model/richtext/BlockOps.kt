package com.dearly.app.model.richtext

/** Pure paragraph/list editing rules (Enter, Backspace) used by the block editor. */
object BlockOps {

    /**
     * Splits a block at every '\n' (typed Enter or pasted multi-line text).
     * New blocks inherit the list type; checkboxes start unchecked.
     * Pressing Enter on an empty list item turns it into a normal paragraph instead.
     */
    fun splitAtNewlines(block: Block.Text): List<Block.Text> {
        val parts = block.text.split('\n')
        if (parts.size == 1) return listOf(block)
        if (block.list != ListType.NONE && parts.size == 2 && parts.all { it.isEmpty() }) {
            return listOf(Block.Text("", emptyList(), ListType.NONE, false))
        }
        val result = ArrayList<Block.Text>(parts.size)
        var pos = 0
        parts.forEachIndexed { index, part ->
            val spans = SpanOps.normalize(SpanOps.slice(block.spans, pos, pos + part.length), part.length)
            result.add(
                Block.Text(
                    text = part,
                    spans = spans,
                    list = block.list,
                    checked = index == 0 && block.checked
                )
            )
            pos += part.length + 1
        }
        return result
    }

    /** Joins [current] onto the end of [previous]; the caret belongs at previous.text.length. */
    fun mergeWithPrevious(previous: Block.Text, current: Block.Text): Block.Text {
        val text = previous.text + current.text
        val spans = SpanOps.normalize(
            previous.spans + SpanOps.shift(current.spans, previous.text.length),
            text.length
        )
        return previous.copy(text = text, spans = spans)
    }

    fun ensureTrailingText(blocks: List<Block>): List<Block> {
        if (blocks.isEmpty()) return listOf(Block.Text())
        return if (blocks.last() is Block.Text) blocks else blocks + Block.Text()
    }

    /** Ordinal of each numbered item within its consecutive run (1-based); 0 for non-numbered blocks. */
    fun numbering(blocks: List<Block>): IntArray {
        val out = IntArray(blocks.size)
        var counter = 0
        blocks.forEachIndexed { i, b ->
            if (b is Block.Text && b.list == ListType.NUMBERED) {
                counter++
                out[i] = counter
            } else {
                counter = 0
            }
        }
        return out
    }

    fun emptyTable(rows: Int = 2, cols: Int = 2): Block.Table =
        Block.Table(List(rows) { List(cols) { "" } })

    const val MAX_TABLE_ROWS = 30
    const val MAX_TABLE_COLS = 8
}
