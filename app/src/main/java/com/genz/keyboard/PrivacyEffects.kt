package com.genz.keyboard

/**
 * Something that must happen when the effective privacy state changes.
 *
 * The point of naming these is that a privacy change is not one write: it has to stop plugins
 * seeing text, take already-rendered autofill credentials off screen, and invalidate results that
 * are still in flight. Doing that at each call site is how the previous audit's stale-plugin bug
 * happened. One transition function produces the list; the service just applies it.
 */
sealed interface PrivacyEffect {
    /** Tell the script host whether text may be read. */
    class SetScriptPrivate(val value: Boolean) : PrivacyEffect

    /** Remove autofill suggestions that are already on screen. */
    object DropInlineSuggestions : PrivacyEffect

    /** Make any response still inflating lose its generation. */
    object InvalidatePendingInline : PrivacyEffect
}

/** Derives the effects of a privacy transition. Pure, so the decision can be tested. */
object PrivacyTransitions {

    /**
     * @param previous the state before the change, or null if there was none
     * @param next the state after the change
     */
    fun effectsOf(previous: PrivacyState?, next: PrivacyState): List<PrivacyEffect> {
        // Only a change in the *effective* answer matters. Gaining a second reason, or swapping
        // one reason for another, changes nothing observable.
        if (previous != null && previous.isIncognito == next.isIncognito) return emptyList()
        return listOf(
            PrivacyEffect.SetScriptPrivate(next.isIncognito),
            // Both directions clear: going private must take credentials off screen now, and
            // leaving a private field must not surface a response produced while it was private.
            PrivacyEffect.DropInlineSuggestions,
            PrivacyEffect.InvalidatePendingInline
        )
    }
}

/**
 * The persistent writes that must not happen in a private session.
 *
 * Implemented by the service; faked in tests so the routing can be observed without Android.
 */
interface PrivacySensitiveWrites {
    /** Remember that a word should not be corrected again (written to the dictionary). */
    fun rejectCorrection(word: String)

    /** Remember that an emoji was used (written to recents). */
    fun rememberEmoji(emoji: String)
}

/**
 * Routes privacy-sensitive writes through the live privacy state.
 *
 * Every call reads the current state rather than a captured one, so a switch flipped mid-session
 * takes effect on the very next write. Call sites cannot forget the check because they have no
 * other way to reach the write.
 */
class PrivacyWriteRouter(
    private val privacy: () -> PrivacyState,
    private val writes: PrivacySensitiveWrites
) {
    fun rejectCorrection(word: String) {
        if (!privacy().isIncognito) writes.rejectCorrection(word)
    }

    fun rememberEmoji(emoji: String) {
        if (!privacy().isIncognito) writes.rememberEmoji(emoji)
    }
}
