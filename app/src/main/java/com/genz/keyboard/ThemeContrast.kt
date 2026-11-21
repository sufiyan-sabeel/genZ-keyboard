package com.genz.keyboard

/**
 * WCAG 2.1 contrast maths.
 *
 * Pure and independent of Compose so the thresholds can be tested against published reference cases.
 * Nothing here mutates a theme: the numbers are reported, and a failing pair is a decision for a
 * human, not something to silently "fix" by nudging a colour.
 */
object ThemeContrast {

    /** WCAG AA for normal-size text. */
    const val AA_NORMAL = 4.5

    /** WCAG AA for large text (>= 18pt, or >= 14pt bold). */
    const val AA_LARGE = 3.0

    /** WCAG AAA for normal-size text. */
    const val AAA_NORMAL = 7.0

    /**
     * Relative luminance, per WCAG 2.1 §relative-luminance.
     *
     * Channels are linearised out of sRGB before weighting, which is why a mid grey is far darker
     * than its channel average suggests.
     */
    fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = (c and 0xFF) / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = channel(argb shr 16)
        val g = channel(argb shr 8)
        val b = channel(argb)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Contrast ratio between two colours, from 1.0 (identical) to 21.0 (black on white).
     *
     * Order does not matter: the lighter colour is always the numerator.
     */
    fun ratio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    fun passesAA(ratio: Double, large: Boolean = false): Boolean =
        ratio >= if (large) AA_LARGE else AA_NORMAL

    fun passesAAA(ratio: Double, large: Boolean = false): Boolean =
        ratio >= if (large) AAA_NORMAL else AAA_NORMAL

    /** Round to two decimals for reporting. */
    fun rounded(r: Double): Double = Math.round(r * 100.0) / 100.0

    /**
     * The foreground/background pairs that actually appear on the keyboard.
     *
     * Only pairs the renderer really draws are checked — a token with no consumer would produce a
     * meaningless number.
     */
    val PAIRS: List<Pair<String, String>> = listOf(
        "keyText" to "key",          // letter labels on alphabet keys
        "funcText" to "func",        // shift, backspace, enter labels
        "accentText" to "accent"     // the accented key's own label
    )

    /*
     * Deliberately not checked: "keyText on background" and "accentText on background".
     *
     * Every bundled theme gives its keys an opaque fill, so labels are never composited directly
     * over the wallpaper. Measuring that pair would report a failure for a combination the renderer
     * never draws — a false positive that would push a palette change for no visible reason. If a
     * theme ever ships translucent keys, that pair becomes real and must be added back.
     */
}
