package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for editor undo/redo.
 *
 * The dangerous cases are not the happy path: they are switching themes mid-edit, dragging a slider
 * that would otherwise create hundreds of entries, and a "change" that changes nothing.
 */
class ThemeHistoryTest {

    private fun doc(key: String = "#111111", id: String = "t1", name: String = "T") =
        ThemeDoc(id, name, "builtin.preset.windmill", mapOf("colorKey" to key))

    @Test
    fun aFreshHistoryHasNothingToUndo() {
        val h = ThemeHistory(doc())
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun anEditCanBeUndoneAndRedone() {
        val h = ThemeHistory(doc())
        h.push(doc("#222222"))
        assertEquals("#222222", h.current.overrides["colorKey"])
        assertEquals("#111111", h.undo()!!.overrides["colorKey"])
        assertEquals("#222222", h.redo()!!.overrides["colorKey"])
    }

    @Test
    fun anEditThatChangesNothingCreatesNoEntry() {
        val h = ThemeHistory(doc())
        assertFalse(h.push(doc()))
        assertFalse(h.canUndo)
    }

    @Test
    fun aNewEditDiscardsTheRedoBranch() {
        val h = ThemeHistory(doc())
        h.push(doc("#222222"))
        h.undo()
        assertTrue(h.canRedo)
        h.push(doc("#333333"))
        assertFalse(h.canRedo)
    }

    @Test
    fun rapidEditsOfTheSamePropertyCollapseIntoOneStep() {
        val h = ThemeHistory(doc())
        h.push(doc("#222222"), groupKey = "colorKey", now = 1000)
        h.push(doc("#333333"), groupKey = "colorKey", now = 1100)
        h.push(doc("#444444"), groupKey = "colorKey", now = 1200)
        assertEquals(1, h.undoDepth)
        assertEquals("#111111", h.undo()!!.overrides["colorKey"])
    }

    @Test
    fun editsOfDifferentPropertiesAreSeparateSteps() {
        val h = ThemeHistory(doc())
        h.push(doc("#222222"), groupKey = "colorKey", now = 1000)
        h.push(doc("#222222", name = "Renamed"), groupKey = "name", now = 1050)
        assertEquals(2, h.undoDepth)
    }

    @Test
    fun aSlowEditStartsANewStepEvenForTheSameProperty() {
        val h = ThemeHistory(doc(), groupWindowMillis = 600)
        h.push(doc("#222222"), groupKey = "colorKey", now = 1000)
        h.push(doc("#333333"), groupKey = "colorKey", now = 5000)
        assertEquals(2, h.undoDepth)
    }

    @Test
    fun switchingThemesDiscardsHistory() {
        val h = ThemeHistory(doc())
        h.push(doc("#222222"))
        h.load(doc("#999999", id = "t2"))
        assertFalse(h.canUndo)
        assertEquals("t2", h.themeId)
        assertEquals("#999999", h.current.overrides["colorKey"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEditForAnotherThemeIsRejected() {
        val h = ThemeHistory(doc())
        h.push(doc("#222222", id = "other"))
    }

    @Test
    fun resetReturnsToTheOriginalState() {
        val h = ThemeHistory(doc())
        h.push(doc("#222222"))
        h.push(doc("#333333"))
        assertEquals("#111111", h.resetToOriginal().overrides["colorKey"])
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun undoOnAnEmptyHistoryReturnsNull() {
        assertNull(ThemeHistory(doc()).undo())
        assertNull(ThemeHistory(doc()).redo())
    }

    @Test
    fun theStackIsBounded() {
        val h = ThemeHistory(doc(), groupWindowMillis = 0, limit = 5)
        repeat(20) { i -> h.push(doc("#00000$i"), groupKey = "k$i", now = i * 1000L) }
        assertEquals(5, h.undoDepth)
        assertNotNull(h.undo())
    }
}
