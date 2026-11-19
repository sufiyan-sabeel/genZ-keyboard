package com.genz.keyboard

import android.content.Context
import java.io.File

/**
 * The in-progress editing session, saved to disk.
 *
 * Rotation is handled by saved instance state, but that does not survive the process being killed —
 * which Android does routinely when the app is backgrounded. Without this, a user who switches away
 * mid-edit loses everything they had not explicitly saved.
 *
 * Writes go through a temp file and a rename, so a crash mid-write leaves the previous session
 * intact rather than a truncated one.
 */
object ThemeDraftSession {

    private const val FILE = "editing_session.json"
    private const val TMP = "editing_session.tmp"
    private const val MAX_BYTES = 256L * 1024

    private fun file(context: Context) = File(context.filesDir, FILE)
    private fun tmp(context: Context) = File(context.filesDir, TMP)

    /** @return true when the session was written. */
    fun save(context: Context, history: ThemeHistory): Boolean = runCatching {
        val json = history.snapshot()
        if (json.length > MAX_BYTES) return false
        val tmpFile = tmp(context)
        tmpFile.writeText(json)
        // rename is atomic on the same filesystem, so a crash cannot leave a half-written session
        tmpFile.renameTo(file(context))
    }.getOrDefault(false)

    /**
     * The saved session, or null when there is none.
     *
     * A corrupt or oversized file is deleted rather than retried forever: a session that cannot be
     * parsed will never parse, and leaving it means the editor opens broken every time.
     */
    fun load(context: Context): ThemeHistory? = runCatching {
        val f = file(context)
        if (!f.isFile) return null
        if (f.length() > MAX_BYTES) { f.delete(); return null }
        val restored = ThemeHistory.restore(f.readText())
        if (restored == null) { f.delete(); return null }
        restored
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete(); tmp(context).delete() }
    }

    fun exists(context: Context): Boolean = file(context).isFile
}
