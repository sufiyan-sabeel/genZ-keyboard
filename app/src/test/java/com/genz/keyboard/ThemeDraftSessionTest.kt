package com.genz.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for disk-backed draft recovery.
 *
 * The cases that matter are the failure ones: a corrupt or oversized session must never be able to
 * destroy a valid draft the user still has open.
 */
class ThemeDraftSessionTest {

    private lateinit var dir: File

    private fun doc(key: String = "#111111", id: String = "t1", name: String = "T") =
        ThemeDoc(id, name, "builtin.preset.windmill", mapOf("colorKey" to key))

    private fun history(vararg edits: String): ThemeHistory {
        val h = ThemeHistory(doc())
        edits.forEachIndexed { i, c -> h.push(doc(c), groupKey = "colorKey", now = i * 5000L) }
        return h
    }

    @Before fun setUp() { dir = Files.createTempDirectory("draft").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test
    fun aDraftRoundTrips() {
        val h = history("#222222")
        assertTrue(ThemeDraftSession.save(dir, h))
        val restored = ThemeDraftSession.load(dir)
        assertNotNull(restored)
        assertEquals(h.current, restored!!.current)
        assertEquals(h.undoDepth, restored.undoDepth)
    }

    @Test
    fun aFreshInstanceRestoresTheSameDraft() {
        // a new process must recover exactly what the previous one had
        ThemeDraftSession.save(dir, history("#222222", "#333333"))
        val restored = ThemeDraftSession.load(dir)!!
        assertEquals("#333333", restored.current.overrides["colorKey"])
        assertTrue(restored.canUndo)
    }

    @Test
    fun savingLeavesNoTemporaryFileBehind() {
        ThemeDraftSession.save(dir, history("#222222"))
        val names = dir.list()!!.toList()
        assertTrue("expected the session file", names.any { it == "editing_session.json" })
        assertFalse("temp file must be renamed away", names.any { it == "editing_session.tmp" })
    }

    @Test
    fun anInterruptedWriteDoesNotDestroyThePreviousDraft() {
        // simulate a crash mid-write: a temp file exists but the real one is still the old draft
        ThemeDraftSession.save(dir, history("#222222"))
        File(dir, "editing_session.tmp").writeText("{ truncated")
        val restored = ThemeDraftSession.load(dir)!!
        assertEquals("#222222", restored.current.overrides["colorKey"])
    }

    @Test
    fun corruptJsonIsDiscardedRatherThanCrashing() {
        ThemeDraftSession.save(dir, history("#222222"))
        File(dir, "editing_session.json").writeText("not json at all")
        assertNull(ThemeDraftSession.load(dir))
        assertFalse("a file that cannot parse must be removed", ThemeDraftSession.exists(dir))
    }

    @Test
    fun anOversizedFileIsDiscarded() {
        ThemeDraftSession.save(dir, history("#222222"))
        File(dir, "editing_session.json").writeText("x".repeat(300 * 1024))
        assertNull(ThemeDraftSession.load(dir))
        assertFalse(ThemeDraftSession.exists(dir))
    }

    @Test
    fun aMissingFileLoadsNothing() {
        assertNull(ThemeDraftSession.load(dir))
        assertFalse(ThemeDraftSession.exists(dir))
    }

    @Test
    fun anEmptyFileLoadsNothing() {
        File(dir, "editing_session.json").writeText("")
        assertNull(ThemeDraftSession.load(dir))
    }

    @Test
    fun unknownThemePropertiesSurviveARoundTrip() {
        // a property the editor does not expose must not be dropped by a save/load cycle
        val h = ThemeHistory(doc())
        val withUnknown = doc().copy(
            overrides = mapOf("colorKey" to "#222222", "futureProperty" to "#ABCDEF")
        )
        h.push(withUnknown, groupKey = "multi", now = 1000)
        ThemeDraftSession.save(dir, h)
        val restored = ThemeDraftSession.load(dir)!!
        assertEquals("#ABCDEF", restored.current.overrides["futureProperty"])
    }

    @Test
    fun consecutiveSavesKeepTheLatest() {
        val h = history("#222222")
        ThemeDraftSession.save(dir, h)
        h.push(doc("#333333"), groupKey = "colorKey", now = 90000)
        ThemeDraftSession.save(dir, h)
        assertEquals("#333333", ThemeDraftSession.load(dir)!!.current.overrides["colorKey"])
    }

    @Test
    fun clearingRemovesTheSession() {
        ThemeDraftSession.save(dir, history("#222222"))
        ThemeDraftSession.clear(dir)
        assertFalse(ThemeDraftSession.exists(dir))
        assertNull(ThemeDraftSession.load(dir))
    }

    @Test
    fun aSnapshotFromAnOlderFormatIsRejectedNotMisread() {
        // an unrecognisable payload must load as nothing rather than as a half-parsed theme
        File(dir, "editing_session.json").writeText("""{"version":0,"theme":"something-else"}""")
        assertNull(ThemeDraftSession.load(dir))
    }
}
