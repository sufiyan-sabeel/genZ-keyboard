package com.genz.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the editor-field rules that drive suggestions, autocorrect and — once
 * "Zero-Trace Auto-Incognito" lands — privacy.
 *
 * These run on the JVM with no device: [EditorFieldSpecs.of] takes two ints and returns a value.
 */
class EditorFieldSpecTest {

    private fun spec(inputType: Int, imeOptions: Int = 0, correctEverywhere: Boolean = false) =
        EditorFieldSpecs.of(inputType, imeOptions, correctEverywhere)

    private val plainText = InputType.TYPE_CLASS_TEXT

    // ---------------------------------------------------------------- canaries
    //
    // If the compile-time constants this module depends on were ever read from a stub jar with
    // wrong values, every other assertion here would pass for the wrong reason. These pin them.

    @Test
    fun constantsHaveTheirDocumentedValues() {
        assertEquals(0x0000000f, InputType.TYPE_MASK_CLASS)
        assertEquals(0x00000ff0, InputType.TYPE_MASK_VARIATION)
        assertEquals(0x00000001, InputType.TYPE_CLASS_TEXT)
        assertEquals(0x00000002, InputType.TYPE_CLASS_NUMBER)
        assertEquals(0x00010000, InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertEquals(0x01000000, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
    }

    // ---------------------------------------------------------------- plain fields

    @Test
    fun plainTextFieldAcceptsSuggestionsAndCorrection() {
        val s = spec(plainText)
        assertTrue(s.isText)
        assertFalse(s.isPassword)
        assertFalse(s.isAddressLike)
        assertTrue(s.acceptsSuggestions)
        assertTrue(s.autoCorrect)
        assertFalse(s.autoCapitalize)
        assertFalse(s.isPrivate)
    }

    @Test
    fun zeroInputTypeIsNotTextAndGetsNoSuggestions() {
        // what an absent or unknown field looks like
        val s = spec(0)
        assertFalse(s.isText)
        assertFalse(s.acceptsSuggestions)
        assertFalse(s.autoCorrect)
        assertFalse(s.isPassword)
    }

    // ---------------------------------------------------------------- passwords

    @Test
    fun allThreeTextPasswordVariationsAreDetected() {
        val variations = listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        variations.forEach { v ->
            val s = spec(plainText or v)
            assertTrue("variation 0x${v.toString(16)} must be a password", s.isPassword)
            assertTrue(s.isPrivate)
            assertFalse("no suggestions in a password field", s.acceptsSuggestions)
            assertFalse("no autocorrect in a password field", s.autoCorrect)
            assertFalse("no autocapitalisation in a password field", s.autoCapitalize)
        }
    }

    @Test
    fun numericPasswordIsDetected() {
        val s = spec(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        assertTrue(s.isPassword)
        assertTrue(s.isPrivate)
        assertFalse(s.acceptsSuggestions)
    }

    @Test
    fun ordinaryNumberFieldIsNotAPassword() {
        val s = spec(InputType.TYPE_CLASS_NUMBER)
        assertFalse(s.isPassword)
        assertFalse(s.acceptsSuggestions)   // suggestions are text-only
    }

    // ---------------------------------------------------------------- addresses

    @Test
    fun addressLikeFieldsGetNoSuggestions() {
        val variations = listOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
        variations.forEach { v ->
            val s = spec(plainText or v)
            assertTrue("0x${v.toString(16)} is address-like", s.isAddressLike)
            assertFalse(s.acceptsSuggestions)
            assertFalse(s.autoCorrect)
        }
    }

    // ---------------------------------------------------------------- no-suggestions flag

    @Test
    fun noSuggestionsFlagSuppressesCorrectionByDefault() {
        val s = spec(plainText or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertTrue(s.noSuggestions)
        assertFalse(s.autoCorrect)
    }

    @Test
    fun correctEverywhereOverridesTheNoSuggestionsFlag() {
        // many chat apps set NO_SUGGESTIONS on ordinary message fields
        val s = spec(plainText or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, correctEverywhere = true)
        assertTrue(s.noSuggestions)
        assertTrue(s.autoCorrect)
    }

    @Test
    fun correctEverywhereCannotOverrideAPasswordField() {
        val s = spec(
            plainText or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            correctEverywhere = true
        )
        assertTrue(s.isPassword)
        assertFalse("a password field must never autocorrect", s.autoCorrect)
    }

    // ---------------------------------------------------------------- capitalisation

    @Test
    fun capitalisationFlagsAreDetected() {
        assertTrue(spec(plainText or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES).autoCapitalize)
        assertTrue(spec(plainText or InputType.TYPE_TEXT_FLAG_CAP_WORDS).autoCapitalize)
        assertTrue(spec(plainText or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS).autoCapitalize)
        assertFalse(spec(plainText).autoCapitalize)
    }

    // ---------------------------------------------------------------- personalised learning

    @Test
    fun noPersonalizedLearningIsReadFromImeOptions() {
        val s = spec(plainText, imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertTrue(s.noPersonalizedLearning)
        assertTrue("learning must stop even in an ordinary field", s.isPrivate)
        assertTrue("the field is still an ordinary text field", s.acceptsSuggestions)
    }

    @Test
    fun noPersonalizedLearningDoesNotAffectFieldType() {
        val plain = spec(plainText)
        val private = spec(plainText, imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertEquals(plain.isText, private.isText)
        assertEquals(plain.isPassword, private.isPassword)
        assertEquals(plain.acceptsSuggestions, private.acceptsSuggestions)
        assertEquals(plain.autoCorrect, private.autoCorrect)
    }

    @Test
    fun passwordIsPrivateEvenWithoutTheFlag() {
        assertTrue(spec(plainText or InputType.TYPE_TEXT_VARIATION_PASSWORD).isPrivate)
    }

    @Test
    fun ordinaryFieldIsNotPrivate() {
        assertFalse(spec(plainText).isPrivate)
    }

    @Test
    fun unrelatedImeOptionsDoNotImplyPrivacy() {
        // e.g. IME_ACTION_SEARCH / IME_FLAG_NO_ENTER_ACTION must not be mistaken for the privacy flag
        val s = spec(plainText, imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        assertFalse(s.noPersonalizedLearning)
        assertFalse(s.isPrivate)
    }

    // ---------------------------------------------------------------- combinations

    @Test
    fun emailFieldWithNoSuggestionsAndThePrivacyFlagIsStillPrivateAndUncorrected() {
        val s = spec(
            plainText or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            correctEverywhere = true
        )
        assertTrue(s.isAddressLike)
        assertTrue(s.noPersonalizedLearning)
        assertTrue(s.isPrivate)
        assertFalse("correctEverywhere must not enable correction in an address field", s.autoCorrect)
    }
}
