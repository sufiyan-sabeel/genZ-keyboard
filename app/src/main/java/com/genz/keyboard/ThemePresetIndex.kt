package com.genz.keyboard

/**
 * One bundled theme, as the Studio needs to show it in the gallery.
 *
 * Parsing is pure: the caller supplies the file name and its JSON, so the gallery can be tested
 * without an Android asset manager.
 */
data class PresetEntry(
    val id: String,
    val name: String,
    val sourceFile: String,
    /** Wallpaper path as declared by the theme, e.g. `anime/art_sunset.webp`, or null. */
    val wallpaper: String?,
    /** The theme's own colours, already parsed to ARGB. */
    val colours: Map<String, Int>,
    val description: String
)

/**
 * Reads the bundled theme files into gallery entries.
 *
 * Only themes that can actually render are returned: a theme declaring a wallpaper that this build
 * does not ship is dropped, so the gallery never shows a card whose artwork is missing. That is how
 * the three withheld themes stay out of the UI even if their files are present in the repository.
 */
object ThemePresetIndex {

    /** Files the gallery shows: the twelve palette presets and the artwork themes. */
    fun isGalleryFile(name: String): Boolean =
        name.endsWith(".json") && (name.startsWith("art_") || name.startsWith("preset_"))

    /**
     * @param availableWallpapers wallpaper paths this build actually ships, e.g.
     *   `anime/art_sunset.webp`. Empty means the build has no artwork (the F-Droid flavour).
     */
    fun parse(
        fileName: String,
        json: String,
        availableWallpapers: Set<String>
    ): PresetEntry? {
        val o = ThemeJson.decode(json) ?: return null
        val id = (o["id"] as? String)?.trim().orEmpty()
        if (id.isEmpty()) return null
        val name = (o["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return null

        val wallpaper = (o["backgroundImage"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        // A theme whose artwork is absent would render as a broken card, so it is not offered.
        if (wallpaper != null && wallpaper !in availableWallpapers) return null

        @Suppress("UNCHECKED_CAST")
        val skin = o["skin"] as? Map<String, Any?> ?: return null
        @Suppress("UNCHECKED_CAST")
        val colors = skin["colors"] as? Map<String, Any?> ?: emptyMap<String, Any?>()

        val parsed = LinkedHashMap<String, Int>()
        for (key in ThemeApply.COLOR_KEYS) {
            val raw = colors[key.removePrefix("color").replaceFirstChar { it.lowercase() }] as? String
                ?: colors[key] as? String
                ?: continue
            ThemeApply.parseColor(raw)?.let { parsed[key] = it }
        }
        return PresetEntry(
            id = id,
            name = name,
            sourceFile = fileName,
            wallpaper = wallpaper,
            colours = parsed,
            description = (o["description"] as? String).orEmpty()
        )
    }

    /** Build the gallery from raw (file name, json) pairs, skipping anything unusable. */
    fun build(files: List<Pair<String, String>>, availableWallpapers: Set<String>): List<PresetEntry> =
        files.mapNotNull { (n, j) -> parse(n, j, availableWallpapers) }.sortedBy { it.name.lowercase() }
}
