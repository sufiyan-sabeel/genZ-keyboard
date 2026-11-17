package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the wallpaper and theme-file validation rules.
 *
 * These are the guards that stop a theme referencing a file the app did not create: a stored
 * wallpaper name must be a bare file name with an accepted image extension, and nothing else.
 */
class ThemeAssetsTest {

    @Test
    fun acceptedImageTypesMapToExtensions() {
        assertEquals("png", ThemeAssets.extensionForMime("image/png"))
        assertEquals("jpg", ThemeAssets.extensionForMime("image/jpeg"))
        assertEquals("webp", ThemeAssets.extensionForMime("image/webp"))
    }

    @Test
    fun otherTypesAreRejected() {
        assertNull(ThemeAssets.extensionForMime("application/pdf"))
        assertNull(ThemeAssets.extensionForMime("image/svg+xml"))
        assertNull(ThemeAssets.extensionForMime(null))
    }

    @Test
    fun aPlainImageNameIsValid() {
        assertTrue(ThemeAssets.isValidWallpaperName("wall_1a2b.png"))
        assertTrue(ThemeAssets.isValidWallpaperName("wall_ff.jpg"))
        assertTrue(ThemeAssets.isValidWallpaperName("wall_00.webp"))
    }

    @Test
    fun pathsAndTraversalAreRejected() {
        assertFalse(ThemeAssets.isValidWallpaperName("../secret.png"))
        assertFalse(ThemeAssets.isValidWallpaperName("/etc/passwd.png"))
        assertFalse(ThemeAssets.isValidWallpaperName("dir/wall.png"))
        assertFalse(ThemeAssets.isValidWallpaperName("dir\\wall.png"))
    }

    @Test
    fun nonImageExtensionsAreRejected() {
        assertFalse(ThemeAssets.isValidWallpaperName("wall.apk"))
        assertFalse(ThemeAssets.isValidWallpaperName("wall.json"))
        assertFalse(ThemeAssets.isValidWallpaperName("wall"))
        assertFalse(ThemeAssets.isValidWallpaperName(""))
    }

    @Test
    fun absurdlyLongNamesAreRejected() {
        assertFalse(ThemeAssets.isValidWallpaperName("a".repeat(100) + ".png"))
    }

    @Test
    fun theSizeCapsAreSaneAndMatchTheThemeCodec() {
        assertEquals(ThemeDoc.MAX_JSON_BYTES.toLong(), ThemeAssets.MAX_THEME_BYTES)
        assertTrue(ThemeAssets.MAX_IMAGE_BYTES in 1..(32L * 1024 * 1024))
    }
}
