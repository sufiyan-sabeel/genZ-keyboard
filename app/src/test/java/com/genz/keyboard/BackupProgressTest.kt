package com.genz.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for progress reporting and cancellation.
 *
 * A cancelled operation must leave the device exactly as it was — a half-applied restore is worse than
 * no restore at all, so cancellation runs the same rollback path as a failure.
 */
class BackupProgressTest {

    private lateinit var root: File
    private lateinit var themeDir: File
    private lateinit var imageDir: File
    private lateinit var out: File

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
    private fun doc(id: String, key: String = "#111111") =
        ThemeDoc(id, "T", "builtin.preset.windmill", mapOf("colorKey" to key), "wall.webp")

    @Before fun setUp() {
        root = Files.createTempDirectory("prog").toFile()
        themeDir = File(root, "themes").apply { mkdirs() }
        imageDir = File(themeDir, "img").apply { mkdirs() }
        out = File(root, "b.zip")
    }
    @After fun tearDown() { root.deleteRecursively(); BackupOperation.reset() }

    private fun export(file: File = out, themes: List<ThemeDoc> = listOf(doc("user.1"))) =
        BackupService.export(file, "{}", mapOf("a" to 1), emptyMap(), themes, imageBytes = { png })

    @Test
    fun exportReportsProgress() {
        val seen = mutableListOf<Pair<Int, Int>>()
        BackupService.export(out, "{}", emptyMap(), emptyMap(), listOf(doc("a"), doc("b")),
            imageBytes = { png }, progress = { d, t -> seen += d to t })
        assertTrue("progress was never reported", seen.isNotEmpty())
        assertEquals(2, seen.last().second)
    }

    @Test
    fun cancellingAnExportReportsCancelled() {
        val r = BackupService.export(out, "{}", emptyMap(), emptyMap(), listOf(doc("a")),
            imageBytes = { png }, isCancelled = { true })
        assertEquals(BackupService.Result.Cancelled, r)
    }

    @Test
    fun cancellingARestoreLeavesExistingDataUntouched() {
        // the important case: cancel midway must not leave a half-restored device
        ThemeStore(themeDir).save(doc("existing", "#ABCDEF"))
        export(out, listOf(doc("incoming"), doc("incoming2")))

        val r = BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> },
            isCancelled = { true })
        assertEquals(BackupService.Result.Cancelled, r)
        assertEquals("#ABCDEF", ThemeStore(themeDir).load("existing")!!.overrides["colorKey"])
        assertNull("a cancelled restore must not add themes", ThemeStore(themeDir).load("incoming"))
    }

    @Test
    fun aRestoreReportsProgress() {
        export(out, listOf(doc("a"), doc("b")))
        val seen = mutableListOf<Pair<Int, Int>>()
        BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> },
            progress = { d, t -> seen += d to t })
        assertTrue(seen.isNotEmpty())
        assertTrue("progress should advance", seen.last().first >= seen.first().first)
    }

    @Test
    fun cancellationIsCheckedBetweenThemesNotOnlyAtTheStart() {
        // a restore that starts fine and is cancelled later must still roll back
        ThemeStore(themeDir).save(doc("existing", "#ABCDEF"))
        export(out, listOf(doc("t1"), doc("t2"), doc("t3")))
        var calls = 0
        val r = BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> },
            isCancelled = { calls++ > 0 })
        assertEquals(BackupService.Result.Cancelled, r)
        assertEquals("#ABCDEF", ThemeStore(themeDir).load("existing")!!.overrides["colorKey"])
    }

    @Test
    fun anUncancelledRestoreStillSucceeds() {
        export(out, listOf(doc("user.1")))
        val r = BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> }, isCancelled = { false })
        assertTrue(r is BackupService.Result.Ok)
        assertFalse(BackupOperation.isBusy)
    }

    @Test
    fun cancellingBeforeSettingsAreAppliedRollsBackThemes() {
        // stopping at the settings stage must not leave themes without their settings
        ThemeStore(themeDir).save(doc("existing", "#ABCDEF"))
        export(out, listOf(doc("incoming")))
        var settingsApplied = false
        val r = BackupService.restore(out, themeDir, imageDir,
            { settingsApplied = true }, { _, _ -> }, isCancelled = { true })
        assertEquals(BackupService.Result.Cancelled, r)
        assertFalse("settings must not be applied after cancellation", settingsApplied)
        assertEquals("#ABCDEF", ThemeStore(themeDir).load("existing")!!.overrides["colorKey"])
        assertNull(ThemeStore(themeDir).load("incoming"))
    }

    @Test
    fun cancellingBeforeVocabularyIsAppliedSkipsIt() {
        ThemeStore(themeDir).save(doc("existing", "#ABCDEF"))
        export(out, listOf(doc("incoming")))
        var wordsApplied = false
        var calls = 0
        // allow the theme copy to run, then cancel before the vocabulary stage
        val r = BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> wordsApplied = true },
            isCancelled = { calls++ > 1 })
        assertEquals(BackupService.Result.Cancelled, r)
        assertFalse("vocabulary must not be applied after cancellation", wordsApplied)
        assertEquals("#ABCDEF", ThemeStore(themeDir).load("existing")!!.overrides["colorKey"])
    }

    @Test
    fun repeatedCancellationRequestsAreHarmless() {
        // the UI guard prevents duplicates, but the service must tolerate them too
        export(out, listOf(doc("a")))
        val r = BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> },
            isCancelled = { true })
        assertEquals(BackupService.Result.Cancelled, r)
        val again = BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> },
            isCancelled = { true })
        assertEquals(BackupService.Result.Cancelled, again)
    }

    @Test
    fun aCancelledExportLeavesNoStaleTempArchive() {
        val tmp = File(root, "export-tmp.zip")
        BackupService.export(tmp, "{}", emptyMap(), emptyMap(), listOf(doc("a")),
            imageBytes = { png }, isCancelled = { true })
        // a cancelled export must not leave a usable-looking archive behind
        assertFalse("cancelled export wrote a file", tmp.isFile)
    }

    @Test
    fun anUnwritableDestinationFailsClearly() {
        // low storage or an inaccessible folder surfaces as a failure, not a false success
        val blocked = File(root, "missing-dir/deeper/out.zip")
        val r = BackupService.export(blocked, "{}", emptyMap(), emptyMap(), listOf(doc("a")),
            imageBytes = { png })
        assertTrue("expected Failed, got $r", r is BackupService.Result.Failed)
    }

    @Test
    fun aFailureAfterSettingsAreAppliedPutsThemBack() {
        // without this the device would be left with the backup's settings and the old themes
        ThemeStore(themeDir).save(doc("existing", "#ABCDEF"))
        export(out, listOf(doc("incoming")))
        var current = "ORIGINAL"
        val r = BackupService.restore(out, themeDir, imageDir,
            { json -> current = json }, { _, _ -> error("vocabulary write failed") },
            beforeSettings = { "ORIGINAL" },
            beforeWords = { emptyMap<String, Int>() to emptyMap() })
        assertTrue("expected Failed, got $r", r is BackupService.Result.Failed)
        assertEquals("settings must be restored on rollback", "ORIGINAL", current)
        assertEquals("#ABCDEF", ThemeStore(themeDir).load("existing")!!.overrides["colorKey"])
    }

    @Test
    fun aFailureAfterVocabularyIsAppliedPutsItBack() {
        ThemeStore(themeDir).save(doc("existing", "#ABCDEF"))
        export(out, listOf(doc("incoming")))
        var words: Map<String, Int> = mapOf("before" to 1)
        runCatching {
            BackupService.restore(out, themeDir, imageDir, {},
                { w, _ -> words = w; error("later stage failed") },
                beforeWords = { mapOf("before" to 1) to emptyMap() })
        }
        assertEquals("vocabulary must be restored on rollback", mapOf("before" to 1), words)
    }

    @Test
    fun rollbackLeavesNoSnapshotDirectoryBehind() {
        ThemeStore(themeDir).save(doc("existing", "#ABCDEF"))
        export(out, listOf(doc("incoming")))
        BackupService.restore(out, themeDir, imageDir, { error("boom") }, { _, _ -> })
        assertFalse("snapshot dir must be cleaned up",
            File(themeDir.parentFile, "restore-snapshot").exists())
    }
}
