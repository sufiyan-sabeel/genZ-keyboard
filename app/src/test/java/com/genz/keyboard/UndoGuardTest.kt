package com.genz.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the rules that decide whether a Typo-Undo may be applied (#3).
 *
 * The feature is not "offer an undo" — that already existed. It is "never delete text on an
 * assumption", so most of these cases are the ones where the undo must be refused.
 */
class UndoGuardTest {

    private val field = "com.example.app/42"
    private val now = 1_000_000L

    private fun state(
        original: String = "teh",
        committed: String = "the ",
        fieldKey: String = field,
        expires: Long = now + UndoGuard.TTL_MILLIS
    ) = UndoAutoCorrectState(original, committed, fieldKey, expires)

    // ---------------------------------------------------------------- liveness

    @Test
    fun aFreshOfferIsLive() {
        assertTrue(UndoGuard.isLive(state(), field, now))
    }

    @Test
    fun nullStateIsNeverLive() {
        assertFalse(UndoGuard.isLive(null, field, now))
    }

    @Test
    fun anExpiredOfferIsNotLive() {
        assertFalse(UndoGuard.isLive(state(), field, now + UndoGuard.TTL_MILLIS + 1))
    }

    @Test
    fun theDeadlineItselfIsStillLive() {
        // inclusive boundary, so a slow tap at the very edge is not silently dropped
        assertTrue(UndoGuard.isLive(state(), field, now + UndoGuard.TTL_MILLIS))
    }

    @Test
    fun anOfferFromAnotherFieldIsNotLive() {
        assertFalse(UndoGuard.isLive(state(), "com.other.app/7", now))
    }

    @Test
    fun anOfferWithNoFieldIdentityIsNotLiveInARealField() {
        // default-constructed state (fieldKey "") must not be applied in a real field
        assertFalse(UndoGuard.isLive(state(fieldKey = ""), field, now))
    }

    @Test
    fun anOfferWithNothingToRestoreIsNotLive() {
        assertFalse(UndoGuard.isLive(state(original = ""), field, now))
        assertFalse(UndoGuard.isLive(state(committed = ""), field, now))
    }

    // ---------------------------------------------------------------- editor match

    @Test
    fun theEditorEndingWithTheCommittedTextMatches() {
        assertTrue(UndoGuard.matchesEditor(state(), "I typed the "))
    }

    @Test
    fun anExactEditorContentMatches() {
        assertTrue(UndoGuard.matchesEditor(state(), "the "))
    }

    @Test
    fun aDifferentTailDoesNotMatch() {
        // the user typed after the correction
        assertFalse(UndoGuard.matchesEditor(state(), "I typed thex"))
    }

    @Test
    fun anEditorShorterThanTheCommitDoesNotMatch() {
        assertFalse(UndoGuard.matchesEditor(state(), "the"))
    }

    @Test
    fun aNullEditorDoesNotMatch() {
        assertFalse(UndoGuard.matchesEditor(state(), null))
    }

    @Test
    fun anEmptyCommitNeverMatches() {
        assertFalse(UndoGuard.matchesEditor(state(committed = ""), "anything"))
    }

    @Test
    fun theCommittedTextMustBeAContiguousSuffix() {
        // "the " appears in the middle but not at the end
        assertFalse(UndoGuard.matchesEditor(state(), "the cat sat"))
    }

    // ---------------------------------------------------------------- rapid typing

    @Test
    fun aSecondCorrectionInvalidatesTheFirst() {
        // first correction "teh" -> "the "; user keeps typing so the field now ends "then "
        val first = state(original = "teh", committed = "the ")
        assertFalse("the first undo must not fire once the text moved on",
            UndoGuard.matchesEditor(first, "I typed then "))
    }

    @Test
    fun theNewestCorrectionIsTheOneThatMatches() {
        val second = state(original = "then", committed = "then ")
        assertTrue(UndoGuard.matchesEditor(second, "I typed then "))
    }

    @Test
    fun anUndoOfAnUndoDoesNotMatchTheRestoredText() {
        // after undo the field ends with the original word plus a space, not the correction
        val s = state(original = "teh", committed = "the ")
        assertFalse(UndoGuard.matchesEditor(s, "I typed teh "))
    }

    // ---------------------------------------------------------------- whole decision

    @Test
    fun anOfferIsApplicableOnlyWhenLiveAndMatching() {
        val s = state()
        assertTrue(UndoGuard.isLive(s, field, now) && UndoGuard.matchesEditor(s, "typed the "))
        assertFalse(UndoGuard.isLive(s, field, now + 99_999) && UndoGuard.matchesEditor(s, "typed the "))
        assertFalse(UndoGuard.isLive(s, field, now) && UndoGuard.matchesEditor(s, "typed thex"))
    }

    @Test
    fun theTimeToLiveIsBoundedAndPositive() {
        // a long-lived undo is a stale undo; a zero one is not a feature
        assertTrue(UndoGuard.TTL_MILLIS in 1..15_000)
    }

    // ---------------------------------------------------------------- state defaults

    @Test
    fun theNewFieldsDefaultSoOldCallSitesStillCompile() {
        val bare = UndoAutoCorrectState("teh", "the ")
        assertTrue(bare.fieldKey.isEmpty())
        assertTrue(bare.expiresAtMillis == 0L)
        // and such a state is correctly refused rather than applied
        assertFalse(UndoGuard.isLive(bare, field, now))
    }
}
