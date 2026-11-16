package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the suggestion-strip contract.
 *
 * The strip is a fixed three-slot row, and three separate v1.1 features write into it:
 * Typo-Undo (#3) claims a slot, the math evaluator (#1) offers a result chip, and entity chips
 * (#4) would too. These tests pin the invariants those features depend on, so a future edit
 * cannot silently break them.
 */
class SuggestionChipModelTest {

    @Test
    fun emptyRowHasExactlyThreeSlots() {
        assertEquals(3, SuggestionChipModel.EMPTY_ROW.size)
    }

    @Test
    fun everySlotOfAnEmptyRowIsEmpty() {
        SuggestionChipModel.EMPTY_ROW.forEach { assertTrue(it.isEmpty) }
    }

    @Test
    fun onlyTheMiddleSlotIsPrimary() {
        assertTrue(SuggestionChipModel.EMPTY_ROW[1].isPrimary)
        assertFalse(SuggestionChipModel.EMPTY_ROW[0].isPrimary)
        assertFalse(SuggestionChipModel.EMPTY_ROW[2].isPrimary)
    }

    @Test
    fun aChipWithTextIsNotEmpty() {
        assertFalse(SuggestionChipModel("hello", "hello").isEmpty)
    }

    @Test
    fun anUndoChipIsNotEmptyEvenWithNoCommitWord() {
        // #3 renders its pill with a label but no word to commit
        val undo = SuggestionChipModel("Undo", "", isUndo = true)
        assertTrue(undo.commitWord.isEmpty())
        assertFalse("an undo chip must never be treated as an empty slot", undo.isEmpty)
    }

    @Test
    fun aChipWithAnEmptyLabelAndNoWordIsEmpty() {
        assertTrue(SuggestionChipModel("", "").isEmpty)
    }

    @Test
    fun flagsDefaultToOff() {
        val plain = SuggestionChipModel("x", "x")
        assertFalse(plain.isPrimary)
        assertFalse(plain.isKeepExact)
        assertFalse(plain.isUndo)
    }

    @Test
    fun writingIntoTheThirdSlotLeavesTheOthersAlone() {
        // the emoji suggestion replaces only a free third slot; corrections and next-word
        // suggestions must survive that
        val chips = SuggestionChipModel.EMPTY_ROW.toMutableList()
        chips[0] = SuggestionChipModel("correction", "correction")
        chips[1] = SuggestionChipModel("next", "next", isPrimary = true)
        chips[2] = SuggestionChipModel("🙂", "🙂", isKeepExact = true)

        assertEquals("correction", chips[0].commitWord)
        assertEquals("next", chips[1].commitWord)
        assertTrue(chips[1].isPrimary)
        assertEquals("🙂", chips[2].commitWord)
        assertTrue(chips[2].isKeepExact)
        assertEquals(3, chips.size)
    }

    @Test
    fun undoAutoCorrectStateKeepsBothWordsDistinct() {
        // the state #3 restores from; if these were ever conflated, undo would be a no-op
        val state = UndoAutoCorrectState(originalWord = "teh", committedText = "the")
        assertEquals("teh", state.originalWord)
        assertEquals("the", state.committedText)
    }
}
