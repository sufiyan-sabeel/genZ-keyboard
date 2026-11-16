package com.genz.keyboard

/**
 * Why the keyboard is currently in private mode.
 *
 * Every reason is something the keyboard itself can see and honour. None of them is a claim
 * about what the host app, the operating system or another input method does with the text.
 */
enum class IncognitoReason {
    /** The user switched incognito on by hand. Persisted, and wins over everything. */
    MANUAL,

    /** The editor is a password field — any of the text or numeric password variations. */
    PASSWORD_FIELD,

    /**
     * The editor asked not to be learned from, via
     * [android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING] (API 26+).
     *
     * This is a *request from the editor to the IME*. It is honoured because this keyboard
     * chooses to honour it, not because the platform enforces it, and it says nothing about
     * other keyboards or about what the host app retains.
     */
    NO_PERSONALIZED_LEARNING
}

/**
 * The single source of truth for "is the keyboard private right now?".
 *
 * Before this existed the answer was read as `settings.incognito` at nine separate call sites,
 * each of which also had to remember `flags.isPassword`. Adding a third reason would have meant
 * nine more edits and one inevitable miss — and a missed site is a silent privacy hole, not a
 * visible bug. There is now one derivation and one predicate ([isIncognito]).
 *
 * The class is pure: no Android types, no I/O, no time. That is what makes it testable, and it is
 * why the whole privacy decision can be checked in `PrivacyStateTest` without a device.
 */
data class PrivacyState(val reasons: Set<IncognitoReason>) {

    /** The only predicate call sites should use. */
    val isIncognito: Boolean get() = reasons.isNotEmpty()

    /** True when a reason other than the manual switch is active — i.e. the field forced it. */
    val forcedByField: Boolean
        get() = reasons.any { it != IncognitoReason.MANUAL }

    fun has(reason: IncognitoReason): Boolean = reason in reasons

    companion object {

        /** Shared instance for the common case, so the typing path allocates nothing. */
        val OFF = PrivacyState(emptySet())

        /**
         * Derive the state from the manual switch and the current field.
         *
         * @param manual the persisted user switch
         * @param field what the current editor accepts, or null when no editor is attached
         */
        fun derive(manual: Boolean, field: EditorFieldSpec?): PrivacyState {
            val password = field?.isPassword == true
            val noLearning = field?.noPersonalizedLearning == true
            // hot path: an ordinary field with the switch off must not allocate
            if (!manual && !password && !noLearning) return OFF

            val reasons = LinkedHashSet<IncognitoReason>(3)
            if (manual) reasons.add(IncognitoReason.MANUAL)
            if (password) reasons.add(IncognitoReason.PASSWORD_FIELD)
            if (noLearning) reasons.add(IncognitoReason.NO_PERSONALIZED_LEARNING)
            return PrivacyState(reasons)
        }
    }
}
