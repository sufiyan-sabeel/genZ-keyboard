package com.genz.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the listener-order defect found in the privacy milestone audit.
 *
 * The bug: the service applied a privacy transition by reading the new incognito value back from
 * `KeyboardPrefs`, whose cache is refreshed by a *separate* listener. `SharedPreferences.edit`
 * uses `apply()`, and listener notification order is unspecified, so the keyboard's listener could
 * run first and read the **old** value — the transition then looked like "no change" and nothing
 * was applied, leaving `ScriptHost.privateMode` stale until the next keystroke.
 *
 * These tests pin the fix at the level that can be tested without Android: the transition must be
 * derived from the value it is **given**, never from a source that might lag behind it.
 */
class IncognitoTransitionTest {

    private val plain = EditorFieldSpecs.of(InputType.TYPE_CLASS_TEXT, 0, false)
    private val password = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0, false
    )

    private val off = PrivacyState.derive(false, plain)

    private fun scriptPrivate(result: IncognitoTransition.Result): Boolean? =
        result.effects.filterIsInstance<PrivacyEffect.SetScriptPrivate>().singleOrNull()?.value

    // ---------------------------------------------------------------- the defect

    @Test
    fun theSuppliedValueWinsEvenWhenACacheStillHoldsTheOldOne() {
        // `staleCache` models what KeyboardPrefs.current() returns when its listener has not run
        // yet. The transition must ignore it and use the value it was handed.
        val staleCache = false
        val previous = PrivacyState.derive(staleCache, plain)

        val result = IncognitoTransition.apply(manual = true, spec = plain, previous = previous)

        assertTrue("privacy must follow the supplied value", result.state.isIncognito)
        assertEquals("plugins must be switched to private immediately", true, scriptPrivate(result))
        assertTrue(result.changed)
    }

    @Test
    fun derivingFromTheStaleValueWouldHaveAppliedNothing() {
        // this is the shape of the original bug: old value in, old value out, no effects, no
        // transition — and plugins keep reading text until something else notices
        val stale = PrivacyState.derive(false, plain)
        assertTrue(
            "sanity: a stale-to-stale comparison produces no effects, which is why the bug was silent",
            PrivacyTransitions.effectsOf(stale, stale).isEmpty()
        )
        assertTrue(
            "the supplied value must produce effects where the stale path produced none",
            IncognitoTransition.apply(true, plain, stale).changed
        )
    }

    @Test
    fun turningPrivacyOffIsAlsoDrivenByTheSuppliedValue() {
        val private = PrivacyState.derive(true, plain)
        val result = IncognitoTransition.apply(manual = false, spec = plain, previous = private)
        assertFalse(result.state.isIncognito)
        assertEquals(false, scriptPrivate(result))
    }

    // ---------------------------------------------------------------- no spurious work

    @Test
    fun applyingTheSameValueTwiceAppliesNothingTheSecondTime() {
        // both the toolbar action and the preference listener can fire for one toggle
        val first = IncognitoTransition.apply(true, plain, off)
        assertTrue(first.changed)
        val second = IncognitoTransition.apply(true, plain, first.state)
        assertFalse("the second call must be a no-op", second.changed)
        assertTrue(second.effects.isEmpty())
    }

    @Test
    fun theFieldIsPartOfTheDecision() {
        // entering a password field forces privacy without any preference change
        val result = IncognitoTransition.apply(manual = false, spec = password, previous = off)
        assertTrue(result.state.isIncognito)
        assertTrue(result.changed)
        assertEquals(true, scriptPrivate(result))
    }

    @Test
    fun movingBetweenTwoPrivateFieldsIsNotAChange() {
        val inPassword = IncognitoTransition.apply(false, password, off)
        val toManual = IncognitoTransition.apply(true, password, inPassword.state)
        assertFalse("still private, nothing to tear down", toManual.changed)
    }

    @Test
    fun theResultingStateMatchesPlainDerivation() {
        // the transition must not invent a different privacy answer from PrivacyState.derive
        listOf(false to plain, true to plain, false to password, true to password).forEach { (manual, spec) ->
            assertEquals(
                PrivacyState.derive(manual, spec),
                IncognitoTransition.apply(manual, spec, off).state
            )
        }
    }

    @Test
    fun aTransitionToPrivateAlwaysCarriesAllThreeEffects() {
        val result = IncognitoTransition.apply(true, plain, off)
        assertEquals(3, result.effects.size)
        assertTrue(result.effects.contains(PrivacyEffect.DropInlineSuggestions))
        assertTrue(result.effects.contains(PrivacyEffect.InvalidatePendingInline))
    }
}
