package com.dearly.app

import com.dearly.app.model.richtext.Block
import com.dearly.app.model.richtext.BlockOps
import com.dearly.app.model.richtext.ListType
import com.dearly.app.model.richtext.SpanData
import com.dearly.app.model.richtext.SpanKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockOpsTest {
    @Test fun splitDistributesTextAndSpans() {
        val b = Block.Text("ab\ncd", listOf(SpanData(SpanKind.BOLD, 1, 4)))
        val r = BlockOps.splitAtNewlines(b)
        assertEquals(2, r.size)
        assertEquals("ab", r[0].text)
        assertEquals(listOf(SpanData(SpanKind.BOLD, 1, 2)), r[0].spans)
        assertEquals("cd", r[1].text)
        assertEquals(listOf(SpanData(SpanKind.BOLD, 0, 1)), r[1].spans)
    }

    @Test fun splitKeepsListTypeAndResetsChecked() {
        val r = BlockOps.splitAtNewlines(Block.Text("a\nb", list = ListType.CHECK, checked = true))
        assertTrue(r[0].checked)
        assertEquals(false, r[1].checked)
        assertEquals(ListType.CHECK, r[1].list)
    }

    @Test fun enterOnEmptyListItemBecomesParagraph() {
        val r = BlockOps.splitAtNewlines(Block.Text("\n", list = ListType.BULLET))
        assertEquals(1, r.size)
        assertEquals(ListType.NONE, r[0].list)
    }

    @Test fun pastedMultiLineTextSplitsIntoManyBlocks() {
        assertEquals(3, BlockOps.splitAtNewlines(Block.Text("a\nb\nc")).size)
    }

    @Test fun noNewlineReturnsSameBlock() {
        val b = Block.Text("hello")
        assertEquals(listOf(b), BlockOps.splitAtNewlines(b))
    }

    @Test fun mergeShiftsSpans() {
        val m = BlockOps.mergeWithPrevious(
            Block.Text("ab", listOf(SpanData(SpanKind.BOLD, 0, 2))),
            Block.Text("cd", listOf(SpanData(SpanKind.BOLD, 0, 2)))
        )
        assertEquals("abcd", m.text)
        assertEquals(listOf(SpanData(SpanKind.BOLD, 0, 4)), m.spans)
    }

    @Test fun numberingRestartsAfterInterruption() {
        val blocks = listOf(
            Block.Text("a", list = ListType.NUMBERED), Block.Text("b", list = ListType.NUMBERED),
            Block.Text("x"), Block.Text("c", list = ListType.NUMBERED)
        )
        assertArrayEquals(intArrayOf(1, 2, 0, 1), BlockOps.numbering(blocks))
    }

    @Test fun documentAlwaysEndsWithTextBlock() {
        assertTrue(BlockOps.ensureTrailingText(listOf(BlockOps.emptyTable())).last() is Block.Text)
        assertEquals(1, BlockOps.ensureTrailingText(emptyList()).size)
    }
}
