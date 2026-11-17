package com.genz.keyboard

/**
 * Size bounds for an inline suggestion chip, in pixels, derived from a density.
 */
data class InlineBounds(
    val minWidth: Int,
    val minHeight: Int,
    val maxWidth: Int,
    val maxHeight: Int
) {
    /** The platform rejects a presentation spec whose minimum exceeds its maximum. */
    val isValid: Boolean
        get() = minWidth > 0 && minHeight > 0 && maxWidth >= minWidth && maxHeight >= minHeight
}

/**
 * Rules for Android 11+ inline autofill, kept out of the service so they can be tested on the JVM.
 *
 * The keyboard already built a full [android.view.inputmethod.InlineSuggestionsRequest]; what it
 * did not do was decide *whether* to, bound the sizes in one place, or reject responses that no
 * longer belong to the current field. Those decisions live here.
 */
object InlineAutofillPolicy {

    /**
     * Chip bounds in dp. The maximum width is deliberately narrow: the chip sits in the gap
     * between the menu buttons and the microphone, and a wider suggestion would be clipped.
     */
    const val MIN_WIDTH_DP = 64f
    const val MIN_HEIGHT_DP = 34f
    const val MAX_WIDTH_DP = 200f
    const val MAX_HEIGHT_DP = 38f

    /** How many suggestions to ask the autofill service for. */
    const val MAX_SUGGESTIONS = 6

    fun bounds(density: Float): InlineBounds {
        val d = if (density > 0f) density else 1f
        return InlineBounds(
            minWidth = (MIN_WIDTH_DP * d).toInt(),
            minHeight = (MIN_HEIGHT_DP * d).toInt(),
            maxWidth = (MAX_WIDTH_DP * d).toInt(),
            maxHeight = (MAX_HEIGHT_DP * d).toInt()
        )
    }

    /**
     * Inline autofill may run only when the keyboard is not in a private session.
     *
     * Autofill suggestions are stored credentials — the single most sensitive thing the keyboard
     * could put on screen. In a password field or in incognito the keyboard must not even ask for
     * them, so this is checked before the request is built and again before a response is shown.
     */
    fun isAllowed(privacy: PrivacyState): Boolean = !privacy.isIncognito

    /**
     * Whether the inline strip may be displayed, from the two signals the UI actually has.
     *
     * Kept here so the rendering rule and the request/response rule cannot drift apart: the strip
     * must disappear the moment either signal says private, not at the next field change.
     */
    fun mayShow(manualIncognito: Boolean, privateField: Boolean): Boolean =
        !manualIncognito && !privateField

    /**
     * Whether an inflated response may be published.
     *
     * Two independent things must hold: it must still be the newest response, and the session must
     * not have become private while the views were inflating. Either alone is not enough — a fresh
     * response in a private session, or a stale response in a public one, must both be dropped.
     */
    fun mayPublish(generation: Int, currentGeneration: Int, privacy: PrivacyState): Boolean =
        generation == currentGeneration && isAllowed(privacy)

    /** Clamp a requested suggestion count into the range the keyboard will render. */
    fun suggestionCount(requested: Int): Int = requested.coerceIn(0, MAX_SUGGESTIONS)

    /** A response is worth showing only when it actually carries suggestions. */
    fun accepts(count: Int): Boolean = count > 0
}
