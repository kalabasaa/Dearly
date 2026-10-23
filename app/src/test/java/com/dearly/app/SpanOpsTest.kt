package com.dearly.app

import com.dearly.app.model.richtext.SpanData
import com.dearly.app.model.richtext.SpanKind
import com.dearly.app.model.richtext.SpanOps
import com.dearly.app.model.richtext.TextSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpanOpsTest {
    private fun bold(a: Int, b: Int) = SpanData(SpanKind.BOLD, a, b)

    @Test fun toggleAppliesToPartialSelectionOnly() {
        val r = SpanOps.toggle(emptyList(), SpanKind.BOLD, 2, 5, 10)
        assertEquals(listOf(bold(2, 5)), r)
    }

    @Test fun toggleRemovesWhenFullyCovered() {
        val r = SpanOps.toggle(listOf(bold(0, 10)), SpanKind.BOLD, 3, 6, 10)
        assertEquals(listOf(bold(0, 3), bold(6, 10)), r)
    }

    @Test fun toggleOnPartiallyStyledSelectionAppliesEverywhere() {
        val r = SpanOps.toggle(listOf(bold(0, 4)), SpanKind.BOLD, 2, 8, 10)
        assertEquals(listOf(bold(0, 8)), r)
    }

    @Test fun adjacentSpansMerge() {
        val r = SpanOps.normalize(listOf(bold(0, 3), bold(3, 6)), 10)
        assertEquals(listOf(bold(0, 6)), r)
    }

    @Test fun normalizeClipsAndDropsEmpty() {
        val r = SpanOps.normalize(listOf(bold(-5, 3), bold(8, 20), bold(4, 4)), 10)
        assertEquals(listOf(bold(0, 3), bold(8, 10)), r)
    }

    @Test fun overlappingDifferentKindsAreIndependent() {
        val spans = SpanOps.toggle(listOf(bold(0, 6)), SpanKind.ITALIC, 3, 9, 10)
        assertTrue(SpanOps.isFullyCovered(spans, SpanKind.BOLD, 0, 6))
        assertTrue(SpanOps.isFullyCovered(spans, SpanKind.ITALIC, 3, 9))
        assertFalse(SpanOps.isFullyCovered(spans, SpanKind.ITALIC, 0, 9))
    }

    @Test fun isFullyCoveredDetectsGap() {
        assertFalse(SpanOps.isFullyCovered(listOf(bold(0, 3), bold(4, 8)), SpanKind.BOLD, 0, 8))
        assertFalse(SpanOps.isFullyCovered(listOf(bold(0, 3)), SpanKind.BOLD, 3, 3))
    }

    @Test fun setSizeReplacesOnlySelectedRange() {
        var s = SpanOps.setSize(emptyList(), 0, 10, 5, 10)
        s = SpanOps.setSize(s, 3, 6, 1, 10)
        assertEquals(1, SpanOps.sizeLevel(s, 3, 6))
        assertEquals(5, SpanOps.sizeLevel(s, 0, 3))
        assertEquals(5, SpanOps.sizeLevel(s, 6, 10))
        assertNull(SpanOps.sizeLevel(s, 0, 10))
    }

    @Test fun sizeThreeIsDefaultAndStoresNothing() {
        val s = SpanOps.setSize(listOf(SpanData(SpanKind.SIZE, 0, 5, 4)), 0, 5, TextSizes.DEFAULT_LEVEL, 5)
        assertTrue(s.isEmpty())
        assertEquals(3, SpanOps.sizeLevel(s, 0, 5))
    }

    @Test fun sizeFourIsVisiblyLargerThanThreeAndFiveThanFour() {
        val ratio = TextSizes.factor(4) / TextSizes.factor(3)
        assertTrue(ratio > 1.1f && ratio < 1.3f)
        assertTrue(TextSizes.factor(5) / TextSizes.factor(4) > 1.1f)
        assertEquals(1.0f, TextSizes.factor(3), 0.0f)
        assertTrue(TextSizes.factor(1) < TextSizes.factor(2) && TextSizes.factor(4) < TextSizes.factor(5))
    }

    @Test fun highlightReplacesAndToggles() {
        var s = SpanOps.toggle(emptyList(), SpanKind.HIGHLIGHT, 1, 4, 10)
        assertTrue(SpanOps.isFullyCovered(s, SpanKind.HIGHLIGHT, 1, 4))
        s = SpanOps.toggle(s, SpanKind.HIGHLIGHT, 1, 4, 10)
        assertTrue(s.isEmpty())
    }

    @Test fun sliceShiftsRelativeToStart() {
        val r = SpanOps.slice(listOf(bold(2, 8)), 4, 6)
        assertEquals(listOf(bold(0, 2)), r)
        assertTrue(SpanOps.slice(listOf(bold(0, 3)), 3, 6).isEmpty())
    }
}
