package com.genz.keyboard

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * The offline backup container.
 *
 * A ZIP rather than one large JSON: wallpapers are binary, and base64 inside JSON would inflate them
 * and make a single corrupted byte unrecoverable for the whole archive.
 *
 * `settings.json` is stored byte-for-byte as [KeyboardPrefs.exportJson] produces it, so a backup taken
 * by this build can still be imported by an older one, and a round trip cannot quietly alter a
 * preference.
 *
 * The whole archive is validated before anything is applied; a bad file leaves the device untouched.
 */
object BackupBundle {

    const val FORMAT = "genz-backup"
    const val VERSION = 1
    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val WORDS = "words.json"
    const val THEMES_DIR = "themes/"
    const val IMAGES_DIR = "images/"

    sealed interface ReadResult {
        data class Ok(val contents: Contents) : ReadResult
        /** A settings-only file from an older build: still valid, but nothing else is inside. */
        data class LegacySettings(val json: String) : ReadResult
        data class Invalid(val reason: String) : ReadResult
    }

    data class Contents(
        val settingsJson: String?,
        val words: Map<String, Int>,
        val bigrams: Map<String, Int>,
        val themes: List<ThemeDoc>,
        val images: Map<String, ByteArray>
    )

    /**
     * Write an archive.
     *
     * @param imageBytes resolves a theme's wallpaper name to its bytes; a missing file is skipped
     *   rather than failing the backup, because losing one wallpaper must not cost the user every
     *   other theme.
     */
    fun write(
        target: File,
        settingsJson: String,
        words: Map<String, Int>,
        bigrams: Map<String, Int>,
        themes: List<ThemeDoc>,
        imageBytes: (String) -> ByteArray?
    ): Boolean = runCatching {
        val images = LinkedHashMap<String, ByteArray>()
        themes.mapNotNull { it.wallpaper }.distinct().forEach { name ->
            if (DownloadGuard.isSafeObjectPath(name)) imageBytes(name)?.let { images[name] = it }
        }

        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(MANIFEST, manifestJson(settingsJson, words, bigrams, themes, images).toByteArray())
            put(SETTINGS, settingsJson.toByteArray())
            put(WORDS, wordsJson(words, bigrams).toByteArray())
            themes.forEach { put("$THEMES_DIR${it.id}.json", ThemeCodec.encode(it).toByteArray()) }
            images.forEach { (name, bytes) -> put("$IMAGES_DIR$name", bytes) }
        }
        true
    }.getOrDefault(false)

    /** Read and validate an archive without touching application data. */
    fun read(file: File): ReadResult = runCatching {
        if (!file.isFile) return ReadResult.Invalid("file not found")
        if (file.length() > BackupFormat.MAX_ARCHIVE_BYTES) return ReadResult.Invalid("archive is too large")

        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()

            // A bare JSON file is what every earlier build produced; keep accepting it.
            if (entries.isEmpty()) return ReadResult.Invalid("archive is empty")

            val manifestEntry = zip.getEntry(MANIFEST)
                ?: return legacyOrInvalid(zip, entries)

            val names = entries.map { it.name }
            if (names.size != names.distinct().size) return ReadResult.Invalid("duplicate entries")

            val manifest = zip.getInputStream(manifestEntry).use { it.readBytes() }
            if (manifest.size > BackupFormat.MAX_MANIFEST_BYTES) return ReadResult.Invalid("manifest is too large")
            val m = ThemeJson.decode(manifest.toString(Charsets.UTF_8))
                ?: return ReadResult.Invalid("manifest is not valid JSON")
            if ((m["format"] as? String) != FORMAT) return ReadResult.Invalid("not a GenZ Keyboard backup")
            val version = (m["version"] as? Number)?.toInt() ?: 0
            if (version > VERSION) return ReadResult.Invalid("backup was made by a newer version")

            var total = 0L
            if (entries.size > BackupFormat.MAX_ENTRIES) return ReadResult.Invalid("too many entries")

            fun text(name: String): String? {
                val e = zip.getEntry(name) ?: return null
                val bytes = zip.getInputStream(e).use { it.readBytes() }
                total += bytes.size
                if (total > BackupFormat.MAX_ARCHIVE_BYTES) return null
                return bytes.toString(Charsets.UTF_8)
            }

            val settings = text(SETTINGS)
            val wordsJsonText = text(WORDS)

            val themes = mutableListOf<ThemeDoc>()
            val images = mutableMapOf<String, ByteArray>()
            for (e in entries) {
                if (e.isDirectory) continue
                // anything outside the known layout is a malformed or hostile archive
                if (e.name != MANIFEST && e.name != SETTINGS && e.name != WORDS &&
                    !e.name.startsWith(THEMES_DIR) && !e.name.startsWith(IMAGES_DIR)
                ) {
                    return ReadResult.Invalid("unexpected entry: ${e.name}")
                }
                if (BackupValidate.entryPath(e.name) == null) return ReadResult.Invalid("unsafe entry: ${e.name}")

                when {
                    e.name.startsWith(THEMES_DIR) -> {
                        val bytes = zip.getInputStream(e).use { it.readBytes() }
                        total += bytes.size
                        if (total > BackupFormat.MAX_ARCHIVE_BYTES) return ReadResult.Invalid("archive expands too far")
                        when (val v = ThemeCodec.decode(bytes.toString(Charsets.UTF_8))) {
                            is ThemeValidation.Ok -> themes += v.doc
                            is ThemeValidation.Invalid -> return ReadResult.Invalid("theme ${e.name}: ${v.reason}")
                        }
                    }
                    e.name.startsWith(IMAGES_DIR) -> {
                        val bytes = zip.getInputStream(e).use { it.readBytes() }
                        total += bytes.size
                        if (total > BackupFormat.MAX_ARCHIVE_BYTES) return ReadResult.Invalid("archive expands too far")
                        if (ImageMagic.detect(bytes) == null) return ReadResult.Invalid("not an image: ${e.name}")
                        images[e.name.removePrefix(IMAGES_DIR)] = bytes
                    }
                }
            }

            val parsed = wordsJsonText?.let { ThemeJson.decode(it) }
            ReadResult.Ok(
                Contents(
                    settingsJson = settings,
                    words = intMap(parsed?.get("words")),
                    bigrams = intMap(parsed?.get("bigrams")),
                    themes = themes,
                    images = images
                )
            )
        }
    }.getOrElse { ReadResult.Invalid(it.message ?: "could not read the archive") }

    /** A file with no manifest is a settings-only backup from an earlier build. */
    private fun legacyOrInvalid(zip: ZipFile, entries: List<ZipEntry>): ReadResult {
        if (entries.size != 1) return ReadResult.Invalid("archive has no manifest")
        val e = entries.first()
        if (e.name != SETTINGS && !e.name.endsWith(".json")) return ReadResult.Invalid("archive has no manifest")
        val text = zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
        val o = ThemeJson.decode(text) ?: return ReadResult.Invalid("not valid JSON")
        if ((o["format"] as? String) != "expressive-keyboard-settings") {
            return ReadResult.Invalid("not a GenZ Keyboard backup")
        }
        return ReadResult.LegacySettings(text)
    }

    private fun intMap(v: Any?): Map<String, Int> {
        val m = v as? Map<*, *> ?: return emptyMap()
        return m.entries.mapNotNull { (k, value) ->
            val key = k as? String ?: return@mapNotNull null
            val n = (value as? Number)?.toInt() ?: return@mapNotNull null
            key to n
        }.toMap()
    }

    private fun wordsJson(words: Map<String, Int>, bigrams: Map<String, Int>): String {
        fun obj(m: Map<String, Int>) = m.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
        return "{\"words\":${obj(words)},\"bigrams\":${obj(bigrams)}}"
    }

    private fun manifestJson(
        settingsJson: String,
        words: Map<String, Int>,
        bigrams: Map<String, Int>,
        themes: List<ThemeDoc>,
        images: Map<String, ByteArray>
    ): String = buildString {
        append("{\"format\":\"$FORMAT\",\"version\":$VERSION,")
        append("\"createdAt\":${System.currentTimeMillis()},")
        append("\"themes\":${themes.size},\"images\":${images.size},")
        append("\"words\":${words.size},\"bigrams\":${bigrams.size}}")
    }

    /**
     * Merge two vocabularies, keeping the higher count.
     *
     * A restore must never reduce what the user has already learned: a backup taken last month would
     * otherwise erase words learned since.
     */
    fun mergeWords(current: Map<String, Int>, incoming: Map<String, Int>): Map<String, Int> {
        val out = LinkedHashMap(current)
        for ((k, v) in incoming) out[k] = maxOf(out[k] ?: 0, v)
        return out
    }

    /**
     * Decide what to do about a theme id that already exists.
     *
     * Never silently overwrite: an identical theme is skipped, a different one is imported as a copy
     * under a fresh id so the user's current version survives.
     */
    fun resolveThemeId(existing: Map<String, ThemeDoc>, incoming: ThemeDoc): String? {
        val current = existing[incoming.id] ?: return incoming.id
        if (current == incoming) return null          // already present, nothing to do
        var n = 1
        while (existing["${incoming.id}.restored-$n"] != null) n++
        return "${incoming.id}.restored-$n"
    }
}
