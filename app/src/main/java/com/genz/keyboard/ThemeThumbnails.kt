package com.genz.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thumbnails for the bundled theme artwork.
 *
 * The full-resolution wallpapers are ~740×1300. Decoding nine of those for a scrolling list would cost
 * tens of megabytes and make the theme screen stutter, so every image is subsampled on decode and the
 * results are kept in a small cache.
 *
 * Uses the platform decoder rather than an image library: the requirement is one asset path, one size,
 * and an LRU — a dependency would add weight for nothing.
 */
object ThemeThumbnails {

    /** Decoded at this edge length; a card thumbnail is 40dp, so 2× density headroom is plenty. */
    const val TARGET_PX = 96

    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun cacheSizeBytes(): Int {
        // an eighth of the heap, the usual convention for a bitmap cache
        val max = Runtime.getRuntime().maxMemory()
        return (max / 8).coerceAtMost(8L * 1024 * 1024).toInt()
    }

    /** Clear between tests. */
    internal fun clear() = cache.evictAll()

    /**
     * Decode [assetPath] subsampled to roughly [TARGET_PX].
     *
     * `inSampleSize` is a power-of-two divisor, so the result is at least TARGET_PX and at most twice
     * it — never the full-size image.
     */
    fun load(context: Context, assetPath: String, targetPx: Int = TARGET_PX): Bitmap? {
        cache.get(assetPath)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565   // no alpha in these JPEG-derived wallpapers
        }
        val bitmap = runCatching {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return null

        cache.put(assetPath, bitmap)
        return bitmap
    }

    /** The sample size that would be chosen for these dimensions — pure, so it can be tested. */
    fun sampleSizeFor(width: Int, height: Int, targetPx: Int = TARGET_PX): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) sample *= 2
        return sample
    }
}

/**
 * A theme's artwork, loaded off the main thread and released with the composition.
 *
 * Returns null while loading and when the asset is absent, so the card falls back to its colour block
 * rather than showing an empty hole.
 */
@Composable
internal fun rememberThemeThumbnail(assetPath: String?): ImageBitmap? {
    if (assetPath == null) return null
    val context = LocalContext.current
    val state by produceState<ImageBitmap?>(initialValue = null, assetPath) {
        value = withContext(Dispatchers.IO) {
            ThemeThumbnails.load(context, assetPath)?.asImageBitmap()
        }
    }
    return state
}
