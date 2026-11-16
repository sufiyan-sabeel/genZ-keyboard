package com.genz.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the single privacy derivation behind "Zero-Trace Auto-Incognito" (#13).
 *
 * The point of these tests is combinations, not isolated booleans: the bug this feature exists to
 * prevent is a reason that is honoured on one path and forgotten on another, so the matrix below
 * covers every pairing of the manual switch, a password field and the editor's
 * no-personalised-learning request.
 */
class PrivacyStateTest {

    private val plain = EditorFieldSpecs.of(InputType.TYPE_CLASS_TEXT, 0, correctEverywhere = false)
    private val password = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0, false
    )
    private val noLearning = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false
    )
    private val emailNoLearning = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false
    )

    private fun derive(manual: Boolean, field: EditorFieldSpec?) = PrivacyState.derive(manual, field)

    // ---------------------------------------------------------------- the matrix

    @Test
    fun manualOffWithOrdinaryFieldIsNotPrivate() {
        val s = derive(false, plain)
        assertFalse(s.isIncognito)
        assertTrue(s.reasons.isEmpty())
        assertFalse(s.forcedByField)
    }

    @Test
    fun manualOnWithOrdinaryFieldIsPrivateForTheManualReason() {
        val s = derive(true, plain)
        assertTrue(s.isIncognito)
        assertEquals(setOf(IncognitoReason.MANUAL), s.reasons)
        assertFalse("the field did not force this", s.forcedByField)
    }

    @Test
    fun passwordFieldIsPrivateWithoutTheManualSwitch() {
        val s = derive(false, password)
        assertTrue(s.isIncognito)
        assertEquals(setOf(IncognitoReason.PASSWORD_FIELD), s.reasons)
        assertTrue(s.forcedByField)
    }

    @Test
    fun passwordFieldPlusManualKeepsBothReasons() {
        val s = derive(true, password)
        assertTrue(s.isIncognito)
        assertEquals(
            setOf(IncognitoReason.MANUAL, IncognitoReason.PASSWORD_FIELD),
            s.reasons
        )
    }

    @Test
    fun noPersonalizedLearningIsHonouredOnItsOwn() {
        val s = derive(false, noLearning)
        assertTrue(s.isIncognito)
        assertEquals(setOf(IncognitoReason.NO_PERSONALIZED_LEARNING), s.reasons)
        assertTrue(s.forcedByField)
    }

    @Test
    fun noPersonalizedLearningPlusManualKeepsBothReasons() {
        val s = derive(true, noLearning)
        assertEquals(
            setOf(IncognitoReason.MANUAL, IncognitoReason.NO_PERSONALIZED_LEARNING),
            s.reasons
        )
    }

    @Test
    fun passwordAndLearningRequestTogetherAreBothRecorded() {
        // an editor can ask for both at once; nothing may be dropped
        val both = EditorFieldSpecs.of(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            false
        )
        val s = derive(false, both)
        assertEquals(
            setOf(IncognitoReason.PASSWORD_FIELD, IncognitoReason.NO_PERSONALIZED_LEARNING),
            s.reasons
        )
    }

    @Test
    fun allThreeReasonsCanBeActiveAtOnce() {
        val both = EditorFieldSpecs.of(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
            false
        )
        val s = derive(true, both)
        assertEquals(3, s.reasons.size)
        assertTrue(s.has(IncognitoReason.MANUAL))
        assertTrue(s.has(IncognitoReason.PASSWORD_FIELD))
        assertTrue(s.has(IncognitoReason.NO_PERSONALIZED_LEARNING))
    }

    // ---------------------------------------------------------------- no editor attached

    @Test
    fun noEditorAndManualOffIsNotPrivate() {
        assertFalse(derive(false, null).isIncognito)
    }

    @Test
    fun noEditorButManualOnIsPrivate() {
        val s = derive(true, null)
        assertTrue(s.isIncognito)
        assertEquals(setOf(IncognitoReason.MANUAL), s.reasons)
    }

    // ---------------------------------------------------------------- transitions

    @Test
    fun movingFromOrdinaryToPasswordFieldAndBackClearsTheAutomaticReason() {
        // ordinary field: not private
        assertFalse(derive(false, plain).isIncognito)
        // into a password field: private because of the field
        assertTrue(derive(false, password).isIncognito)
        // back to an ordinary field: automatic reason gone
        val back = derive(false, plain)
        assertFalse("the password reason must not outlive the field", back.isIncognito)
        assertTrue(back.reasons.isEmpty())
    }

    @Test
    fun theManualSwitchSurvivesAMoveBetweenFields() {
        assertTrue(derive(true, plain).has(IncognitoReason.MANUAL))
        assertTrue(derive(true, password).has(IncognitoReason.MANUAL))
        assertTrue(derive(true, noLearning).has(IncognitoReason.MANUAL))
        assertTrue(derive(true, null).has(IncognitoReason.MANUAL))
    }

    @Test
    fun turningTheManualSwitchOffInAPasswordFieldStaysPrivate() {
        // the dangerous case: the user taps "off" but the field still demands privacy
        val s = derive(false, password)
        assertTrue("a password field must stay private", s.isIncognito)
        assertFalse(s.has(IncognitoReason.MANUAL))
    }

    @Test
    fun turningTheManualSwitchOffInAnOrdinaryFieldIsNoLongerPrivate() {
        assertFalse(derive(false, plain).isIncognito)
    }

    // ---------------------------------------------------------------- consistency

    @Test
    fun isIncognitoAgreesWithTheFieldPredicate() {
        // two predicates exist — EditorFieldSpec.isPrivate and PrivacyState.isIncognito — and
        // they must never disagree for the same field when the switch is off
        listOf(plain, password, noLearning, emailNoLearning).forEach { spec ->
            assertEquals(
                "mismatch for spec $spec",
                spec.isPrivate,
                PrivacyState.derive(false, spec).isIncognito
            )
        }
    }

    @Test
    fun reasonsContainNoDuplicates() {
        val s = derive(true, password)
        assertEquals(s.reasons.size, s.reasons.toList().size)
    }

    @Test
    fun hasReportsExactlyTheActiveReasons() {
        val s = derive(false, password)
        assertTrue(s.has(IncognitoReason.PASSWORD_FIELD))
        assertFalse(s.has(IncognitoReason.MANUAL))
        assertFalse(s.has(IncognitoReason.NO_PERSONALIZED_LEARNING))
    }

    // ---------------------------------------------------------------- hot path

    @Test
    fun theCommonCaseReturnsTheSharedInstance() {
        // the typing path must not allocate when nothing applies
        assertSame(PrivacyState.OFF, derive(false, plain))
        assertSame(PrivacyState.OFF, derive(false, null))
        assertSame(PrivacyState.OFF, derive(false, emailNoLearning.copy(noPersonalizedLearning = false)))
    }

    @Test
    fun aPrivateStateIsNotTheSharedInstance() {
        assertFalse(PrivacyState.OFF === derive(true, plain))
    }

    @Test
    fun equalInputsProduceEqualStates() {
        // the service caches on its inputs, so equality has to behave
        assertEquals(derive(true, password), derive(true, password))
        assertEquals(derive(true, password).hashCode(), derive(true, password).hashCode())
    }
}
