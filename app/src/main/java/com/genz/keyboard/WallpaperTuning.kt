package com.genz.keyboard

/**
 * How a wallpaper is drawn behind the keys.
 *
 * Stored as sparse overrides on the [ThemeDoc], so a theme keeps these settings through save,
 * export and import without the format needing a new field. Pure: no Android types, so the
 * clamping rules are unit-tested.
 */
data class WallpaperTuning(
    /** 0 = wallpaper invisible, 1 = fully visible. */
    val opacity: Float = 1f,
    /** Blur radius in dp applied to the wallpaper only. */
    val blurDp: Float = 0f,
    /** Multiplier on the wallpaper's brightness; 1 = untouched. */
    val brightness: Float = 1f,
    /** Zoom factor; 1 = fit, larger crops in. */
    val scale: Float = 1f,
    /** Pan, as a fraction of the wallpaper's width/height. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
) {
    companion object {
        const val KEY_OPACITY = "wallpaperOpacity"
        const val KEY_BLUR = "wallpaperBlur"
        const val KEY_BRIGHTNESS = "wallpaperBrightness"
        const val KEY_SCALE = "wallpaperScale"
        const val KEY_OFFSET_X = "wallpaperOffsetX"
        const val KEY_OFFSET_Y = "wallpaperOffsetY"

        /** Bounds chosen so the UI cannot produce an unusable keyboard. */
        const val MIN_OPACITY = 0.05f
        const val MAX_OPACITY = 1f
        const val MIN_BLUR = 0f
        const val MAX_BLUR = 24f
        const val MIN_BRIGHTNESS = 0.4f
        const val MAX_BRIGHTNESS = 1.6f
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 3f
        const val MAX_PAN = 0.5f

        /** Read the tuning stored on a theme, falling back to the defaults. */
        fun from(doc: ThemeDoc): WallpaperTuning = WallpaperTuning(
            opacity = doc.overrides[KEY_OPACITY]?.toFloatOrNull()
                ?.coerceIn(MIN_OPACITY, MAX_OPACITY) ?: 1f,
            blurDp = doc.overrides[KEY_BLUR]?.toFloatOrNull()
                ?.coerceIn(MIN_BLUR, MAX_BLUR) ?: 0f,
            brightness = doc.overrides[KEY_BRIGHTNESS]?.toFloatOrNull()
                ?.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS) ?: 1f,
            scale = doc.overrides[KEY_SCALE]?.toFloatOrNull()
                ?.coerceIn(MIN_SCALE, MAX_SCALE) ?: 1f,
            offsetX = doc.overrides[KEY_OFFSET_X]?.toFloatOrNull()
                ?.coerceIn(-MAX_PAN, MAX_PAN) ?: 0f,
            offsetY = doc.overrides[KEY_OFFSET_Y]?.toFloatOrNull()
                ?.coerceIn(-MAX_PAN, MAX_PAN) ?: 0f
        )

        /** Write the tuning back onto a theme, clamping first so a bad value can never be stored. */
        fun apply(doc: ThemeDoc, t: WallpaperTuning): ThemeDoc {
            val ov = doc.overrides.toMutableMap()
            ov[KEY_OPACITY] = t.opacity.coerceIn(MIN_OPACITY, MAX_OPACITY).toString()
            ov[KEY_BLUR] = t.blurDp.coerceIn(MIN_BLUR, MAX_BLUR).toString()
            ov[KEY_BRIGHTNESS] = t.brightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS).toString()
            ov[KEY_SCALE] = t.scale.coerceIn(MIN_SCALE, MAX_SCALE).toString()
            ov[KEY_OFFSET_X] = t.offsetX.coerceIn(-MAX_PAN, MAX_PAN).toString()
            ov[KEY_OFFSET_Y] = t.offsetY.coerceIn(-MAX_PAN, MAX_PAN).toString()
            return doc.copy(overrides = ov)
        }

        /** Remove the tuning, so the theme falls back to defaults. */
        fun clear(doc: ThemeDoc): ThemeDoc {
            val ov = doc.overrides.toMutableMap()
            listOf(KEY_OPACITY, KEY_BLUR, KEY_BRIGHTNESS, KEY_SCALE, KEY_OFFSET_X, KEY_OFFSET_Y)
                .forEach { ov.remove(it) }
            return doc.copy(overrides = ov)
        }
    }
}
