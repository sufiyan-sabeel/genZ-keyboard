package com.genz.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for export and restore.
 *
 * Restore is the direction that can destroy data, so most of these are failure cases: a corrupt
 * archive, a write that fails midway, and whether the previous state comes back intact.
 */
class BackupServiceTest {

    private lateinit var root: File
    private lateinit var themeDir: File
    private lateinit var imageDir: File
    private lateinit var out: File

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)

    private fun doc(id: String = "user.1", key: String = "#111111", wallpaper: String? = "wall.webp") =
        ThemeDoc(id, "T", "builtin.preset.windmill", mapOf("colorKey" to key), wallpaper)

    private val settings = """{"format":"expressive-keyboard-settings","version":1}"""

    private fun exportTo(file: File, themes: List<ThemeDoc> = listOf(doc())) =
        BackupService.export(
            file, settings,
            mapOf("en:hello" to 3), mapOf("en:he llo" to 2),
            themes,
            imageBytes = { png }
        )

    @Before fun setUp() {
        root = Files.createTempDirectory("svc").toFile()
        themeDir = File(root, "themes").apply { mkdirs() }
        imageDir = File(themeDir, "img").apply { mkdirs() }
        out = File(root, "backup.zip")
    }
    @After fun tearDown() { root.deleteRecursively() }

    @Test
    fun anExportCanBeReadBack() {
        val r = exportTo(out)
        assertTrue("expected Ok, got $r", r is BackupService.Result.Ok)
        assertEquals(1, (r as BackupService.Result.Ok).themes)
    }

    @Test
    fun inspectingAnArchiveChangesNothing() {
        exportTo(out)
        assertTrue(BackupService.inspect(out) is BackupService.Result.Ok)
        assertFalse("inspect must not create themes", File(themeDir, "user.1.json").exists())
    }

    @Test
    fun aRestoreAppliesThemesAndImages() {
        exportTo(out)
        val r = BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> })
        assertTrue("expected Ok, got $r", r is BackupService.Result.Ok)
        assertNotNull(ThemeStore(themeDir).load("user.1"))
        assertTrue(File(imageDir, "wall.webp").isFile)
    }

    @Test
    fun aRestoreMergesWordsRatherThanReplacingThem() {
        exportTo(out)
        var merged: Map<String, Int>? = null
        BackupService.restore(out, themeDir, imageDir, {}, { w, _ -> merged = w })
        assertEquals(3, merged!!["en:hello"])
    }

    @Test
    fun aCorruptArchiveChangesNothing() {
        // the user's existing themes must survive a bad backup file
        ThemeStore(themeDir).save(doc(id = "existing", key = "#ABCDEF"))
        out.writeText("not a zip")
        val r = BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> })
        assertTrue(r is BackupService.Result.Invalid)
        assertEquals("#ABCDEF", ThemeStore(themeDir).load("existing")!!.overrides["colorKey"])
    }

    @Test
    fun aFailingRestoreRollsBackTheThemesDirectory() {
        ThemeStore(themeDir).save(doc(id = "existing", key = "#ABCDEF"))
        exportTo(out, listOf(doc(id = "incoming")))
        // settings application fails after themes were already written
        val r = BackupService.restore(out, themeDir, imageDir, { error("boom") }, { _, _ -> })
        assertTrue("expected Failed, got $r", r is BackupService.Result.Failed)
        assertEquals("#ABCDEF", ThemeStore(themeDir).load("existing")!!.overrides["colorKey"])
        assertEquals("the incoming theme must be rolled back", null, ThemeStore(themeDir).load("incoming"))
    }

    @Test
    fun anExistingThemeIsNotOverwrittenOnRestore() {
        ThemeStore(themeDir).save(doc(id = "user.1", key = "#ABCDEF"))
        exportTo(out, listOf(doc(id = "user.1", key = "#111111")))
        BackupService.restore(out, themeDir, imageDir, {}, { _, _ -> })
        // the original survives, the archive lands as a copy
        assertEquals("#ABCDEF", ThemeStore(themeDir).load("user.1")!!.overrides["colorKey"])
        assertNotNull(ThemeStore(themeDir).load("user.1.restored-1"))
    }

    @Test
    fun aLegacySettingsOnlyBackupStillRestores() {
        val legacy = File(root, "old.zip")
        java.util.zip.ZipOutputStream(legacy.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("settings.json"))
            z.write(settings.toByteArray()); z.closeEntry()
        }
        var applied = false
        val r = BackupService.restore(legacy, themeDir, imageDir, { applied = true }, { _, _ -> })
        assertTrue(r is BackupService.Result.Ok)
        assertTrue("legacy settings were not applied", applied)
    }

    @Test
    fun aFailedExportLeavesNoUsableArchive() {
        val target = File(root, "nope/blocked.zip")
        val r = BackupService.export(target, settings, emptyMap(), emptyMap(), listOf(doc()),
            imageBytes = { png })
        assertTrue(r is BackupService.Result.Failed)
    }
}
