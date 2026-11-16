package com.genz.keyboard

/**
 * Rules that decide whether an offered Typo-Undo may actually be applied.
 *
 * Applying an undo deletes text from the editor. If the editor has moved on since the correction —
 * the user typed, the cursor jumped, the app rewrote the field, or the keyboard was re-bound to a
 * different field — deleting the wrong span corrupts the user's text. A missing undo is a small
 * annoyance; a wrong undo is data loss. Every check here therefore fails closed.
 *
 * Pure Kotlin on purpose: the whole decision is testable without a device.
 */
object UndoGuard {

    /**
     * How long an undo stays available. Long enough to notice a bad correction, short enough that
     * the state cannot outlive the sentence it belongs to.
     */
    const val TTL_MILLIS = 6_000L

    /** True when the offer is still current: not null, not expired, and from this same field. */
    fun isLive(state: UndoAutoCorrectState?, fieldKey: String, nowMillis: Long): Boolean {
        if (state == null) return false
        if (state.committedText.isEmpty() || state.originalWord.isEmpty()) return false
        if (state.fieldKey != fieldKey) return false
        return nowMillis <= state.expiresAtMillis
    }

    /**
     * True when the editor still ends with the text the correction committed.
     *
     * This is the check that makes replacement safe: without it the undo would delete
     * [UndoAutoCorrectState.committedText]'s length of characters from whatever happens to sit
     * before the cursor now.
     */
    fun matchesEditor(state: UndoAutoCorrectState, textBeforeCursor: CharSequence?): Boolean {
        if (textBeforeCursor == null) return false
        val committed = state.committedText
        if (committed.isEmpty()) return false
        if (textBeforeCursor.length < committed.length) return false
        val tail = textBeforeCursor.subSequence(
            textBeforeCursor.length - committed.length,
            textBeforeCursor.length
        )
        return tail.toString() == committed
    }
}
