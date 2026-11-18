package com.genz.keyboard

/**
 * Derives a full keyboard palette from a wallpaper's pixels.
 *
 * Deliberately free of Android types: the caller decodes a bitmap and passes the pixels in, which
 * means the whole extraction — including the readability guarantees — is unit-testable with
 * synthetic images.
 */
object PaletteExtractor {

    /** The seven surfaces the keyboard needs, as ARGB ints. */
    data class Palette(
        val background: Int,
        val key: Int,
        val keyText: Int,
        val func: Int,
        val funcText: Int,
        val accent: Int,
        val accentText: Int
    )

    private const val LIGHT_TEXT = 0xFFF4F1EA.toInt()
    private const val DARK_TEXT = 0xFF12130F.toInt()

    /**
     * Build a palette from [pixels] laid out as [width] x [height].
     *
     * The top third of the image becomes the gradient start and the bottom third the end, because
     * a keyboard sits over the lower part of a wallpaper and should echo what is behind it. Keycaps
     * are derived from the middle band, and the accent is the most saturated colour found — so a
     * sunset wallpaper yields an orange accent rather than a default purple.
     */
    fun extract(pixels: IntArray, width: Int, height: Int): Palette {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return fallback()

        val top = averageBand(pixels, width, height, 0.0, 0.33)
        val mid = averageBand(pixels, width, height, 0.33, 0.66)
        val bottom = averageBand(pixels, width, height, 0.66, 1.0)

        // Keycaps sit over the middle band: darken or lighten it until a label can be read on it.
        val keyBase = shadeToward(mid, 0.22f)
        val keyText = bestTextOn(keyBase)
        val func = shadeToward(mid, 0.34f)
        val funcText = bestTextOn(func)

        val accent = mostSaturated(pixels) ?: shadeToward(mid, 0.55f)
        val accentText = bestTextOn(accent)

        return Palette(
            background = top,
            key = keyBase,
            keyText = keyText,
            func = func,
            funcText = funcText,
            accent = accent,
            accentText = accentText
        )
    }

    /** Write a palette onto a theme as the seven colour overrides the renderer reads. */
    fun toOverrides(pal: Palette): Map<String, String> = linkedMapOf(
        "colorBackground" to ThemeApply.formatColor(pal.background),
        "colorKey" to ThemeApply.formatColor(pal.key),
        "colorKeyText" to ThemeApply.formatColor(pal.keyText),
        "colorFunc" to ThemeApply.formatColor(pal.func),
        "colorFuncText" to ThemeApply.formatColor(pal.funcText),
        "colorAccent" to ThemeApply.formatColor(pal.accent),
        "colorAccentText" to ThemeApply.formatColor(pal.accentText)
    )

    /** A neutral palette for when there is no usable image. */
    fun fallback(): Palette = Palette(
        background = 0xFF1D2128.toInt(),
        key = 0xFF283031.toInt(),
        keyText = LIGHT_TEXT,
        func = 0xFF33403F.toInt(),
        funcText = LIGHT_TEXT,
        accent = 0xFF7E9AA8.toInt(),
        accentText = DARK_TEXT
    )

    /** Average colour of the rows between [fromFrac] and [toFrac] of the image. */
    private fun averageBand(p: IntArray, w: Int, h: Int, fromFrac: Double, toFrac: Double): Int {
        val y0 = (h * fromFrac).toInt().coerceIn(0, h - 1)
        val y1 = (h * toFrac).toInt().coerceIn(y0 + 1, h)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        // sample at most ~2000 pixels per band: enough for an average, cheap on big images
        val step = ((y1 - y0) * w / 2000).coerceAtLeast(1)
        var i = y0 * w
        val end = y1 * w
        while (i < end) {
            val c = p[i]
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            n++
            i += step
        }
        if (n == 0L) return 0xFF202020.toInt()
        return argb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** The most colourful pixel, quantised to a coarse bucket so noise does not win. */
    private fun mostSaturated(p: IntArray): Int? {
        var best = -1
        var bestScore = 0
        val step = (p.size / 4000).coerceAtLeast(1)
        var i = 0
        while (i < p.size) {
            val c = p[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val sat = max - min
            // prefer saturated but not blindingly bright, so the accent stays usable as a surface
            val score = if (max < 40) 0 else sat * (255 - kotlin.math.abs(max - 150)) / 255
            if (score > bestScore) { bestScore = score; best = c }
            i += step
        }
        return if (best >= 0 && bestScore > 12) best else null
    }

    /** Move a colour towards black or white depending on how light it already is. */
    private fun shadeToward(c: Int, amount: Float): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val target = if (luminance(r, g, b) > 0.45) 0 else 255
        fun mix(v: Int) = (v + (target - v) * amount).toInt().coerceIn(0, 255)
        return argb(mix(r), mix(g), mix(b))
    }

    /** Whichever of a near-white or near-black label is readable on [bg]. */
    private fun bestTextOn(bg: Int): Int =
        if (contrast(bg, LIGHT_TEXT) >= contrast(bg, DARK_TEXT)) LIGHT_TEXT else DARK_TEXT

    fun contrast(a: Int, b: Int): Double {
        val la = luminance((a shr 16) and 0xFF, (a shr 8) and 0xFF, a and 0xFF)
        val lb = luminance((b shr 16) and 0xFF, (b shr 8) and 0xFF, b and 0xFF)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(r: Int, g: Int, b: Int): Double {
        fun f(u: Int): Double {
            val v = u / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
