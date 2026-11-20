package com.genz.keyboard

/**
 * The structured (non-numeric) theme properties, encoded to and from the flat override map.
 *
 * Every value here comes from an enum or sealed type the renderer actually parses — `SkinMaterial`,
 * `SkinShape`, `PressEffect`, `BorderStyle`, `LabelEffect` and `SkinFill`. Nothing is invented, and an
 * unrecognised value decodes to null rather than to a plausible-looking default, so a control can
 * never show a selection the renderer will not honour.
 */
object ThemeStructured {

    /** `SkinMaterial` — how a key's surface is drawn. */
    val MATERIALS = listOf("solid", "glass", "outline", "neumorph", "bevel", "none")

    /** `SkinShape` — the outline of a keycap. */
    val SHAPES = listOf("rounded", "pill", "cut", "squircle", "wavy")

    /** `PressEffect` — what a key does when touched. */
    val PRESSES = listOf("scale", "jelly", "bounce", "sink", "none")

    /** `BorderStyle` — solid, dotted or dashed outline. */
    val BORDERS = listOf("solid", "dots", "dashed")

    /** `LabelEffect` — plain label, or a dotted-matrix label. */
    val LABELS = listOf("none", "dots")

    private val KNOWN = mapOf(
        "material" to MATERIALS,
        "shape" to SHAPES,
        "press" to PRESSES,
        "borderStyle" to BORDERS,
        "labelEffect" to LABELS
    )

    /** Keys this object knows how to encode. */
    fun keys(): Set<String> = KNOWN.keys

    fun options(key: String): List<String> = KNOWN[key].orEmpty()

    /** Encode an enum property, rejecting values the renderer does not know. */
    fun encodeEnum(key: String, value: String): String? {
        val allowed = KNOWN[key] ?: return null
        val v = value.trim().lowercase()
        return if (v in allowed) v else null
    }

    /** Decode an enum property; null when absent or not a recognised value. */
    fun decodeEnum(key: String, raw: String?): String? {
        val allowed = KNOWN[key] ?: return null
        val v = raw?.trim()?.lowercase() ?: return null
        return if (v in allowed) v else null
    }

    /**
     * A solid fill.
     *
     * The parser accepts a bare colour string as shorthand for a solid fill, so that is what is
     * written — a JSON object here would be equivalent but noisier.
     */
    fun encodeSolid(argb: Int): String = ThemeApply.formatColor(argb)

    /**
     * A gradient fill.
     *
     * Matches the parser's gradient form: an array of colours plus an optional angle and type.
     * Written as compact JSON because the override map stores strings.
     */
    fun encodeGradient(colours: List<Int>, angle: Float, radial: Boolean): String? {
        if (colours.size < 2) return null
        val list = colours.joinToString(",", "[", "]") { "\"${ThemeApply.formatColor(it)}\"" }
        val a = String.format(java.util.Locale.US, "%.0f", angle.coerceIn(0f, 360f))
        val type = if (radial) ",\"type\":\"radial\"" else ""
        return "{\"colors\":$list,\"angle\":$a$type}"
    }

    /** Whether a stored fill value is a gradient rather than a solid colour. */
    fun isGradient(raw: String?): Boolean = raw?.trim()?.startsWith("{") == true

    /** Whether a stored value is a colour this app can render. */
    fun isSolid(raw: String?): Boolean = raw != null && ThemeApply.parseColor(raw) != null
}
