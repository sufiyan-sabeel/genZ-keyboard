package com.genz.keyboard

/**
 * Pure validation of an untrusted backup archive.
 *
 * Every rule here is a function of the archive's contents alone, so the dangerous decisions can be
 * tested without a device, a ZIP or a ContentResolver. The importer is expected to call these
 * *before* touching any application data.
 */
object BackupValidate {

    /** Entry names that are safe: relative, no traversal, no absolute path, no backslash. */
    private val SAFE_PATH = Regex("^[A-Za-z0-9][A-Za-z0-9._/-]{0,119}$")

    /**
     * Normalise an archive entry name.
     *
     * @return the path when it is safe, or null when it could escape the staging directory.
     */
    fun normalisePath(raw: String): String? {
        val p = raw.trim()
        if (p.isEmpty() || p.length > 120) return null
        if (p.contains('\\')) return null
        if (p.startsWith("/") || p.startsWith("./")) return null
        if (p.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
        if (!SAFE_PATH.matches(p)) return null
        return p
    }

    /** True when the path sits in a directory the importer is willing to read. */
    fun isAllowedPath(path: String): Boolean {
        if (BackupFormat.FORBIDDEN_PREFIXES.any { path.startsWith(it) }) return false
        if (path == BackupFormat.MANIFEST || path == BackupFormat.PREFS || path == BackupFormat.DATA) return true
        val ext = path.substringAfterLast('.', "").lowercase()
        return when {
            path.startsWith(BackupFormat.THEMES_DIR) -> ext in BackupFormat.THEME_EXTENSIONS
            path.startsWith(BackupFormat.IMAGES_DIR) -> ext in BackupFormat.IMAGE_EXTENSIONS
            path.startsWith(BackupFormat.ASSETS_DIR) -> ext in BackupFormat.ASSET_EXTENSIONS
            else -> false
        }
    }

    /** Per-entry byte cap, by kind. */
    fun entryCap(path: String): Long = when {
        path.startsWith(BackupFormat.ASSETS_DIR) -> {
            val ext = path.substringAfterLast('.', "").lowercase()
            when (ext) {
                "ttf", "otf" -> BackupFormat.MAX_ASSET_FONT_BYTES
                "ogg", "mp3", "wav" -> BackupFormat.MAX_ASSET_SOUND_BYTES
                else -> BackupFormat.MAX_ASSET_IMAGE_BYTES
            }
        }
        path == BackupFormat.MANIFEST -> BackupFormat.MAX_MANIFEST_BYTES.toLong()
        else -> BackupFormat.MAX_ENTRY_BYTES
    }

    /**
     * Validate the manifest's own header.
     *
     * A future schema is refused rather than partially applied, and a backup taken by an app older
     * than the importer's minimum is refused because it cannot describe settings this app reads.
     */
    fun checkHeader(manifest: BackupManifest, installedVersionCode: Int): BackupCheck {
        if (manifest.schema <= 0) return BackupCheck.Failed("archive schema is missing or invalid")
        if (manifest.schema > BackupFormat.SCHEMA) {
            return BackupCheck.Failed("archive was made by a newer version (schema ${manifest.schema})")
        }
        if (installedVersionCode < BackupFormat.MIN_APP_VERSION_CODE) {
            return BackupCheck.Failed("this app version is too old to import backups")
        }
        if (manifest.appVersionCode < BackupFormat.MIN_APP_VERSION_CODE) {
            return BackupCheck.Failed(
                "archive was made by app version ${manifest.appVersionCode}, older than the supported minimum"
            )
        }
        return BackupCheck.Ok
    }

    /**
     * Validate the entry list: safe paths, allowed kinds, no duplicates, sizes within cap.
     *
     * Duplicate detection is case-insensitive because two entries differing only in case would
     * collide on a case-insensitive filesystem during extraction.
     */
    fun checkEntries(entries: List<ManifestEntry>): BackupCheck {
        if (entries.size > BackupFormat.MAX_ENTRIES) {
            return BackupCheck.Failed("archive has more than ${BackupFormat.MAX_ENTRIES} entries")
        }
        val seen = HashSet<String>(entries.size)
        var total = 0L
        for (e in entries) {
            val path = normalisePath(e.path) ?: return BackupCheck.Failed("unsafe path: ${e.path}")
            if (!isAllowedPath(path)) return BackupCheck.Failed("unexpected file in archive: $path")
            if (!seen.add(path.lowercase())) return BackupCheck.Failed("duplicate entry: $path")
            if (e.bytes > entryCap(path)) return BackupCheck.Failed("entry too large: $path")
            total += e.bytes
            if (total > BackupFormat.MAX_ARCHIVE_BYTES) {
                return BackupCheck.Failed("archive expands beyond the size limit")
            }
        }
        val themes = entries.count { it.path.startsWith(BackupFormat.THEMES_DIR) }
        if (themes > BackupFormat.MAX_THEMES) return BackupCheck.Failed("too many themes")
        val images = entries.count { it.path.startsWith(BackupFormat.IMAGES_DIR) }
        if (images > BackupFormat.MAX_IMAGES) return BackupCheck.Failed("too many images")
        return BackupCheck.Ok
    }

    /**
     * Cross-check the archive's actual payload list against the manifest.
     *
     * A file present in the ZIP but absent from the manifest is rejected: it is exactly how an
     * archive would smuggle in a payload the importer was never told about. A manifest record with
     * no file is equally rejected.
     */
    fun checkAgainstArchive(manifest: List<ManifestEntry>, actualPaths: List<String>): BackupCheck {
        val declared = manifest.map { it.path.lowercase() }.toSet()
        val actual = actualPaths
            .filter { it != BackupFormat.MANIFEST }
            .map { it.lowercase() }
            .toSet()
        val unlisted = actual - declared
        if (unlisted.isNotEmpty()) return BackupCheck.Failed("archive contains files not in the manifest: ${unlisted.first()}")
        val missing = declared - actual
        if (missing.isNotEmpty()) return BackupCheck.Failed("manifest lists a missing file: ${missing.first()}")
        return BackupCheck.Ok
    }

    /** Outcome of validating preference values. */
    data class PrefsResult(
        val accepted: Map<String, Any>,
        val dropped: List<String>,
        val rejectedSecret: List<String>
    )

    /**
     * Validate preference values against the allowlist.
     *
     * Unknown keys are dropped and reported rather than applied, so an archive from a different
     * build cannot inject settings. Secret keys are stripped unconditionally, even when the value
     * is well-formed.
     */
    fun checkPrefs(raw: Map<String, Any?>): PrefsResult {
        val accepted = LinkedHashMap<String, Any>()
        val dropped = ArrayList<String>()
        val secrets = ArrayList<String>()
        for ((key, value) in raw) {
            if (key in BackupFormat.FORBIDDEN_KEYS) { secrets += key; continue }
            val type = BackupFormat.PREF_TYPES[key]
            if (type == null) { dropped += key; continue }
            val coerced = coerce(value, type)
            if (coerced == null) { dropped += key; continue }
            accepted[key] = coerced
        }
        return PrefsResult(accepted, dropped, secrets)
    }

    /** @return the value coerced to the declared type, or null when it does not fit. */
    private fun coerce(value: Any?, type: PrefType): Any? = when (type) {
        PrefType.STRING -> value as? String
        PrefType.BOOL -> value as? Boolean
        PrefType.INT -> (value as? Number)?.toInt()
        PrefType.LONG -> (value as? Number)?.toLong()
        PrefType.FLOAT -> (value as? Number)?.toFloat()
    }
}
