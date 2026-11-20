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
}
