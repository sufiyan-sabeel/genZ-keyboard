package com.genz.keyboard

/**
 * A user-authored theme: a base preset plus only the properties the user changed.
 *
 * Sparse on purpose. Storing a full copy would freeze the theme at the preset version it was
 * created from, so a later improvement to that preset would never reach it; storing only the
 * differences keeps user themes small and lets them inherit preset fixes.
 *
 * No Android types: the whole model, its JSON codec and its validation are unit-testable.
 */
data class ThemeDoc(
    val id: String,
    val name: String,
    val basePreset: String,
    /** Flat property path -> value, e.g. `"colors.key" to "#1A1C22"`. */
    val overrides: Map<String, String> = emptyMap(),
    /** A file name inside the theme image directory. Never a path or a URL. */
    val wallpaper: String? = null,
    val schemaVersion: Int = SCHEMA
) {
    companion object {
        /** Bumped only when the on-disk shape changes incompatibly. */
        const val SCHEMA = 1
        const val MAX_NAME = 48
        const val MAX_OVERRIDES = 200
        const val MAX_VALUE = 40
        const val MAX_JSON_BYTES = 64 * 1024
    }
}

/** The outcome of validating untrusted theme JSON. */
sealed interface ThemeValidation {
    data class Ok(val doc: ThemeDoc) : ThemeValidation
    data class Invalid(val reason: String) : ThemeValidation
}

/**
 * Encodes and validates theme documents.
 *
 * Imported themes are untrusted input, so every rule lives here as a pure function rather than
 * inside a click handler: a malformed file must be rejected with a reason, never crash the app and
 * never be able to reach the network or the filesystem.
 */
object ThemeCodec {

    private val ID = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    private val PRESET = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    // `*` is a legitimate path segment: the skin schema uses `keys.*` for "every key".
    private val KEY = Regex("^[a-z*][a-zA-Z0-9_.*]{0,47}$")
    private val HEX = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val NUM = Regex("^-?[0-9]{1,4}(?:\\.[0-9]{1,3})?$")
    /** A bare file name with an image extension: no slashes, no scheme, no traversal. */
    private val WALLPAPER = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}\\.(?:png|jpg|jpeg|webp)$")

    fun encode(doc: ThemeDoc): String {
        val ov = LinkedHashMap<String, Any?>()
        doc.overrides.toSortedMap().forEach { (k, v) -> ov[k] = v }
        val fields = LinkedHashMap<String, Any?>()
        fields["schema"] = doc.schemaVersion
        fields["id"] = doc.id
        fields["name"] = doc.name
        fields["base"] = doc.basePreset
        doc.wallpaper?.let { fields["wallpaper"] = it }
        fields["overrides"] = ov
        return ThemeJson.encode(fields)
    }

    /**
     * Parse and validate untrusted JSON.
     *
     * Rejects: wrong or future schema, oversized payload, bad identifiers, oversized names, too
     * many overrides, values that are neither a hex colour nor a small number, and any wallpaper
     * reference that is not a bare local file name.
     */
    fun decode(json: String): ThemeValidation {
        if (json.toByteArray().size > ThemeDoc.MAX_JSON_BYTES) {
            return ThemeValidation.Invalid("theme file is too large")
        }
        val o = ThemeJson.decode(json) ?: return ThemeValidation.Invalid("not valid JSON")

        val schema = (o["schema"] as? Number)?.toInt() ?: -1
        if (schema != ThemeDoc.SCHEMA) {
            return ThemeValidation.Invalid("unsupported theme version")
        }
        val id = (o["id"] as? String)?.trim().orEmpty()
        if (!ID.matches(id)) return ThemeValidation.Invalid("invalid theme id")

        val name = (o["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty()) return ThemeValidation.Invalid("theme name is required")
        if (name.length > ThemeDoc.MAX_NAME) return ThemeValidation.Invalid("theme name is too long")

        val base = (o["base"] as? String)?.trim().orEmpty()
        if (!PRESET.matches(base)) return ThemeValidation.Invalid("invalid base preset")

        val wallpaper = when (val w = o["wallpaper"]) {
            null -> null
            is String -> {
                val t = w.trim()
                // Reject anything that is not a plain file name: no URLs, no absolute paths, no "..".
                if (!WALLPAPER.matches(t)) return ThemeValidation.Invalid("invalid wallpaper reference")
                t
            }
            else -> return ThemeValidation.Invalid("invalid wallpaper reference")
        }

        val overrides = LinkedHashMap<String, String>()
        when (val ov = o["overrides"]) {
            null -> Unit
            is Map<*, *> -> {
                if (ov.size > ThemeDoc.MAX_OVERRIDES) {
                    return ThemeValidation.Invalid("too many overrides")
                }
                for ((k, raw) in ov) {
                    val key = k as? String ?: return ThemeValidation.Invalid("invalid property name")
                    if (!KEY.matches(key)) return ThemeValidation.Invalid("invalid property: $key")
                    val value = when (raw) {
                        is String -> raw.trim()
                        is Number -> if (raw.toDouble() == raw.toLong().toDouble()) raw.toLong().toString() else raw.toString()
                        else -> return ThemeValidation.Invalid("invalid value for $key")
                    }
                    if (value.isEmpty() || value.length > ThemeDoc.MAX_VALUE) {
                        return ThemeValidation.Invalid("invalid value for $key")
                    }
                    if (!HEX.matches(value) && !NUM.matches(value)) {
                        return ThemeValidation.Invalid("invalid value for $key")
                    }
                    overrides[key] = value
                }
            }
            else -> return ThemeValidation.Invalid("invalid overrides")
        }
        return ThemeValidation.Ok(
            ThemeDoc(
                id = id,
                name = name,
                basePreset = base,
                overrides = overrides,
                wallpaper = wallpaper,
                schemaVersion = schema
            )
        )
    }

    /** A stable id derived from the theme's content, so saving the same theme twice is idempotent. */
    fun idFor(name: String, base: String, overrides: Map<String, String>): String {
        val seed = buildString {
            append(base); append('|'); append(name); append('|')
            overrides.toSortedMap().forEach { (k, v) -> append(k).append('=').append(v).append(';') }
        }
        // 32-bit FNV-1a: stable across runs and platforms, unlike String.hashCode on some inputs.
        var h = -0x7ee3623b
        for (c in seed) {
            h = h xor c.code
            h *= 0x01000193
        }
        return "user." + Integer.toHexString(h)
    }
}
