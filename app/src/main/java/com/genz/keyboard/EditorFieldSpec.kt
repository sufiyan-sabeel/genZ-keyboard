package com.genz.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * What the current editor field accepts, derived from [EditorInfo] — as plain values.
 *
 * This exists so the field rules can be unit-tested. The rules previously lived inline in
 * `ComposeKeyboardService.computeFlags`, where they needed a live service and an `EditorInfo`
 * object and therefore could never be tested on the JVM. Nothing here touches Android at
 * runtime: the function takes two ints, and every `InputType`/`EditorInfo` value it uses is a
 * compile-time constant, so a plain JVM test can call it.
 *
 * Behaviour is identical to the inline version it replaced; the only addition is
 * [noPersonalizedLearning], which is read but not yet acted on — it is the seam that
 * "Zero-Trace Auto-Incognito" (#13) will consume.
 */
data class EditorFieldSpec(
    /** The field takes free text (`TYPE_CLASS_TEXT`). */
    val isText: Boolean,
    /** Any password variation, text or numeric. */
    val isPassword: Boolean,
    /** Email address, web email or URI. */
    val isAddressLike: Boolean,
    /** The field asked for no suggestions via `TYPE_TEXT_FLAG_NO_SUGGESTIONS`. */
    val noSuggestions: Boolean,
    /** The field accepts suggestions at all. */
    val acceptsSuggestions: Boolean,
    /** Autocorrection is allowed (suggestions on, and the flag respected unless overridden). */
    val autoCorrect: Boolean,
    /** The field asked for automatic capitalisation. */
    val autoCapitalize: Boolean,
    /** The editor asked the IME not to learn from this field (`IME_FLAG_NO_PERSONALIZED_LEARNING`, API 26+). */
    val noPersonalizedLearning: Boolean
) {
    /**
     * True when the keyboard must not learn from, remember, or offer personalised help in this
     * field. Password detection and the editor's explicit request both count.
     *
     * Note: this is what *this keyboard* will do. It is not a statement about the host app, the
     * operating system, or any other input method.
     */
    val isPrivate: Boolean get() = isPassword || noPersonalizedLearning
}

object EditorFieldSpecs {

    /** Password variations, text class. */
    private val TEXT_PASSWORDS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    )

    /** Address-like variations: no suggestions, no autocorrect. */
    private val ADDRESS_LIKE = setOf(
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_URI
    )

    private const val CAP_FLAGS = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
        InputType.TYPE_TEXT_FLAG_CAP_WORDS or
        InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS

    /**
     * @param inputType the editor's `EditorInfo.inputType`
     * @param imeOptions the editor's `EditorInfo.imeOptions`
     * @param correctEverywhere the user setting that autocorrects even in fields that asked for
     *   no suggestions — many chat apps set that flag on ordinary message fields, which used to
     *   make correction work in only some apps.
     */
    fun of(inputType: Int, imeOptions: Int, correctEverywhere: Boolean): EditorFieldSpec {
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val text = cls == InputType.TYPE_CLASS_TEXT

        val password = (text && variation in TEXT_PASSWORDS) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val addressLike = text && variation in ADDRESS_LIKE
        val noSuggestions = (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

        val suggestions = text && !password && !addressLike
        return EditorFieldSpec(
            isText = text,
            isPassword = password,
            isAddressLike = addressLike,
            noSuggestions = noSuggestions,
            acceptsSuggestions = suggestions,
            autoCorrect = suggestions && (!noSuggestions || correctEverywhere),
            autoCapitalize = text && !password && !addressLike && (inputType and CAP_FLAGS) != 0,
            noPersonalizedLearning = (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        )
    }
}
