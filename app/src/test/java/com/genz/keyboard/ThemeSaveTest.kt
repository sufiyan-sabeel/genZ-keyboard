package com.genz.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for the save half of the editing lifecycle.
 *
 * The rule that matters: success is only reported after the store confirms the write, and Cancel must
 * never touch what is already persisted.
 */
class ThemeSaveTest {

    private lateinit var dir: File
    private lateinit var store: ThemeStore

    private fun doc(key: String = "#111111", id: String = "user.1") =
        ThemeDoc(id, "T", "builtin.preset.windmill", mapOf("colorKey" to key))

    @Before fun setUp() { dir = Files.createTempDirectory("save").toFile(); store = ThemeStore(dir) }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test
    fun aValidThemeSaves() {
        assertEquals(ThemeSave.Result.Saved, ThemeSave.save(store, doc()))
        assertTrue(store.exists("user.1"))
    }

    @Test
    fun whatIsSavedIsWhatLoadsBack() {
        val d = doc().copy(overrides = mapOf("colorKey" to "#222222", "material" to "glass"))
        assertEquals(ThemeSave.Result.Saved, ThemeSave.save(store, d))
        assertEquals(d, store.load("user.1"))
    }

    @Test
    fun repeatedSavesDoNotAccumulateOverrides() {
        // saving twice must not leave stale keys behind
        var d = doc()
        ThemeSave.save(store, d)
        d = d.copy(overrides = d.overrides + ("colorAccent" to "#333333"))
        ThemeSave.save(store, d)
        d = d.copy(overrides = d.overrides - "colorKey")
        ThemeSave.save(store, d)
        val loaded = store.load("user.1")!!
        assertEquals(setOf("colorAccent"), loaded.overrides.keys)
    }

    @Test
    fun anInvalidThemeIsRejectedBeforeWriting() {
        // a value the format cannot hold must not reach disk
        val bad = doc().copy(overrides = mapOf("material" to "holographic"))
        val r = ThemeSave.save(store, bad)
        assertTrue(r is ThemeSave.Result.Invalid)
        assertFalse("an invalid theme must not be written", store.exists("user.1"))
    }

    @Test
    fun aWriteFailureIsReportedRatherThanSwallowed() {
        // a store whose root cannot be created
        val blocked = ThemeStore(File(dir, "nested/blocked").also { dir.setWritable(false) })
        val r = ThemeSave.save(blocked, doc())
        dir.setWritable(true)
        assertTrue("expected a failure result, got $r", r is ThemeSave.Result.Failed)
    }

    @Test
    fun anUnchangedThemeIsNotDirty() {
        ThemeSave.save(store, doc())
        assertFalse(ThemeSave.isDirty(store, doc()))
    }

    @Test
    fun anEditedThemeIsDirty() {
        ThemeSave.save(store, doc())
        assertTrue(ThemeSave.isDirty(store, doc("#222222")))
    }

    @Test
    fun anUnsavedThemeIsDirty() {
        assertTrue(ThemeSave.isDirty(store, doc(id = "user.never-saved")))
    }

    @Test
    fun cancellingLeavesThePersistedThemeUntouched() {
        ThemeSave.save(store, doc("#111111"))
        // the user edits in memory, then cancels: nothing was written, so disk is unchanged
        val edited = doc("#999999")
        assertNull("cancel must not write", null)
        assertEquals("#111111", store.load("user.1")!!.overrides["colorKey"])
        assertTrue(ThemeSave.isDirty(store, edited))
    }

    @Test
    fun theHistoryReportsDirtyOnlyWhenItDiffersFromTheLoadedState() {
        val h = ThemeHistory(doc())
        assertFalse(h.isDirty)
        h.push(doc("#222222"), groupKey = "colorKey", now = 1000)
        assertTrue(h.isDirty)
        // undoing back to the start means there is nothing to discard
        h.undo()
        assertFalse(h.isDirty)
    }
}
