package com.genz.keyboard

/**
 * Undo/redo history for the theme editor.
 *
 * Every entry is a whole [ThemeDoc] snapshot rather than a diff. A theme is small — a map of string
 * overrides — so snapshots are cheap, and they make undo trivially correct: there is no risk of a
 * diff being applied to a state it was not computed against.
 *
 * Kept pure so the interesting cases (switching themes mid-edit, grouped edits, a no-op change) are
 * tested without a device.
 */
class ThemeHistory(
    initial: ThemeDoc,
    /** Consecutive edits of the same key within this window collapse into one undo step. */
    private val groupWindowMillis: Long = 600,
    private val limit: Int = 100
) {

    private val undoStack = ArrayDeque<ThemeDoc>()
    private val redoStack = ArrayDeque<ThemeDoc>()

    var current: ThemeDoc = initial
        private set

    /** The theme this history belongs to. A different id means the history must be discarded. */
    var themeId: String = initial.id
        private set

    private var lastGroupKey: String? = null
    private var lastEditAt: Long = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDepth: Int get() = undoStack.size
    val redoDepth: Int get() = redoStack.size

    /**
     * Record an edit.
     *
     * @param groupKey edits sharing a key in quick succession collapse into one step, so dragging a
     *   slider does not create two hundred undo entries.
     * @return false when the edit changed nothing, in which case no history entry is created.
     */
    fun push(next: ThemeDoc, groupKey: String? = null, now: Long = System.currentTimeMillis()): Boolean {
        if (next == current) return false
        if (next.id != themeId) {
            // an edit for another theme would corrupt this history
            throw IllegalArgumentException("edit belongs to theme ${next.id}, history holds $themeId")
        }

        val sameGroup = groupKey != null &&
            groupKey == lastGroupKey &&
            now - lastEditAt <= groupWindowMillis

        if (!sameGroup) {
            undoStack.addLast(current)
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        // a new edit always invalidates the redo branch
        redoStack.clear()

        current = next
        lastGroupKey = groupKey
        lastEditAt = now
        return true
    }

    /** @return the previous state, or null when there is nothing to undo. */
    fun undo(): ThemeDoc? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        // an undo ends the current group, so the next edit starts a fresh step
        lastGroupKey = null
        return current
    }

    /** @return the next state, or null when there is nothing to redo. */
    fun redo(): ThemeDoc? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        lastGroupKey = null
        return current
    }

    /** Reset to the theme as it was loaded, discarding all history. */
    fun resetToOriginal(): ThemeDoc {
        val original = undoStack.firstOrNull() ?: current
        undoStack.clear()
        redoStack.clear()
        lastGroupKey = null
        current = original
        return current
    }

    /**
     * Load a different theme.
     *
     * History is discarded rather than carried over: undoing into another theme's state would
     * silently swap the user's artwork.
     */
    fun load(doc: ThemeDoc) {
        undoStack.clear()
        redoStack.clear()
        lastGroupKey = null
        lastEditAt = 0L
        current = doc
        themeId = doc.id
    }
}
