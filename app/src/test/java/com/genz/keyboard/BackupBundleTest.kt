package com.genz.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests for the offline backup container.
 *
 * The failures matter more than the happy path: an archive that is corrupt, hostile or from a newer
 * build must leave the device untouched rather than half-restored.
 */
class BackupBundleTest {

    private lateinit var dir: File

    private fun doc(id: String = "user.1", key: String = "#111111", wallpaper: String? = "wall.webp") =
        ThemeDoc(id, "T", "builtin.preset.windmill", mapOf("colorKey" to key), wallpaper)

    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    )

    private fun write(
        target: File,
        settings: String = """{"format":"expressive-keyboard-settings","version":1}""",
        words: Map<String, Int> = mapOf("en:hello" to 3),
        bigrams: Map<String, Int> = mapOf("en:he llo" to 2),
        themes: List<ThemeDoc> = listOf(doc()),
        images: Map<String, ByteArray> = mapOf("wall.webp" to png)
    ) = BackupBundle.write(target, settings, words, bigrams, themes) { images[it] }

    @Before fun setUp() { dir = Files.createTempDirectory("bundle").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test
    fun anArchiveRoundTrips() {
        val f = File(dir, "b.zip")
        assertTrue(write(f))
        val r = BackupBundle.read(f)
        assertTrue("expected Ok, got $r", r is BackupBundle.ReadResult.Ok)
        val c = (r as BackupBundle.ReadResult.Ok).contents
        assertEquals(1, c.themes.size)
        assertEquals("user.1", c.themes.first().id)
        assertEquals(3, c.words["en:hello"])
        assertEquals(2, c.bigrams["en:he llo"])
        assertNotNull(c.images["wall.webp"])
        assertTrue(c.settingsJson!!.contains("expressive-keyboard-settings"))
    }

    @Test
    fun theWallpaperReferenceIsPreserved() {
        // losing this would leave the theme pointing at nothing after a restore
        val f = File(dir, "b.zip")
        write(f)
        val c = (BackupBundle.read(f) as BackupBundle.ReadResult.Ok).contents
        assertEquals("wall.webp", c.themes.first().wallpaper)
        assertTrue(c.images.containsKey("wall.webp"))
    }

    @Test
    fun aMissingWallpaperDoesNotFailTheBackup() {
        val f = File(dir, "b.zip")
        assertTrue(write(f, images = emptyMap()))
        val c = (BackupBundle.read(f) as BackupBundle.ReadResult.Ok).contents
        assertEquals(1, c.themes.size)
        assertTrue(c.images.isEmpty())
    }

    @Test
    fun aLegacySettingsFileIsStillAccepted() {
        // every backup taken before this feature must keep working
        val f = File(dir, "old.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("settings.json"))
            z.write("""{"format":"expressive-keyboard-settings","version":1}""".toByteArray())
            z.closeEntry()
        }
        val r = BackupBundle.read(f)
        assertTrue("expected LegacySettings, got $r", r is BackupBundle.ReadResult.LegacySettings)
    }

    @Test
    fun anArchiveFromANewerBuildIsRefused() {
        val f = File(dir, "future.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write("""{"format":"genz-backup","version":99}""".toByteArray())
            z.closeEntry()
        }
        assertTrue(BackupBundle.read(f) is BackupBundle.ReadResult.Invalid)
    }

    @Test
    fun aForeignFileIsRefused() {
        val f = File(dir, "foreign.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write("""{"format":"something-else","version":1}""".toByteArray())
            z.closeEntry()
        }
        assertTrue(BackupBundle.read(f) is BackupBundle.ReadResult.Invalid)
    }

    @Test
    fun aTraversalEntryIsRefused() {
        // the classic zip-slip: an entry that would escape the extraction directory
        val f = File(dir, "slip.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write("""{"format":"genz-backup","version":1}""".toByteArray())
            z.closeEntry()
            val evil = ".." + File.separator + "escape.txt"
            z.putNextEntry(ZipEntry(evil))
            z.write("x".toByteArray())
            z.closeEntry()
        }
        assertTrue(BackupBundle.read(f) is BackupBundle.ReadResult.Invalid)
    }

    @Test
    fun anUnexpectedEntryIsRefused() {
        val f = File(dir, "extra.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write("""{"format":"genz-backup","version":1}""".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("plugins/evil.json"))
            z.write("{}".toByteArray())
            z.closeEntry()
        }
        assertTrue(BackupBundle.read(f) is BackupBundle.ReadResult.Invalid)
    }

    @Test
    fun aNonImageInTheImagesDirectoryIsRefused() {
        val f = File(dir, "fake.zip")
        BackupBundle.write(f, "{}", emptyMap(), emptyMap(), listOf(doc())) { "not an image".toByteArray() }
        assertTrue(BackupBundle.read(f) is BackupBundle.ReadResult.Invalid)
    }

    @Test
    fun aCorruptFileIsRefusedRatherThanCrashing() {
        val f = File(dir, "corrupt.zip")
        f.writeText("this is not a zip")
        assertTrue(BackupBundle.read(f) is BackupBundle.ReadResult.Invalid)
    }

    @Test
    fun wordsMergeKeepsTheHigherCount() {
        // a restore must never reduce what the user has already learned
        val merged = BackupBundle.mergeWords(
            mapOf("en:hello" to 5, "en:world" to 1),
            mapOf("en:hello" to 2, "en:new" to 4)
        )
        assertEquals(5, merged["en:hello"])
        assertEquals(1, merged["en:world"])
        assertEquals(4, merged["en:new"])
    }

    @Test
    fun anIdenticalThemeIsSkippedRatherThanDuplicated() {
        val existing = mapOf("user.1" to doc())
        assertNull(BackupBundle.resolveThemeId(existing, doc()))
    }

    @Test
    fun aConflictingThemeIsImportedAsACopy() {
        // never silently overwrite the user's current version
        val existing = mapOf("user.1" to doc())
        assertEquals("user.1.restored-1", BackupBundle.resolveThemeId(existing, doc(key = "#999999")))
    }

    @Test
    fun aSecondConflictPicksAFreeCopyId() {
        val existing = mapOf(
            "user.1" to doc(),
            "user.1.restored-1" to doc(id = "user.1.restored-1")
        )
        assertEquals("user.1.restored-2", BackupBundle.resolveThemeId(existing, doc(key = "#999999")))
    }

    @Test
    fun aNewThemeKeepsItsOwnId() {
        assertEquals("user.9", BackupBundle.resolveThemeId(mapOf("user.1" to doc()), doc(id = "user.9")))
    }
}
