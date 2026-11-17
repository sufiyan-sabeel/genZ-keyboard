package com.genz.keyboard

/**
 * Which mod-facing events may reach user scripts, and when.
 *
 * The keyboard already refused these events in a private session, but only because each one
 * happened to have an entry in the script host's permission map. That is an emergent property, not
 * a rule: an event added later without an entry would have been delivered silently. This class
 * states the rule, and [ScriptHost.emit] enforces it before any handler runs.
 *
 * Pure, so the policy is testable without a script engine.
 */
object PluginEventPolicy {

    /**
     * Events that carry what the user typed, or enough to reconstruct it.
     *
     * `pointer` belongs here because touch coordinates on a keyboard *are* the typed text, and
     * `selection` because cursor positions reveal the length of what was typed.
     */
    val CONTENT_EVENTS: Set<String> = setOf(
        "press",
        "key",
        "word",
        "suggest",
        "start",
        "selection",
        "pick",
        "voice",
        "autocorrect",
        "glide",
        "pointer",
        "clipboard"
    )

    fun carriesTypedContent(event: String): Boolean = event in CONTENT_EVENTS

    /**
     * May this event be delivered to scripts under the given effective privacy?
     *
     * Non-content events (panel changes, language, sensor levels) stay available: they say nothing
     * about what was typed, and blocking them would break mods that draw effects without reading
     * text.
     */
    fun allows(event: String, privacy: PrivacyState): Boolean =
        !(privacy.isIncognito && carriesTypedContent(event))
}
