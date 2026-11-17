package com.genz.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for theme persistence, run against a real temporary directory.
 *
 * `ThemeStore` takes a `File` root rather than a `Context` precisely so this is possible: the CRUD
 * logic is where the bugs live, and proving it should not need a device.
 */
class ThemeStoreTest {

    private lateinit var root: File
    private lateinit var store: ThemeStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("themes").toFile()
        store = ThemeStore(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun doc(id: String = "user.a", name: String = "Mine") =
        ThemeDoc(id, name, "builtin.anime.windmill", mapOf("colors.key" to "#283031"))

    @Test
    fun anEmptyStoreListsNothing() {
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun savingThenLoadingReturnsTheSameDocument() {
        val d = doc()
        assertTrue(store.save(d))
        assertEquals(d, store.load(d.id))
    }

    @Test
    fun savingCreatesTheDirectoryIfMissing() {
        val nested = File(root, "a/b/c")
        val s = ThemeStore(nested)
        assertTrue(s.save(doc()))
        assertTrue(nested.isDirectory)
    }

    @Test
    fun savingTwiceOverwritesRatherThanDuplicating() {
        val d = doc()
        store.save(d)
        store.save(d.copy(name = "Renamed"))
        assertEquals(1, store.list().size)
        assertEquals("Renamed", store.load(d.id)?.name)
    }

    @Test
    fun noTemporaryFileIsLeftBehind() {
        // the write goes through a .tmp file; a crash there must not leave litter
        store.save(doc())
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun aCorruptFileIsSkippedRatherThanCrashingTheList() {
        store.save(doc("user.a"))
        File(root, "broken.json").writeText("{ not json")
        val list = store.list()
        assertEquals(1, list.size)
        assertEquals("user.a", list.first().id)
    }

    @Test
    fun aFileWithAFutureSchemaIsSkipped() {
        store.save(doc("user.a"))
        File(root, "future.json").writeText("""{"schema":9,"id":"user.z","name":"Z","base":"b"}""")
        assertEquals(1, store.list().size)
    }

    @Test
    fun loadingAnUnknownIdReturnsNull() {
        assertNull(store.load("user.nope"))
    }

    @Test
    fun existsReflectsTheFilesystem() {
        val d = doc()
        assertFalse(store.exists(d.id))
        store.save(d)
        assertTrue(store.exists(d.id))
    }

    @Test
    fun deletingRemovesTheTheme() {
        val d = doc()
        store.save(d)
        assertTrue(store.delete(d.id))
        assertFalse(store.exists(d.id))
        assertNull(store.load(d.id))
    }

    @Test
    fun deletingSomethingAbsentIsFalse() {
        assertFalse(store.delete("user.nope"))
    }

    @Test
    fun duplicateCreatesANewThemeWithANewId() {
        val d = doc()
        store.save(d)
        val copy = store.duplicate(d.id, "Mine copy")
        assertNotNull(copy)
        assertEquals("Mine copy", copy!!.name)
        assertFalse("the copy must not overwrite the original", copy.id == d.id)
        assertEquals(2, store.list().size)
        assertEquals(d, store.load(d.id))
    }

    @Test
    fun duplicateKeepsOverridesAndBasePreset() {
        val d = doc()
        store.save(d)
        val copy = store.duplicate(d.id, "Copy")!!
        assertEquals(d.basePreset, copy.basePreset)
        assertEquals(d.overrides, copy.overrides)
    }

    @Test
    fun duplicatingAMissingThemeReturnsNull() {
        assertNull(store.duplicate("user.nope", "Copy"))
    }

    @Test
    fun listIsSortedByNameSoTheUiIsStable() {
        store.save(doc("user.1", "Zebra"))
        store.save(doc("user.2", "alpha"))
        store.save(doc("user.3", "Moon"))
        assertEquals(listOf("alpha", "Moon", "Zebra"), store.list().map { it.name })
    }

    @Test
    fun sizeBytesGrowsWithStoredThemes() {
        val before = store.sizeBytes()
        store.save(doc())
        assertTrue(store.sizeBytes() > before)
    }

    @Test
    fun aThemeSurvivesAFreshStoreInstance() {
        // simulates the app restarting
        store.save(doc())
        assertEquals(1, ThemeStore(root).list().size)
    }
}
