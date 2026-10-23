package com.dearly.app

import com.dearly.app.model.richtext.UndoHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoHistoryTest {
    @Test fun undoRedoWalkTheTimeline() {
        val h = UndoHistory<String>()
        h.reset("a")
        assertFalse(h.canUndo); assertFalse(h.canRedo)
        h.push("b"); h.push("c")
        assertTrue(h.canUndo); assertFalse(h.canRedo)
        assertEquals("b", h.undo()); assertEquals("a", h.undo()); assertNull(h.undo())
        assertTrue(h.canRedo)
        assertEquals("b", h.redo()); assertEquals("c", h.redo()); assertNull(h.redo())
    }

    @Test fun pushAfterUndoDropsRedoBranch() {
        val h = UndoHistory<Int>()
        h.reset(1); h.push(2); h.push(3)
        h.undo()
        h.push(9)
        assertFalse(h.canRedo)
        assertEquals(2, h.undo())
    }

    @Test fun duplicateStateIsIgnored() {
        val h = UndoHistory<String>()
        h.reset("a")
        assertFalse(h.push("a"))
        assertFalse(h.canUndo)
    }

    @Test fun limitDropsOldestButKeepsConsistency() {
        val h = UndoHistory<Int>(limit = 3)
        h.reset(0)
        for (i in 1..10) h.push(i)
        assertEquals(10, h.current())
        assertEquals(9, h.undo()); assertEquals(8, h.undo()); assertNull(h.undo())
    }
}
