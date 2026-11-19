package com.genz.keyboard

import java.util.Locale

/**
 * The numeric theme properties the editor exposes, with their real ranges.
 *
 * Ranges come from the renderer, not from taste: `Skins.kt` coerces radius, border width and shadow
 * values, and a control that offers values the renderer clamps is a control that lies about what it
 * will produce. Keeping the table here means the editor and its tests share one definition.
 */
object ThemeProperty {

    data class Spec(
        val key: String,
        val label: String,
        val min: Float,
        val max: Float,
        /** Value assumed when the theme does not set the property. */
        val default: Float,
        val unit: String
    ) {
        val range: ClosedFloatingPointRange<Float> get() = min..max
    }

    val NUMERIC: List<Spec> = listOf(
        Spec("radius", "Key corner radius", 0f, 40f, 12f, "dp"),
        Spec("borderWidth", "Border width", 0f, 6f, 0f, "dp"),
        Spec("shadow", "Shadow depth", 0f, 24f, 0f, "dp"),
        Spec("shadowY", "Shadow offset", -8f, 16f, 0f, "dp"),
        Spec("fontScale", "Label scale", 0.6f, 1.6f, 1f, "x"),
        Spec("pressScale", "Press scale", 0.8f, 1.1f, 1f, "x"),
        Spec("corner", "Keyboard corner", 0f, 36f, 0f, "dp"),
        Spec("windowBlur", "Background blur", 0f, 30f, 0f, "dp")
    )

    fun byKey(key: String): Spec? = NUMERIC.firstOrNull { it.key == key }

    /**
     * The value to show for a property.
     *
     * A missing or unparsable override falls back to the default rather than to the bottom of the
     * range, so a theme that never set the property shows what the keyboard actually uses.
     */
    fun valueOf(doc: ThemeDoc, spec: Spec): Float =
        doc.overrides[spec.key]?.toFloatOrNull()?.coerceIn(spec.min, spec.max) ?: spec.default

    /**
     * Serialise a value for storage, clamped to the renderer's range.
     *
     * Clamping here means a slider can never write a value the renderer would silently change,
     * and trailing zeros are trimmed so the stored form stays stable and comparable.
     */
    fun encode(spec: Spec, value: Float): String {
        val v = value.coerceIn(spec.min, spec.max)
        return String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    }

    /** Whether a property differs from the theme as loaded, i.e. whether reset is meaningful. */
    fun isModified(doc: ThemeDoc, original: ThemeDoc, spec: Spec): Boolean =
        doc.overrides[spec.key] != original.overrides[spec.key]
}
