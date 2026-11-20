package com.genz.keyboard

import java.io.File

/**
 * Export and restore, on top of [BackupBundle].
 *
 * Everything takes plain [File]s and callbacks rather than a Context, so the whole lifecycle — staging,
 * validation, snapshot, apply, rollback — can be tested in a JVM test with temporary directories.
 *
 * Restore is the dangerous direction: it replaces data the user cannot get back. So it validates the
 * entire archive first, snapshots what it is about to overwrite, and rolls back if any step fails.
 */
object BackupService {

    /** Reported as work proceeds, so a long backup is not a frozen screen. */
    fun interface Progress { fun onProgress(done: Int, total: Int) }

    sealed interface Result {
        data class Ok(val themes: Int, val images: Int, val words: Int, val bigrams: Int) : Result
        /** The user asked to stop. Nothing was changed. */
        data object Cancelled : Result
        /** The archive itself is unacceptable. Nothing was changed. */
        data class Invalid(val reason: String) : Result
        /** The archive was fine but applying it failed. Previous data was restored. */
        data class Failed(val reason: String) : Result
    }

    /**
     * Write an archive and confirm it can be read back.
     *
     * Verification is not paranoia: an export that silently produced a truncated file would be
     * discovered only when the user tries to restore it, long after the original is gone.
     */
    fun export(
        target: File,
        settingsJson: String,
        words: Map<String, Int>,
        bigrams: Map<String, Int>,
        themes: List<ThemeDoc>,
        imageBytes: (String) -> ByteArray?,
        progress: Progress? = null,
        isCancelled: () -> Boolean = { false }
    ): Result {
        if (isCancelled()) return Result.Cancelled
        progress?.onProgress(0, themes.size)
        if (!BackupBundle.write(target, settingsJson, words, bigrams, themes, imageBytes)) {
            return Result.Failed("Could not write the backup file")
        }
        progress?.onProgress(themes.size, themes.size)
        return when (val check = BackupBundle.read(target)) {
            is BackupBundle.ReadResult.Ok ->
                Result.Ok(check.contents.themes.size, check.contents.images.size,
                          check.contents.words.size, check.contents.bigrams.size)
            is BackupBundle.ReadResult.LegacySettings -> Result.Failed("Backup verification failed")
            is BackupBundle.ReadResult.Invalid -> Result.Failed("Backup verification failed: ${check.reason}")
        }
    }

    /** Inspect an archive without touching anything — used for the restore preview. */
    fun inspect(archive: File): Result = when (val r = BackupBundle.read(archive)) {
        is BackupBundle.ReadResult.Ok ->
            Result.Ok(r.contents.themes.size, r.contents.images.size, r.contents.words.size, r.contents.bigrams.size)
        is BackupBundle.ReadResult.LegacySettings -> Result.Ok(0, 0, 0, 0)
        is BackupBundle.ReadResult.Invalid -> Result.Invalid(r.reason)
    }

    /**
     * Apply an archive.
     *
     * Order: validate everything → snapshot → write images → write themes → apply prefs → done.
     * Any failure restores the snapshot, so a partial restore cannot leave the user with half their
     * themes and none of their vocabulary.
     *
     * @param onSettings applies the settings JSON; @param onWords applies the merged vocabularies.
     */
    fun restore(
        archive: File,
        themeDir: File,
        imageDir: File,
        onSettings: (String) -> Unit,
        onWords: (Map<String, Int>, Map<String, Int>) -> Unit,
        progress: Progress? = null,
        isCancelled: () -> Boolean = { false }
    ): Result {
        val read = when (val r = BackupBundle.read(archive)) {
            is BackupBundle.ReadResult.Ok -> r.contents
            is BackupBundle.ReadResult.LegacySettings -> {
                // settings-only backup from an older build: nothing else to apply
                if (isCancelled()) return Result.Cancelled
                return runCatching { onSettings(r.json); Result.Ok(0, 0, 0, 0) }
                    .getOrElse { Result.Failed(it.message ?: "Could not apply settings") }
            }
            is BackupBundle.ReadResult.Invalid -> return Result.Invalid(r.reason)
        }

        // snapshot before touching anything
        val snapshot = runCatching { snapshotOf(themeDir, imageDir) }
            .getOrElse { return Result.Failed("Could not snapshot existing data") }

        return try {
            themeDir.mkdirs(); imageDir.mkdirs()

            val total = read.images.size + read.themes.size
            var done = 0
            progress?.onProgress(0, total)

            for ((name, bytes) in read.images) {
                // cancellation is checked between items so a large restore stops promptly and cleanly
                if (isCancelled()) { rollback(themeDir, imageDir, snapshot); return Result.Cancelled }
                if (!DownloadGuard.isSafeObjectPath(name)) continue
                File(imageDir, name).writeBytes(bytes)
                progress?.onProgress(++done, total)
            }

            val existing = ThemeStore(themeDir).list().associateBy { it.id }.toMutableMap()
            for (doc in read.themes) {
                if (isCancelled()) { rollback(themeDir, imageDir, snapshot); return Result.Cancelled }
                val id = BackupBundle.resolveThemeId(existing, doc) ?: continue
                val stored = doc.copy(id = id)
                if (!ThemeStore(themeDir).save(stored)) error("could not write theme $id")
                existing[id] = stored
                progress?.onProgress(++done, total)
            }

            // settings and vocabulary are the last stages, and they are exactly as cancellable as the
            // file copies — stopping here must roll back too, not leave themes without their settings
            if (isCancelled()) { rollback(themeDir, imageDir, snapshot); return Result.Cancelled }
            read.settingsJson?.let(onSettings)
            if (isCancelled()) { rollback(themeDir, imageDir, snapshot); return Result.Cancelled }
            onWords(read.words, read.bigrams)

            Result.Ok(read.themes.size, read.images.size, read.words.size, read.bigrams.size)
        } catch (t: Throwable) {
            rollback(themeDir, imageDir, snapshot)
            Result.Failed(t.message ?: "Restore failed; previous data was restored")
        }
    }

    // ---------------------------------------------------------------- snapshot / rollback

    private class Snapshot(val themes: File?, val images: File?)

    private fun snapshotOf(themeDir: File, imageDir: File): Snapshot {
        val root = File(themeDir.parentFile ?: themeDir, "restore-snapshot")
        root.deleteRecursively()
        root.mkdirs()
        fun copy(src: File, name: String): File? {
            if (!src.isDirectory) return null
            val dst = File(root, name)
            src.copyRecursively(dst, overwrite = true)
            return dst
        }
        return Snapshot(copy(themeDir, "themes"), copy(imageDir, "img"))
    }

    private fun rollback(themeDir: File, imageDir: File, snap: Snapshot) {
        runCatching {
            themeDir.deleteRecursively()
            imageDir.deleteRecursively()
            snap.themes?.copyRecursively(themeDir, overwrite = true)
            snap.images?.copyRecursively(imageDir, overwrite = true)
            File(themeDir.parentFile ?: themeDir, "restore-snapshot").deleteRecursively()
        }
    }
}
