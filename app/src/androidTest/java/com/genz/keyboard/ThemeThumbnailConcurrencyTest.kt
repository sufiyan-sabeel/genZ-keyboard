package com.genz.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Thumbnail loading under concurrency, on a real device.
 *
 * These need a real `BitmapFactory` — the JVM stub cannot decode — so they live here rather than in
 * the unit tests. The property that matters is that N simultaneous requests for one asset perform one
 * decode and all receive the same bitmap, because a card scrolling out and back was previously able
 * to start a second decode of the same artwork.
 */
@RunWith(AndroidJUnit4::class)
class ThemeThumbnailConcurrencyTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val asset = "anime/art_sunset.webp"

    @Before fun setUp() = ThemeThumbnails.clear()
    @After fun tearDown() = ThemeThumbnails.clear()

    @Test
    fun theBundledArtworkIsReadable() {
        // if this fails the rest of the suite is meaningless
        val bitmap = ThemeThumbnails.load(context, asset)
        assertNotNull("could not decode $asset", bitmap)
    }

    @Test
    fun theThumbnailIsSubsampledNotFullSize() {
        val bitmap = ThemeThumbnails.load(context, asset)!!
        // the source is ~736x1308; a thumbnail must be far smaller in both axes
        assert(bitmap.width < 736) { "thumbnail was not subsampled: ${bitmap.width}px wide" }
        assert(bitmap.height < 1308) { "thumbnail was not subsampled: ${bitmap.height}px tall" }
    }

    @Test
    fun concurrentRequestsForOneAssetShareASingleResult() = runBlocking {
        val results = (1..8).map {
            async { ThemeThumbnails.loadAsync(context, asset) }
        }.awaitAll()

        assertTrueAllPresent(results)
        // every caller must receive the very same cached bitmap instance
        val first = results.first()!!
        results.forEach { assertSame("callers received different bitmaps", first, it) }
    }

    @Test
    fun repeatedLoadsHitTheCacheRatherThanDecodingAgain() = runBlocking {
        val first = ThemeThumbnails.loadAsync(context, asset)
        val second = ThemeThumbnails.loadAsync(context, asset)
        assertSame(first, second)
    }

    @Test
    fun aMissingAssetReturnsNullAndLeavesNothingPending() = runBlocking {
        assertNull(ThemeThumbnails.loadAsync(context, "anime/does_not_exist.webp"))
        // a second attempt must also return promptly rather than waiting on a stale entry
        assertNull(ThemeThumbnails.loadAsync(context, "anime/does_not_exist.webp"))
    }

    @Test
    fun concurrentRequestsForAMissingAssetAllResolveNull() = runBlocking {
        val results = (1..8).map {
            async { ThemeThumbnails.loadAsync(context, "anime/missing_asset.webp") }
        }.awaitAll()
        results.forEach { assertNull("a missing asset must resolve null, not hang", it) }
    }

    @Test
    fun clearingTheCacheForcesAFreshDecode() = runBlocking {
        val first = ThemeThumbnails.loadAsync(context, asset)
        ThemeThumbnails.clear()
        val second = ThemeThumbnails.loadAsync(context, asset)
        assertNotNull(second)
        // a different instance proves the cache was actually cleared
        assertEquals(false, first === second)
    }

    private fun assertTrueAllPresent(results: List<android.graphics.Bitmap?>) {
        results.forEachIndexed { i, b -> assertNotNull("request $i returned null", b) }
    }
}
