package com.genz.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the privacy transition effects and the write router.
 *
 * These cover what the previous audit could only assert by reading the code: that a privacy change
 * produces the right observable consequences, and that the persistent writes actually stop.
 */
class PrivacyEffectsTest {

    private val plain = EditorFieldSpecs.of(InputType.TYPE_CLASS_TEXT, 0, false)
    private val password = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0, false
    )
    private val noLearning = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false
    )

    private fun state(manual: Boolean, field: EditorFieldSpec? = plain) =
        PrivacyState.derive(manual, field)

    private val off = state(false)
    private val manualOn = state(true)
    private val passwordOn = state(false, password)
    private val learningOn = state(false, noLearning)

    // ---------------------------------------------------------------- transitions

    private fun scriptPrivate(effects: List<PrivacyEffect>): Boolean? =
        effects.filterIsInstance<PrivacyEffect.SetScriptPrivate>().singleOrNull()?.value

    @Test
    fun anUnknownPreviousStateAppliesTheEffectsRatherThanAssumingPublic() {
        // null means "we do not know what came before". Applying the effects is idempotent —
        // clearing an empty strip, bumping a counter, setting a boolean — and it is safer than
        // assuming the session was public, which would leave plugins able to read text if the
        // service ever starts in a private field. (This test originally asserted the opposite;
        // the assertion was wrong, not the behaviour.)
        val private = PrivacyTransitions.effectsOf(null, manualOn)
        assertEquals(3, private.size)
        assertEquals(true, scriptPrivate(private))

        val public = PrivacyTransitions.effectsOf(null, off)
        assertEquals(3, public.size)
        assertEquals(false, scriptPrivate(public))
    }

    @Test
    fun stayingPublicProducesNoEffects() {
        assertTrue(PrivacyTransitions.effectsOf(off, off).isEmpty())
    }

    @Test
    fun becomingPrivateStopsScriptAccess() {
        val effects = PrivacyTransitions.effectsOf(off, manualOn)
        assertEquals(true, scriptPrivate(effects))
    }

    @Test
    fun leavingPrivacyRestoresScriptAccess() {
        val effects = PrivacyTransitions.effectsOf(manualOn, off)
        assertEquals(false, scriptPrivate(effects))
    }

    @Test
    fun anyPrivacyChangeDropsOnScreenSuggestionsAndInvalidatesPendingOnes() {
        listOf(off to manualOn, manualOn to off).forEach { (from, to) ->
            val effects = PrivacyTransitions.effectsOf(from, to)
            assertTrue("must drop rendered suggestions", PrivacyEffect.DropInlineSuggestions in effects)
            assertTrue("must invalidate in-flight responses", PrivacyEffect.InvalidatePendingInline in effects)
        }
    }

    @Test
    fun swappingOnePrivacyReasonForAnotherIsNotAChange() {
        // already private for a password; the user also flips the switch. Nothing observable
        // changes, so nothing should be torn down — this is the case a naive "reasons differ"
        // comparison would get wrong.
        assertTrue(PrivacyTransitions.effectsOf(passwordOn, state(true, password)).isEmpty())
        assertTrue(PrivacyTransitions.effectsOf(manualOn, passwordOn).isEmpty())
        assertTrue(PrivacyTransitions.effectsOf(passwordOn, learningOn).isEmpty())
    }

    @Test
    fun aTransitionCarriesExactlyThreeEffects() {
        assertEquals(3, PrivacyTransitions.effectsOf(off, passwordOn).size)
    }

    // ---------------------------------------------------------------- write router

    /** Records what actually reached the persistent layer. */
    private class RecordingSink : PrivacySensitiveWrites {
        val corrections = mutableListOf<String>()
        val emojis = mutableListOf<String>()
        override fun rejectCorrection(word: String) { corrections += word }
        override fun rememberEmoji(emoji: String) { emojis += emoji }
    }

    private fun router(sink: RecordingSink, privacy: () -> PrivacyState) =
        PrivacyWriteRouter(privacy, sink)

    @Test
    fun aPublicSessionWritesNormally() {
        val sink = RecordingSink()
        val r = router(sink) { off }
        r.rejectCorrection("teh")
        r.rememberEmoji("🙂")
        assertEquals(listOf("teh"), sink.corrections)
        assertEquals(listOf("🙂"), sink.emojis)
    }

    @Test
    fun aPrivateSessionWritesNothing() {
        val sink = RecordingSink()
        val r = router(sink) { manualOn }
        r.rejectCorrection("teh")
        r.rememberEmoji("🙂")
        assertTrue(sink.corrections.isEmpty())
        assertTrue(sink.emojis.isEmpty())
    }

    @Test
    fun aPasswordFieldWritesNothing() {
        val sink = RecordingSink()
        val r = router(sink) { passwordOn }
        r.rejectCorrection("secret")
        assertTrue(sink.corrections.isEmpty())
    }

    @Test
    fun theLearningRequestAloneAlsoBlocksWrites() {
        val sink = RecordingSink()
        val r = router(sink) { learningOn }
        r.rememberEmoji("🙂")
        assertTrue(sink.emojis.isEmpty())
    }

    @Test
    fun flippingTheSwitchTakesEffectOnTheVeryNextWrite() {
        // proves the router reads live state rather than capturing it at construction
        val sink = RecordingSink()
        var current = off
        val r = router(sink) { current }

        r.rejectCorrection("one")
        current = manualOn
        r.rejectCorrection("two")
        current = off
        r.rejectCorrection("three")

        assertEquals("only the writes made while public may persist", listOf("one", "three"), sink.corrections)
    }

    @Test
    fun bothWritesAreGatedByTheSameDecision() {
        val sink = RecordingSink()
        var current = off
        val r = router(sink) { current }
        r.rejectCorrection("a"); r.rememberEmoji("x")
        current = passwordOn
        r.rejectCorrection("b"); r.rememberEmoji("y")
        assertEquals(1, sink.corrections.size)
        assertEquals(1, sink.emojis.size)
    }

    @Test
    fun theRouterNeverFabricatesAWrite() {
        // a gated call must not be silently rewritten into some other persisted value
        val sink = RecordingSink()
        val r = router(sink) { manualOn }
        r.rejectCorrection("")
        r.rememberEmoji("")
        assertTrue(sink.corrections.isEmpty())
        assertFalse(sink.corrections.any { it.isNotEmpty() })
    }
}
