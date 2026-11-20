package com.genz.keyboard

/**
 * The save half of the editing lifecycle.
 *
 * Saving is two steps that can each fail: the document must be valid, and the write must reach disk.
 * Reporting "saved" before the store confirms would be a lie the user only discovers after a restart,
 * so the result distinguishes an invalid document from a failed write and never claims success early.
 */
object ThemeSave {

    sealed interface Result {
        data object Saved : Result
        /** The document itself is not acceptable; retrying will not help. */
        data class Invalid(val reason: String) : Result
        /** The document is fine but the write failed; retrying may work. */
        data class Failed(val reason: String) : Result
    }

    /**
     * Validate, then persist.
     *
     * Validation runs through the same codec an imported theme passes, so a theme saved here can
     * always be loaded by anything that accepts the format.
     */
    fun save(store: ThemeStore, doc: ThemeDoc): Result {
        val encoded = ThemeCodec.encode(doc)
        when (val v = ThemeCodec.decode(encoded)) {
            is ThemeValidation.Invalid -> return Result.Invalid(v.reason)
            is ThemeValidation.Ok -> Unit
        }
        return if (store.save(doc)) Result.Saved else Result.Failed("Could not write the theme file")
    }

    /**
     * Whether a save would change what is already on disk.
     *
     * Used to keep the dirty flag honest: a document equal to the stored one is not a change, even if
     * the user has been undoing back and forth.
     */
    fun isDirty(store: ThemeStore, doc: ThemeDoc): Boolean {
        val stored = store.load(doc.id) ?: return true
        return stored != doc
    }
}
