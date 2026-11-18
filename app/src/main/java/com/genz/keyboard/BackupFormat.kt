package com.genz.keyboard

/**
 * Constants, the manifest model and its codec for the user-data backup format.
 *
 * No Android types: the whole format is unit-testable, which matters because everything that
 * decides whether an imported archive is safe lives in here and in [BackupValidate].
 *
 * The container is a ZIP because wallpapers, fonts and sounds must round-trip byte-exact; a JSON
 * file would mean base64 inflation of megabytes of binary.
 */
object BackupFormat {

    /** Bumped only for an incompatible change. A higher value is refused, never guessed at. */
    const val SCHEMA = 1

    /** Oldest app that may import this format. A backup cannot restore a setting the app cannot read. */
    const val MIN_APP_VERSION_CODE = 12

    const val MANIFEST = "manifest.json"
    const val PREFS = "prefs.json"
    const val DATA = "data.json"
    const val JOURNAL = "journal.json"

    const val THEMES_DIR = "themes/"
    const val IMAGES_DIR = "images/"
    const val ASSETS_DIR = "assets/"

    /** Total uncompressed bytes accepted from one archive. Enforced while streaming. */
    const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024

    /** Hard cap on entry count, so a zip with a million empty files cannot exhaust the device. */
    const val MAX_ENTRIES = 500

    /** Per-entry cap for payload files. */
    const val MAX_ENTRY_BYTES = 8L * 1024 * 1024

    const val MAX_MANIFEST_BYTES = 64 * 1024
    const val MAX_THEMES = 200
    const val MAX_IMAGES = 200

    /** Asset caps are per kind: a font is bigger than a click sound but nothing here is huge. */
    const val MAX_ASSET_FONT_BYTES = 5L * 1024 * 1024
    const val MAX_ASSET_SOUND_BYTES = 2L * 1024 * 1024
    const val MAX_ASSET_IMAGE_BYTES = 8L * 1024 * 1024

    /**
     * The exact preference allowlist, with the type each key must have.
     *
     * Deliberately not "all keys": 131 keys are read by `KeyboardPrefs.read`, most of which are
     * device-specific (screen metrics), transient, or credentials. Only settings a user would
     * actually miss after a reinstall are here, and every entry below was checked against the real
     * parser. Adding a key means verifying it exists and its type.
     */
    val PREF_TYPES: Map<String, PrefType> = linkedMapOf(
        // appearance
        "theme_mode" to PrefType.STRING,
        "accent_shape_preset" to PrefType.STRING,
        "font_style" to PrefType.STRING,
        "color_source" to PrefType.STRING,
        "color_background" to PrefType.INT,
        "color_key" to PrefType.INT,
        "color_key_text" to PrefType.INT,
        "color_func" to PrefType.INT,
        "color_func_text" to PrefType.INT,
        "color_accent" to PrefType.INT,
        "color_accent_text" to PrefType.INT,
        "key_height_dp" to PrefType.FLOAT,
        "corner_radius_dp" to PrefType.FLOAT,
        "key_opacity" to PrefType.FLOAT,
        "keyboard_width" to PrefType.FLOAT,
        // behaviour
        "haptic_mode" to PrefType.STRING,
        "sound_enabled" to PrefType.BOOL,
        "sound_volume" to PrefType.FLOAT,
        "one_handed" to PrefType.STRING,
        "space_swipe" to PrefType.STRING,
        "emoji_style" to PrefType.STRING,
        "show_suggestions" to PrefType.BOOL,
        "auto_capitalize" to PrefType.BOOL,
        "auto_correct_strength" to PrefType.STRING,
        "double_space_period" to PrefType.BOOL,
        "dash_merge" to PrefType.BOOL,
        "system_spellcheck" to PrefType.BOOL,
        "glide_typing" to PrefType.BOOL,
        "key_preview" to PrefType.BOOL,
        "floating_keyboard" to PrefType.BOOL,
        // privacy
        "incognito" to PrefType.BOOL,
        "clipboard_history" to PrefType.BOOL,
        "clipboard_size" to PrefType.INT
    )

    /**
     * Keys that must never appear in a backup, on export **or** import.
     *
     * Mirrors `SecretBox.SECRET_PREFS`, which already documents that these are encrypted and not
     * exported. Keeping the list here means a hand-edited archive cannot smuggle them back in.
     */
    val FORBIDDEN_KEYS: Set<String> = setOf(
        "anthropic_key", "gemini_key", "openai_key", "groq_key",
        "openrouter_key", "giphy_key", "klipy_key", "ai_custom"
    )

    /** Path prefixes that are never read from an archive: code, clipboard images, plugin state. */
    val FORBIDDEN_PREFIXES: List<String> = listOf("plugins/", "clips/", "plugin_storage", "lib/", "dex/")

    /** File extensions allowed inside the archive, by directory. */
    val THEME_EXTENSIONS = setOf("json")
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    val ASSET_EXTENSIONS = setOf("ttf", "otf", "ogg", "mp3", "wav", "png", "jpg", "jpeg", "webp")
}

enum class PrefType { STRING, INT, FLOAT, BOOL, LONG }

/** One payload file recorded in the manifest. */
data class ManifestEntry(val path: String, val sha256: String, val bytes: Long)

/**
 * The archive manifest.
 *
 * `entries` is the authoritative list of what the archive contains: a payload present in the ZIP
 * but absent here is rejected, and a manifest record with no payload is rejected too. That is what
 * stops an archive from carrying a file the importer was never told about.
 */
data class BackupManifest(
    val schema: Int,
    val appVersionCode: Int,
    val appVersionName: String,
    val exportedAt: String,
    val entries: List<ManifestEntry>,
    val includesThemes: Boolean = false,
    val includesPreferences: Boolean = false,
    val includesLearnedWords: Boolean = false,
    val includesDictionary: Boolean = false,
    val includesShortcuts: Boolean = false,
    val includesAssets: Boolean = false
)

sealed interface BackupCheck {
    data object Ok : BackupCheck
    data class Failed(val reason: String) : BackupCheck
}

/** Encodes and decodes the manifest. Pure; uses the project's own JSON reader. */
object BackupManifestCodec {

    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun encode(m: BackupManifest): String {
        val entries = m.entries.map { e ->
            linkedMapOf<String, Any?>("path" to e.path, "sha256" to e.sha256, "bytes" to e.bytes)
        }
        val fields = linkedMapOf<String, Any?>(
            "schema" to m.schema,
            "appVersionCode" to m.appVersionCode,
            "appVersionName" to m.appVersionName,
            "exportedAt" to m.exportedAt,
            "includesThemes" to m.includesThemes,
            "includesPreferences" to m.includesPreferences,
            "includesLearnedWords" to m.includesLearnedWords,
            "includesDictionary" to m.includesDictionary,
            "includesShortcuts" to m.includesShortcuts,
            "includesAssets" to m.includesAssets,
            "entries" to entries
        )
        return ThemeJson.encode(fields)
    }

    /** @return the manifest, or null when the JSON is malformed or structurally wrong. */
    fun decode(json: String): BackupManifest? {
        if (json.toByteArray().size > BackupFormat.MAX_MANIFEST_BYTES) return null
        val o = ThemeJson.decode(json) ?: return null
        val schema = (o["schema"] as? Number)?.toInt() ?: return null
        val code = (o["appVersionCode"] as? Number)?.toInt() ?: return null
        val name = o["appVersionName"] as? String ?: ""
        val at = o["exportedAt"] as? String ?: ""

        val rawEntries = o["entries"] as? List<*> ?: return null
        if (rawEntries.size > BackupFormat.MAX_ENTRIES) return null
        val entries = ArrayList<ManifestEntry>(rawEntries.size)
        for (raw in rawEntries) {
            val e = raw as? Map<*, *> ?: return null
            val path = e["path"] as? String ?: return null
            val sha = (e["sha256"] as? String)?.lowercase() ?: return null
            val bytes = (e["bytes"] as? Number)?.toLong() ?: return null
            if (!SHA256.matches(sha)) return null
            if (bytes < 0) return null
            entries += ManifestEntry(path, sha, bytes)
        }
        return BackupManifest(
            schema = schema,
            appVersionCode = code,
            appVersionName = name,
            exportedAt = at,
            entries = entries,
            includesThemes = o["includesThemes"] as? Boolean ?: false,
            includesPreferences = o["includesPreferences"] as? Boolean ?: false,
            includesLearnedWords = o["includesLearnedWords"] as? Boolean ?: false,
            includesDictionary = o["includesDictionary"] as? Boolean ?: false,
            includesShortcuts = o["includesShortcuts"] as? Boolean ?: false,
            includesAssets = o["includesAssets"] as? Boolean ?: false
        )
    }
}
