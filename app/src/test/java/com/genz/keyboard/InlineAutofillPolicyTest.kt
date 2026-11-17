package com.genz.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the inline-autofill rules (#23).
 *
 * The request/response plumbing needs a device, but every decision that matters — may we ask,
 * are these bounds legal, is this response worth showing, does a private session block it — is
 * pure and is checked here.
 */
class InlineAutofillPolicyTest {

    // ---------------------------------------------------------------- privacy gate

    private fun privacy(manual: Boolean, field: EditorFieldSpec?) = PrivacyState.derive(manual, field)

    private val plain = EditorFieldSpecs.of(InputType.TYPE_CLASS_TEXT, 0, false)
    private val password = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0, false
    )
    private val noLearning = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false
    )

    @Test
    fun inlineAutofillIsAllowedInAnOrdinaryField() {
        assertTrue(InlineAutofillPolicy.isAllowed(privacy(false, plain)))
    }

    @Test
    fun inlineAutofillIsBlockedInAPasswordField() {
        // stored credentials must never be offered here
        assertFalse(InlineAutofillPolicy.isAllowed(privacy(false, password)))
    }

    @Test
    fun inlineAutofillIsBlockedInManualIncognito() {
        assertFalse(InlineAutofillPolicy.isAllowed(privacy(true, plain)))
    }

    @Test
    fun inlineAutofillIsBlockedWhenTheEditorAsksNotToBeLearnedFrom() {
        assertFalse(InlineAutofillPolicy.isAllowed(privacy(false, noLearning)))
    }

    @Test
    fun inlineAutofillIsBlockedWithNoEditorAttachedAndIncognitoOn() {
        assertFalse(InlineAutofillPolicy.isAllowed(privacy(true, null)))
    }

    @Test
    fun inlineAutofillIsAllowedWithNoEditorAttachedAndIncognitoOff() {
        assertTrue(InlineAutofillPolicy.isAllowed(privacy(false, null)))
    }

    @Test
    fun theGateAgreesWithTheSharedPrivacyPredicate() {
        // one predicate decides privacy; inline autofill must not invent its own
        listOf(
            privacy(false, plain), privacy(false, password),
            privacy(true, plain), privacy(false, noLearning)
        ).forEach { p ->
            assertEquals(!p.isIncognito, InlineAutofillPolicy.isAllowed(p))
        }
    }

    // ---------------------------------------------------------------- bounds

    @Test
    fun boundsAreValidAtEveryCommonDensity() {
        // 0.75 (ldpi-ish) through 4.0 (xxxhdpi): the presentation spec must never be inverted
        listOf(0.75f, 1.0f, 1.5f, 2.0f, 2.625f, 3.0f, 3.5f, 4.0f).forEach { d ->
            val b = InlineAutofillPolicy.bounds(d)
            assertTrue("invalid bounds at density $d: $b", b.isValid)
        }
    }

    @Test
    fun boundsScaleWithDensity() {
        val at1 = InlineAutofillPolicy.bounds(1f)
        val at2 = InlineAutofillPolicy.bounds(2f)
        assertEquals(at1.minWidth * 2, at2.minWidth)
        assertEquals(at1.maxHeight * 2, at2.maxHeight)
    }

    @Test
    fun aZeroOrNegativeDensityStillProducesValidBounds() {
        // a broken density must not produce a spec the platform would reject
        listOf(0f, -1f).forEach { d ->
            val b = InlineAutofillPolicy.bounds(d)
            assertTrue("invalid bounds at density $d: $b", b.isValid)
        }
    }

    @Test
    fun theMinimumIsSmallerThanTheMaximum() {
        val b = InlineAutofillPolicy.bounds(3f)
        assertTrue(b.maxWidth > b.minWidth)
        assertTrue(b.maxHeight > b.minHeight)
    }

    @Test
    fun anInvertedBoundIsDetectedAsInvalid() {
        // the validity check has to actually fail, or it proves nothing
        assertFalse(InlineBounds(200, 38, 64, 34).isValid)
        assertFalse(InlineBounds(0, 34, 200, 38).isValid)
        assertFalse(InlineBounds(64, 0, 200, 38).isValid)
    }

    @Test
    fun theChipStaysNarrowerThanItsStatedMaximum() {
        // the chip lives in the gap beside the microphone; a wider one is clipped
        val b = InlineAutofillPolicy.bounds(1f)
        assertEquals(200, b.maxWidth)
        assertEquals(38, b.maxHeight)
        assertEquals(64, b.minWidth)
        assertEquals(34, b.minHeight)
    }

    // ---------------------------------------------------------------- suggestion count

    @Test
    fun theRequestedCountIsClampedToTheMaximum() {
        assertEquals(InlineAutofillPolicy.MAX_SUGGESTIONS,
            InlineAutofillPolicy.suggestionCount(99))
    }

    @Test
    fun aNegativeCountBecomesZero() {
        assertEquals(0, InlineAutofillPolicy.suggestionCount(-5))
    }

    @Test
    fun aReasonableCountIsKept() {
        assertEquals(3, InlineAutofillPolicy.suggestionCount(3))
    }

    @Test
    fun theMaximumIsBounded() {
        assertTrue(InlineAutofillPolicy.MAX_SUGGESTIONS in 1..10)
    }

    // ---------------------------------------------------------------- response acceptance

    @Test
    fun anEmptyResponseIsNotShown() {
        // showing an empty strip would reserve space for nothing
        assertFalse(InlineAutofillPolicy.accepts(0))
    }

    @Test
    fun aNonEmptyResponseIsShown() {
        assertTrue(InlineAutofillPolicy.accepts(1))
        assertTrue(InlineAutofillPolicy.accepts(6))
    }

    // ---------------------------------------------------------------- strip visibility

    @Test
    fun theStripIsHiddenByEitherPrivacySignal() {
        // the UI only has these two signals; either one must hide the credentials
        assertTrue(InlineAutofillPolicy.mayShow(manualIncognito = false, privateField = false))
        assertFalse(InlineAutofillPolicy.mayShow(manualIncognito = true, privateField = false))
        assertFalse(InlineAutofillPolicy.mayShow(manualIncognito = false, privateField = true))
        assertFalse(InlineAutofillPolicy.mayShow(manualIncognito = true, privateField = true))
    }

    @Test
    fun theStripVisibilityRuleAgreesWithTheRequestGate() {
        // both derive from the same privacy idea; they must not diverge
        listOf(
            privacy(false, plain), privacy(false, password),
            privacy(true, plain), privacy(false, noLearning)
        ).forEach { p ->
            assertEquals(
                "gate and strip disagree for $p",
                InlineAutofillPolicy.isAllowed(p),
                InlineAutofillPolicy.mayShow(p.has(IncognitoReason.MANUAL), p.forcedByField)
            )
        }
    }

    // ---------------------------------------------------------------- publish decision

    @Test
    fun theNewestResponseInAPublicSessionMayPublish() {
        assertTrue(InlineAutofillPolicy.mayPublish(4, 4, privacy(false, plain)))
    }

    @Test
    fun aStaleResponseMayNotPublishEvenWhenPublic() {
        // an older response finishing late must not overwrite the newer one
        assertFalse(InlineAutofillPolicy.mayPublish(3, 4, privacy(false, plain)))
    }

    @Test
    fun theNewestResponseMayNotPublishOncePrivate() {
        // inflation is asynchronous: incognito can arrive after the response did
        assertFalse(InlineAutofillPolicy.mayPublish(4, 4, privacy(true, plain)))
        assertFalse(InlineAutofillPolicy.mayPublish(4, 4, privacy(false, password)))
        assertFalse(InlineAutofillPolicy.mayPublish(4, 4, privacy(false, noLearning)))
    }

    @Test
    fun bothConditionsAreRequiredTogether() {
        assertFalse(InlineAutofillPolicy.mayPublish(1, 2, privacy(true, password)))
    }

    @Test
    fun everyReasonBlocksInlineAutofill() {
        // manual + password + learning-request all at once must still be blocked
        val all = PrivacyState.derive(true, EditorFieldSpecs.of(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false
        ))
        assertEquals(3, all.reasons.size)
        assertFalse(InlineAutofillPolicy.isAllowed(all))
    }
}
