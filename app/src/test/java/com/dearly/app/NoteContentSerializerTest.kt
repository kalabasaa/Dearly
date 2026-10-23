package com.dearly.app

import com.dearly.app.model.richtext.Block
import com.dearly.app.model.richtext.Document
import com.dearly.app.model.richtext.ListType
import com.dearly.app.model.richtext.NoteContentSerializer
import com.dearly.app.model.richtext.SpanData
import com.dearly.app.model.richtext.SpanKind
import com.dearly.app.model.richtext.StickerSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteContentSerializerTest {
    private fun sample() = Document(
        listOf(
            Block.Text(
                "Hello \"world\"\tline", listOf(
                    // already in canonical (start, kind) order
                    SpanData(SpanKind.BOLD, 0, 5), SpanData(SpanKind.UNDERLINE, 0, 3),
                    SpanData(SpanKind.HIGHLIGHT, 1, 4), SpanData(SpanKind.ITALIC, 2, 8),
                    SpanData(SpanKind.SIZE, 6, 9, 5)
                )
            ),
            Block.Text("bullet", list = ListType.BULLET),
            Block.Text("numbered", list = ListType.NUMBERED),
            Block.Text("done task", list = ListType.CHECK, checked = true),
            Block.Text("open task", list = ListType.CHECK, checked = false),
            Block.Table(listOf(listOf("a", "b"), listOf("", "d é ✓"))),
            Block.Sticker(42L, 120),
            Block.Text("end")
        )
    )

    @Test fun roundTripPreservesEverything() {
        val doc = sample()
        val restored = NoteContentSerializer.fromJson(NoteContentSerializer.toJson(doc))
        assertEquals(doc, restored)
    }

    @Test fun unsortedSpansAreWrittenInCanonicalOrder() {
        val unsorted = Document(listOf(Block.Text("abcdefghij", listOf(
            SpanData(SpanKind.ITALIC, 5, 8), SpanData(SpanKind.BOLD, 0, 2)))))
        val back = NoteContentSerializer.fromJson(NoteContentSerializer.toJson(unsorted))
        assertEquals(listOf(SpanData(SpanKind.BOLD, 0, 2), SpanData(SpanKind.ITALIC, 5, 8)), (back.blocks[0] as Block.Text).spans)
    }

    @Test fun roundTripIsStable() {
        val json = NoteContentSerializer.toJson(sample())
        assertEquals(json, NoteContentSerializer.toJson(NoteContentSerializer.fromJson(json)))
    }

    @Test fun corruptJsonFallsBackToPlainText() {
        val d = NoteContentSerializer.fromJson("{not json", "line1\nline2")
        assertEquals(2, d.blocks.size)
        assertEquals("line1", (d.blocks[0] as Block.Text).text)
    }

    @Test fun emptyJsonFallsBackToPlainText() {
        assertEquals("hi", (NoteContentSerializer.fromJson("", "hi").blocks[0] as Block.Text).text)
    }

    @Test fun unknownBlocksAndBadSpansAreIgnored() {
        val json = """{"v":9,"blocks":[{"t":"future"},{"t":"text","x":"abc","s":[{"k":"zz","a":0,"e":2},{"k":"b","a":-3,"e":99}]}]}"""
        val d = NoteContentSerializer.fromJson(json)
        val t = d.blocks[0] as Block.Text
        assertEquals(listOf(SpanData(SpanKind.BOLD, 0, 3)), t.spans)
    }

    @Test fun raggedTablesArePadded() {
        val json = """{"v":1,"blocks":[{"t":"table","rows":[["a"],["b","c"]]}]}"""
        val table = NoteContentSerializer.fromJson(json).blocks[0] as Block.Table
        assertEquals(listOf(listOf("a", ""), listOf("b", "c")), table.cells)
    }

    @Test fun stickerWithoutSizeGetsSmallDefault() {
        val json = """{"v":1,"blocks":[{"t":"sticker","id":7},{"t":"text","x":"hi"}]}"""
        val sticker = NoteContentSerializer.fromJson(json).blocks[0] as Block.Sticker
        assertEquals(StickerSizes.DEFAULT, sticker.size)
    }

    @Test fun stickerSizeIsKeptAndClamped() {
        val doc = Document(listOf(Block.Sticker(1L, 150), Block.Sticker(2L, 5000), Block.Sticker(3L, 1), Block.Text()))
        val sizes = NoteContentSerializer.fromJson(NoteContentSerializer.toJson(doc)).blocks
            .filterIsInstance<Block.Sticker>().map { it.size }
        assertEquals(listOf(150, StickerSizes.MAX, StickerSizes.MIN), sizes)
    }

    @Test fun plainTextAndStickerIds() {
        val d = sample()
        assertTrue(d.plainText().contains("d é ✓"))
        assertEquals(setOf(42L), d.stickerIds())
        assertTrue(d.hasContent())
        assertFalse(Document.empty().hasContent())
    }
}
