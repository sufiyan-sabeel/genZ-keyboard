package com.genz.keyboard

/**
 * The bridge between a [ThemeDoc] and the live keyboard settings.
 *
 * `KeyboardSettings` already exposes per-surface colour overrides (`colorKey`, `colorKeyText`, …)
 * and `rememberKeyPalette` is built from exactly those fields. That means a theme can be previewed
 * and applied by writing its overrides onto a settings copy — the production renderer is reused
 * unchanged, and no parallel "preview palette" exists to drift out of sync.
 *
 * Pure functions only: this is the part that decides what a theme *means*, so it is unit-tested
 * rather than eyeballed in the UI.
 */
object ThemeApply {

    /** The override keys this layer understands, mapped to the settings field each one drives. */
    val COLOR_KEYS = listOf(
        "colorBackground",
        "colorKey",
        "colorKeyText",
        "colorFunc",
        "colorFuncText",
        "colorAccent",
        "colorAccentText"
    )

    const val KEY_OPACITY = "keyOpacity"
    const val CORNER_RADIUS = "cornerRadiusDp"

    /** Parse `#RRGGBB` or `#AARRGGBB` into an ARGB int, or null when the text is not a colour. */
    fun parseColor(text: String): Int? {
        val h = text.trim().removePrefix("#")
        return when (h.length) {
            6 -> runCatching { (0xFF000000L or h.toLong(16)).toInt() }.getOrNull()
            8 -> runCatching { h.toLong(16).toInt() }.getOrNull()
            else -> null
        }
    }

    fun formatColor(argb: Int): String = "#%08X".format(argb)

    /** Read an override as an ARGB colour, or null when absent/unparseable. */
    fun color(doc: ThemeDoc, key: String): Int? = doc.overrides[key]?.let(::parseColor)

    fun float(doc: ThemeDoc, key: String, fallback: Float): Float =
        doc.overrides[key]?.toFloatOrNull()?.coerceIn(0f, 100f) ?: fallback

    /**
     * Apply a theme to a settings copy.
     *
     * Only the surfaces the theme actually overrides are touched, so everything else — layout,
     * haptics, languages — is inherited untouched from the user's settings.
     */
    fun applyTo(doc: ThemeDoc, base: KeyboardSettings): KeyboardSettings {
        var s = base
        for (key in COLOR_KEYS) {
            val v = color(doc, key) ?: continue
            s = when (key) {
                "colorBackground" -> s.copy(colorBackground = v)
                "colorKey" -> s.copy(colorKey = v)
                "colorKeyText" -> s.copy(colorKeyText = v)
                "colorFunc" -> s.copy(colorFunc = v)
                "colorFuncText" -> s.copy(colorFuncText = v)
                "colorAccent" -> s.copy(colorAccent = v)
                "colorAccentText" -> s.copy(colorAccentText = v)
                else -> s
            }
        }
        doc.overrides[KEY_OPACITY]?.toFloatOrNull()?.let { s = s.copy(keyOpacity = it.coerceIn(0.2f, 1f)) }
        doc.overrides[CORNER_RADIUS]?.toFloatOrNull()?.let { s = s.copy(cornerRadiusDp = it.coerceIn(0f, 40f)) }
        return s
    }

    /**
     * Capture the current settings as a theme document.
     *
     * Used when creating a theme from scratch: whatever the keyboard looks like now becomes the
     * starting point, so a new theme is never a blank palette the user has to rebuild by hand.
     */
    fun capture(name: String, basePreset: String, from: KeyboardSettings): ThemeDoc {
        val overrides = LinkedHashMap<String, String>()
        if (from.colorBackground != 0) overrides["colorBackground"] = formatColor(from.colorBackground)
        if (from.colorKey != 0) overrides["colorKey"] = formatColor(from.colorKey)
        if (from.colorKeyText != 0) overrides["colorKeyText"] = formatColor(from.colorKeyText)
        if (from.colorFunc != 0) overrides["colorFunc"] = formatColor(from.colorFunc)
        if (from.colorFuncText != 0) overrides["colorFuncText"] = formatColor(from.colorFuncText)
        if (from.colorAccent != 0) overrides["colorAccent"] = formatColor(from.colorAccent)
        if (from.colorAccentText != 0) overrides["colorAccentText"] = formatColor(from.colorAccentText)
        overrides[KEY_OPACITY] = from.keyOpacity.toString()
        overrides[CORNER_RADIUS] = from.cornerRadiusDp.toString()
        return ThemeDoc(
            id = ThemeCodec.idFor(name, basePreset, overrides),
            name = name,
            basePreset = basePreset,
            overrides = overrides
        )
    }

    /** Preference key holding the id of the theme currently applied, or "" for none. */
    const val APPLIED_THEME_KEY = "applied_theme_id"

    /**
     * Persist a theme as the active one.
     *
     * Writes the same settings the preview reads, so what the Studio showed is what the keyboard
     * draws after the app restarts. Returns true when the write succeeded.
     */
    fun applyAndPersist(context: android.content.Context, doc: ThemeDoc, base: KeyboardSettings): Boolean {
        val s = applyTo(doc, base)
        return runCatching {
            KeyboardPrefs.edit(context) {
                // Keys verified against KeyboardPrefs.read: a wrong name would persist nothing.
                if (s.colorBackground != 0) putInt("color_background", s.colorBackground)
                if (s.colorKey != 0) putInt("color_key", s.colorKey)
                if (s.colorKeyText != 0) putInt("color_key_text", s.colorKeyText)
                if (s.colorFunc != 0) putInt("color_func", s.colorFunc)
                if (s.colorFuncText != 0) putInt("color_func_text", s.colorFuncText)
                if (s.colorAccent != 0) putInt("color_accent", s.colorAccent)
                if (s.colorAccentText != 0) putInt("color_accent_text", s.colorAccentText)
                putFloat("key_opacity", s.keyOpacity)
                putFloat("corner_radius_dp", s.cornerRadiusDp)
                putString(APPLIED_THEME_KEY, doc.id)
            }
            true
        }.getOrDefault(false)
    }

    /** The base preset a theme is built on, as an asset file name. */
    fun presetAsset(basePreset: String): String =
        "plugins/" + basePreset.substringAfterLast('.').let { "preset_$it.json" }

    /**
     * Resolve the colours a theme will actually show, starting from a preset's declared colours
     * and applying the theme's overrides on top.
     *
     * This is how "preset inheritance" is made concrete: a theme that changes only the accent still
     * gets every other colour from its base.
     */
    fun resolvedColors(doc: ThemeDoc, presetColors: Map<String, Int>): Map<String, Int> {
        val out = LinkedHashMap(presetColors)
        for (key in COLOR_KEYS) {
            color(doc, key)?.let { out[key] = it }
        }
        return out
    }
}
