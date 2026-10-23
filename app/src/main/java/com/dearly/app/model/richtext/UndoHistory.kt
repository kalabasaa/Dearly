package com.dearly.app.model.richtext

/**
 * Snapshot-based undo/redo. The editor pushes a state after each committed edit;
 * auto-saving never touches this history.
 */
class UndoHistory<T>(private val limit: Int = 100) {
    private val states = ArrayList<T>()
    private var index = -1

    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index >= 0 && index < states.size - 1

    fun reset(initial: T) {
        states.clear()
        states.add(initial)
        index = 0
    }

    fun current(): T? = states.getOrNull(index)

    /** Returns true if a new history entry was created. Identical consecutive states are ignored. */
    fun push(state: T): Boolean {
        if (index >= 0 && states[index] == state) return false
        while (states.size > index + 1) states.removeAt(states.size - 1)
        states.add(state)
        index++
        if (states.size > limit) {
            states.removeAt(0)
            index--
        }
        return true
    }

    fun undo(): T? {
        if (!canUndo) return null
        index--
        return states[index]
    }

    fun redo(): T? {
        if (!canRedo) return null
        index++
        return states[index]
    }
}
