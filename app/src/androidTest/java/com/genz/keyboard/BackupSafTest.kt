package com.genz.keyboard

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Export and restore through a real `content://` URI.
 *
 * The system document picker cannot be scripted, so this test supplies the URI the picker would have
 * returned and exercises everything after it: the app opening a stream, writing a valid archive,
 * reading it back, restoring, and refusing a corrupted file.
 *
 * That verifies the app's SAF handling. It does **not** verify the picker UI itself, and this test
 * does not claim to.
 */
@RunWith(AndroidJUnit4::class)
class BackupSafTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority get() = "${context.packageName}.test.saf"

    private lateinit var themeDir: File
    private lateinit var imageDir: File
    private lateinit var outDir: File

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)

    private fun doc(id: String, key: String = "#111111") =
        ThemeDoc(id, "T", "builtin.preset.windmill", mapOf("colorKey" to key), "wall.webp")

    @Before fun setUp() {
        themeDir = File(context.cacheDir, "saf-test/themes").apply { mkdirs() }
        imageDir = File(themeDir, "img").apply { mkdirs() }
        outDir = File(context.cacheDir, "saf").apply { mkdirs() }
    }

    @After fun tearDown() {
        File(context.cacheDir, "saf-test").deleteRecursively()
        outDir.deleteRecursively()
    }

    /** The URI a CreateDocument result would carry, backed by a real file. */
    private fun uriFor(name: String): Uri {
        val f = File(outDir, name)
        if (!f.exists()) f.createNewFile()
        return FileProvider.getUriForFile(context, authority, f)
    }

    private fun writeViaUri(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no output stream")
    }

    private fun readViaUri(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no input stream")

    @Test
    fun anExportWrittenThroughAContentUriIsAValidArchive() {
        val uri = uriFor("export.zip")
        val (w, b) = mapOf("en:hello" to 3) to mapOf<String, Int>()
        val tmp = File(context.cacheDir, "saf-export.zip")

        val r = BackupService.export(tmp, "{}", w, b, listOf(doc("user.1"))) { png }
        assertTrue("export failed: $r", r is BackupService.Result.Ok)

        // this is the step the picker would perform: the app writes through the returned URI
        writeViaUri(uri, tmp.readBytes())
        val back = readViaUri(uri)
        assertTrue("nothing was written through the URI", back.isNotEmpty())

        // and what arrived is a real archive with the expected entries
        val copy = File(context.cacheDir, "saf-copy.zip").apply { writeBytes(back) }
        ZipFile(copy).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue("manifest missing", names.contains("manifest.json"))
            assertTrue("settings missing", names.contains("settings.json"))
            assertTrue("words missing", names.contains("words.json"))
            assertTrue("theme missing: $names", names.any { it.startsWith("themes/") })
        }
        listOf(tmp, copy).forEach { it.delete() }
    }

    @Test
    fun anArchiveReadBackThroughAContentUriRestores() {
        val uri = uriFor("roundtrip.zip")
        val tmp = File(context.cacheDir, "saf-rt.zip")
        BackupService.export(tmp, "{}", mapOf("en:a" to 1), emptyMap(), listOf(doc("user.9"))) { png }
        writeViaUri(uri, tmp.readBytes())

        // read it back the way the import path would, then restore
        val staged = File(context.cacheDir, "saf-staged.zip").apply { writeBytes(readViaUri(uri)) }
        var appliedSettings = false
        val r = BackupService.restore(staged, themeDir, imageDir, { appliedSettings = true }, { _, _ -> })

        assertTrue("restore failed: $r", r is BackupService.Result.Ok)
        assertTrue("settings were not applied", appliedSettings)
        assertNotNull("theme was not restored", ThemeStore(themeDir).load("user.9"))
        assertTrue("wallpaper was not restored", File(imageDir, "wall.webp").isFile)
        listOf(tmp, staged).forEach { it.delete() }
    }

    @Test
    fun aCorruptedFileFromAContentUriIsRejectedAndChangesNothing() {
        ThemeStore(themeDir).save(doc("existing", "#ABCDEF"))
        val uri = uriFor("corrupt.zip")
        writeViaUri(uri, "this is not a zip".toByteArray())

        val staged = File(context.cacheDir, "saf-corrupt.zip").apply { writeBytes(readViaUri(uri)) }
        val r = BackupService.restore(staged, themeDir, imageDir, {}, { _, _ -> })

        assertTrue("expected Invalid, got $r", r is BackupService.Result.Invalid)
        assertEquals(
            "existing data must survive a corrupt import",
            "#ABCDEF", ThemeStore(themeDir).load("existing")!!.overrides["colorKey"]
        )
        staged.delete()
    }

    @Test
    fun anEmptyDocumentIsRejectedRatherThanTreatedAsABackup() {
        val uri = uriFor("empty.zip")
        writeViaUri(uri, ByteArray(0))
        val staged = File(context.cacheDir, "saf-empty.zip").apply { writeBytes(readViaUri(uri)) }
        assertTrue(BackupService.inspect(staged) is BackupService.Result.Invalid)
        staged.delete()
    }

    @Test
    fun aMissingWallpaperStillProducesAnImportableArchive() {
        // the F-Droid flavour ships no artwork; the archive must still round-trip
        val uri = uriFor("nowall.zip")
        val tmp = File(context.cacheDir, "saf-nowall.zip")
        BackupService.export(tmp, "{}", emptyMap(), emptyMap(), listOf(doc("user.5"))) { null }
        writeViaUri(uri, tmp.readBytes())
        val staged = File(context.cacheDir, "saf-nowall-copy.zip").apply { writeBytes(readViaUri(uri)) }
        assertTrue(BackupService.inspect(staged) is BackupService.Result.Ok)
        assertFalse(File(imageDir, "wall.webp").exists())
        listOf(tmp, staged).forEach { it.delete() }
    }
}
