package com.dearly.app.model.richtext

/** Inline formatting kinds. [code] is the stable identifier used in persisted JSON. */
enum class SpanKind(val code: String) {
    BOLD("b"), ITALIC("i"), UNDERLINE("u"), SIZE("z"), HIGHLIGHT("h");

    companion object {
        fun fromCode(code: String?): SpanKind? = values().firstOrNull { it.code == code }
    }
}

/**
 * A formatted range [start, end) inside one text block.
 * For [SpanKind.SIZE] the [value] is the size level 1..5 (level 3 is the default and never stored).
 */
data class SpanData(val kind: SpanKind, val start: Int, val end: Int, val value: Int = 0)

enum class ListType(val code: String) {
    NONE("none"), BULLET("bullet"), NUMBERED("number"), CHECK("check");

    companion object {
        fun fromCode(code: String?): ListType = values().firstOrNull { it.code == code } ?: NONE
    }
}

object TextSizes {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 5
    const val DEFAULT_LEVEL = 3

    // XS, S, M (default), L, XL. Multiplies the editor base size (17sp), so the levels come out at roughly
    // 14, 16, 17, 20 and 24sp: each step is noticeable without being exaggerated.
    private val FACTORS = floatArrayOf(0.82f, 0.92f, 1.0f, 1.18f, 1.42f)

    fun factor(level: Int): Float = FACTORS[level.coerceIn(MIN_LEVEL, MAX_LEVEL) - 1]
}

/** Display size of a sticker in a note, in dp (the longest side). Small by default; the user can change it. */
object StickerSizes {
    const val MIN = 32
    const val MAX = 200
    const val DEFAULT = 72
    const val STEP = 8

    fun clamp(size: Int): Int = size.coerceIn(MIN, MAX)
}

sealed class Block {
    /** One paragraph or one list item. */
    data class Text(
        val text: String = "",
        val spans: List<SpanData> = emptyList(),
        val list: ListType = ListType.NONE,
        val checked: Boolean = false
    ) : Block()

    /** A real table: [cells] is a rectangular rows x columns grid of plain text. */
    data class Table(val cells: List<List<String>>) : Block()

    /** Reference to a reusable sticker from the sticker library, shown left-aligned at [size] dp. */
    data class Sticker(val stickerId: Long, val size: Int = StickerSizes.DEFAULT) : Block()
}

data class Document(val blocks: List<Block>) {

    /** Plain text used for search and previews. Stickers contribute nothing. */
    fun plainText(): String = blocks.mapNotNull { block ->
        when (block) {
            is Block.Text -> block.text
            is Block.Table -> block.cells.joinToString("\n") { row -> row.joinToString(" ") }
            is Block.Sticker -> null
        }
    }.filter { it.isNotBlank() }.joinToString("\n")

    fun stickerIds(): Set<Long> = blocks.filterIsInstance<Block.Sticker>().map { it.stickerId }.toSet()

    /** True when the document holds anything worth saving. */
    fun hasContent(): Boolean = blocks.any { block ->
        when (block) {
            is Block.Text -> block.text.isNotBlank()
            is Block.Table -> true
            is Block.Sticker -> true
        }
    }

    companion object {
        fun empty() = Document(listOf(Block.Text()))

        fun fromPlainText(text: String): Document =
            Document(text.split('\n').map { Block.Text(it) }.ifEmpty { listOf(Block.Text()) })
    }
}
