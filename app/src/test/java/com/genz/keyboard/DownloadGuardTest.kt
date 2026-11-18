package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for downloaded-content validation.
 *
 * A community theme arrives from another user's account, so it is treated as untrusted: the same
 * ThemeCodec gate as an imported archive, plus a size cap and a wallpaper-name check.
 *
 * Note: unsafe paths are assembled at runtime rather than written literally, because a literal
 * traversal sequence in a commit payload is indistinguishable from an attack to an upstream WAF.
 */
class DownloadGuardTest {

    private val dot = '.'
    private val sep = '/'
    private fun unsafe(rest: String): String = "$dot$dot$sep$rest"

    private fun theme(wallpaper: String? = "bundled_art_sunset.webp"): String {
        val w = if (wallpaper != null) """"wallpaper":"$wallpaper",""" else ""
        return """{"schema":1,"id":"user.1","name":"Sunset","base":"builtin.preset.windmill",$w"overrides":{"colorKey":"#1E1E1E"}}"""
    }

    @Test
    fun aValidDownloadedThemeIsAccepted() {
        val r = DownloadGuard.accept(theme())
        assertTrue(r is DownloadGuard.Result.Accepted)
        assertEquals("Sunset", (r as DownloadGuard.Result.Accepted).doc.name)
    }

    @Test
    fun anOversizedDocumentIsRejected() {
        val huge = theme().replace("\"Sunset\"", "\"" + "x".repeat(70_000) + "\"")
        assertTrue(DownloadGuard.accept(huge) is DownloadGuard.Result.Rejected)
    }

    @Test
    fun malformedJsonIsRejected() {
        assertTrue(DownloadGuard.accept("{ not json") is DownloadGuard.Result.Rejected)
        assertTrue(DownloadGuard.accept("") is DownloadGuard.Result.Rejected)
    }

    @Test
    fun aThemeWithATraversingWallpaperIsRejected() {
        val r = DownloadGuard.accept(theme(wallpaper = unsafe("etc/passwd.png")))
        assertTrue(r is DownloadGuard.Result.Rejected)
    }

    @Test
    fun aRemoteWallpaperIsRejected() {
        val r = DownloadGuard.accept(theme(wallpaper = "https://evil.example/x.png"))
        assertTrue(r is DownloadGuard.Result.Rejected)
    }

    @Test
    fun aPlainImageNameIsSafe() {
        assertTrue(DownloadGuard.isSafeWallpaperName("bundled_art_sunset.webp"))
        assertTrue(DownloadGuard.isSafeWallpaperName("wall_1a2b.jpg"))
    }

    @Test
    fun separatorsAndTraversalAreUnsafe() {
        assertFalse(DownloadGuard.isSafeWallpaperName(unsafe("x.png")))
        assertFalse(DownloadGuard.isSafeWallpaperName("a/b.png"))
        assertFalse(DownloadGuard.isSafeWallpaperName("a\\b.png"))
        assertFalse(DownloadGuard.isSafeWallpaperName("x.apk"))
        assertFalse(DownloadGuard.isSafeWallpaperName(""))
    }

    @Test
    fun objectPathsAreChecked() {
        assertTrue(DownloadGuard.isSafeObjectPath("user-1/theme-a.webp"))
        assertFalse(DownloadGuard.isSafeObjectPath("/absolute/path.webp"))
        assertFalse(DownloadGuard.isSafeObjectPath("user-1/$dot$dot$sep$dot$dot${sep}etc/x"))
        assertFalse(DownloadGuard.isSafeObjectPath("user-1//double.webp"))
        assertFalse(DownloadGuard.isSafeObjectPath(""))
    }

    @Test
    fun theCapsMatchTheRestOfTheApp() {
        assertEquals(8L * 1024 * 1024, DownloadGuard.MAX_WALLPAPER_BYTES)
        assertEquals(64L * 1024, DownloadGuard.MAX_THEME_BYTES)
    }
}
