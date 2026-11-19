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

    /**
     * Reset every property back to the theme as it was loaded.
     *
     * This is an ordinary edit, not a wipe: the reset itself is pushed onto the undo stack so the
     * user can change their mind. Clearing the history here would make an accidental reset
     * unrecoverable, which is exactly when undo matters most.
     */
    fun resetToOriginal(): ThemeDoc {
        val original = undoStack.firstOrNull() ?: current
        if (original == current) return current
        undoStack.addLast(current)
        if (undoStack.size > limit) undoStack.removeFirst()
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

    /**
     * Group key for an edit, derived from what actually changed.
     *
     * A single property changing repeatedly (a slider being dragged) shares a key and collapses into
     * one undo step. Several properties changing at once — a preset load, a reset — returns null so
     * it becomes its own discrete entry.
     */
    fun groupKeyFor(next: ThemeDoc): String? {
        val keys = (next.overrides.keys + current.overrides.keys).toSet()
        val changed = keys.filter { next.overrides[it] != current.overrides[it] }
        return when {
            changed.size == 1 -> changed.first()
            changed.isEmpty() && next.name != current.name -> "name"
            changed.isEmpty() && next.wallpaper != current.wallpaper -> "wallpaper"
            else -> null
        }
    }

    /**
     * Reset a single property, leaving everything else alone.
     *
     * Undoable like any other edit. If the property was already at its original value nothing
     * changes and no history entry is created, so the button never lies.
     */
    fun resetProperty(key: String): ThemeDoc {
        val original = undoStack.firstOrNull() ?: current
        val originalValue = original.overrides[key]
        val nextOverrides = current.overrides.toMutableMap()
        if (originalValue == null) nextOverrides.remove(key) else nextOverrides[key] = originalValue
        val next = current.copy(overrides = nextOverrides)
        if (next == current) return current
        push(next, groupKey = "reset:$key")
        return current
    }

    /**
     * A serialisable snapshot of the editor, for surviving configuration changes.
     *
     * Stored as theme JSON rather than objects because a rotation destroys the composition; without
     * this the user's unsaved edits and their whole undo history vanish on a screen turn.
     */
    fun snapshot(): String {
        fun encodeAll(stack: ArrayDeque<ThemeDoc>): String =
            stack.joinToString(",", "[", "]") { ThemeCodec.encode(it) }
        return buildString {
            append("{\"current\":").append(ThemeCodec.encode(current))
            append(",\"undo\":").append(encodeAll(undoStack))
            append(",\"redo\":").append(encodeAll(redoStack))
            append("}")
        }
    }

    companion object {
        /** Rebuild a history from [snapshot], or null when the snapshot is unusable. */
        fun restore(snapshot: String, limit: Int = 100): ThemeHistory? {
            val o = ThemeJson.decode(snapshot) ?: return null
            val currentJson = o["current"] as? String ?: return null
            val current = (ThemeCodec.decode(currentJson) as? ThemeValidation.Ok)?.doc ?: return null

            fun decodeAll(raw: Any?): List<ThemeDoc> =
                (raw as? List<*>).orEmpty().mapNotNull { item ->
                    (item as? String)?.let { ThemeCodec.decode(it) as? ThemeValidation.Ok }?.doc
                }

            val h = ThemeHistory(current, limit = limit)
            decodeAll(o["undo"]).forEach { h.undoStack.addLast(it) }
            decodeAll(o["redo"]).forEach { h.redoStack.addLast(it) }
            return h
        }
    }
}
