package com.genz.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the mod-facing event privacy policy.
 *
 * A plugin that receives key events can reconstruct what was typed, so in a private session those
 * events must not be delivered. These cases pin which events count as content and confirm the
 * decision for every privacy combination.
 */
class PluginEventPolicyTest {

    private val plain = EditorFieldSpecs.of(InputType.TYPE_CLASS_TEXT, 0, false)
    private val password = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0, false
    )
    private val noLearning = EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false
    )

    private val public = PrivacyState.derive(false, plain)
    private val manualOn = PrivacyState.derive(true, plain)
    private val passwordOn = PrivacyState.derive(false, password)
    private val learningOn = PrivacyState.derive(false, noLearning)
    private val allOn = PrivacyState.derive(true, EditorFieldSpecs.of(
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, false
    ))

    private val privateStates get() = listOf(manualOn, passwordOn, learningOn, allOn)

    // ---------------------------------------------------------------- classification

    @Test
    fun keyPressEventsCarryContent() {
        assertTrue(PluginEventPolicy.carriesTypedContent("press"))
        assertTrue(PluginEventPolicy.carriesTypedContent("key"))
    }

    @Test
    fun textAndWordEventsCarryContent() {
        listOf("word", "suggest", "autocorrect", "glide", "voice", "pick", "start", "clipboard")
            .forEach { assertTrue("$it carries content", PluginEventPolicy.carriesTypedContent(it)) }
    }

    @Test
    fun touchAndCursorPositionsCarryContent() {
        // coordinates on a keyboard are the typed text; cursor offsets reveal its length
        assertTrue(PluginEventPolicy.carriesTypedContent("pointer"))
        assertTrue(PluginEventPolicy.carriesTypedContent("selection"))
    }

    @Test
    fun nonContentEventsAreNotClassifiedAsContent() {
        listOf("panel", "shift", "device", "finish", "show", "hide", "language", "gyro", "tilt", "audio")
            .forEach { assertFalse("$it must not be treated as content", PluginEventPolicy.carriesTypedContent(it)) }
    }

    @Test
    fun anUnknownEventIsNotTreatedAsContent() {
        // failing open here is deliberate: an unrecognised event is more likely a UI signal than
        // typed text, and the permission map still gates anything it declares
        assertFalse(PluginEventPolicy.carriesTypedContent("some_future_event"))
    }

    // ---------------------------------------------------------------- the decision

    @Test
    fun keyPressesAreDeliveredInANormalSession() {
        assertTrue(PluginEventPolicy.allows("press", public))
        assertTrue(PluginEventPolicy.allows("key", public))
    }

    @Test
    fun keyPressesAreBlockedInManualIncognito() {
        assertFalse(PluginEventPolicy.allows("press", manualOn))
        assertFalse(PluginEventPolicy.allows("key", manualOn))
    }

    @Test
    fun keyPressesAreBlockedInAPasswordField() {
        assertFalse(PluginEventPolicy.allows("press", passwordOn))
    }

    @Test
    fun keyPressesAreBlockedWhenTheEditorAsksNotToBeLearnedFrom() {
        assertFalse(PluginEventPolicy.allows("press", learningOn))
    }

    @Test
    fun everyContentEventIsBlockedInEveryPrivateState() {
        PluginEventPolicy.CONTENT_EVENTS.forEach { event ->
            privateStates.forEach { state ->
                assertFalse(
                    "$event must be blocked for $state",
                    PluginEventPolicy.allows(event, state)
                )
            }
        }
    }

    @Test
    fun everyContentEventIsAllowedInAPublicSession() {
        PluginEventPolicy.CONTENT_EVENTS.forEach { event ->
            assertTrue("$event must be allowed when public", PluginEventPolicy.allows(event, public))
        }
    }

    @Test
    fun nonContentEventsSurvivePrivacy() {
        // mods that draw effects without reading text must keep working in incognito
        listOf("panel", "shift", "device", "finish", "show", "hide", "language", "gyro", "tilt", "audio")
            .forEach { event ->
                privateStates.forEach { state ->
                    assertTrue("$event should still be delivered for $state", PluginEventPolicy.allows(event, state))
                }
            }
    }

    @Test
    fun theRuleIsThePrivacyPredicateAppliedToContentEvents() {
        // no separate notion of privacy: the same isIncognito answer drives it
        listOf(public, manualOn, passwordOn, learningOn, allOn).forEach { state ->
            PluginEventPolicy.CONTENT_EVENTS.forEach { event ->
                assertTrue(
                    PluginEventPolicy.allows(event, state) == !state.isIncognito
                )
            }
        }
    }

    // ---------------------------------------------------------------- coverage guard

    @Test
    fun theContentSetStillCoversTheEventsTheKeyboardEmits() {
        // guard against silent removal: these are the events that were audited as carrying content
        val audited = setOf(
            "press", "key", "word", "suggest", "start", "selection",
            "pick", "voice", "autocorrect", "glide", "pointer", "clipboard"
        )
        assertTrue(
            "a content event was removed from the policy",
            PluginEventPolicy.CONTENT_EVENTS.containsAll(audited)
        )
    }
}
