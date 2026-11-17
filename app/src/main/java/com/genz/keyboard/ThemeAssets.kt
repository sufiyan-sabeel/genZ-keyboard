package com.genz.keyboard

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Local asset handling for themes: wallpaper import and theme file import/export.
 *
 * Every entry point is untrusted. Images are size- and type-checked and copied into app storage
 * under a hashed name, so a theme can only ever reference a file this app created — never a path
 * the user typed and never a remote URL.
 */
object ThemeAssets {

    /** 8 MB: comfortably above a phone wallpaper, far below anything that would exhaust memory. */
    const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

    /** 64 KB of JSON, matching ThemeDoc.MAX_JSON_BYTES. */
    const val MAX_THEME_BYTES = ThemeDoc.MAX_JSON_BYTES.toLong()

    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun imageDir(context: Context): File = File(context.filesDir, "themes/img")

    fun themeDir(context: Context): File = File(context.filesDir, "themes")

    /** Extension of a MIME type, or null when the type is not an accepted image. */
    fun extensionForMime(mime: String?): String? = when (mime?.lowercase()) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> null
    }

    /** A stored wallpaper name must be a bare file name with an accepted extension. */
    fun isValidWallpaperName(name: String): Boolean {
        if (name.isEmpty() || name.length > 80) return false
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    fun wallpaperFile(context: Context, name: String): File? =
        if (isValidWallpaperName(name)) File(imageDir(context), name) else null

    /**
     * Copy a picked image into app storage.
     *
     * @return the stored file name, or null when the pick failed, was too large, or was not an
     * accepted image type. Never throws.
     */
    fun importWallpaper(context: Context, uri: Uri): String? = runCatching {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val ext = extensionForMime(mime) ?: return null

        // Size check before reading, so a huge file is rejected without loading it.
        val declared = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (declared > MAX_IMAGE_BYTES) return null

        val dir = imageDir(context).apply { if (!isDirectory) mkdirs() }
        val name = "wall_" + Integer.toHexString((uri.toString() + System.currentTimeMillis()).hashCode()) + "." + ext
        val target = File(dir, name)
        var copied = 0L
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    copied += n
                    if (copied > MAX_IMAGE_BYTES) {
                        out.close()
                        target.delete()
                        return null
                    }
                    out.write(buf, 0, n)
                }
            }
        } ?: return null
        if (copied <= 0) {
            target.delete()
            return null
        }
        name
    }

    /** Read an imported theme file, capped at [MAX_THEME_BYTES]. Null when unreadable. */
    fun readTheme(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_THEME_BYTES) null else String(bytes)
        }
    }.getOrNull()

    /** Write a theme file to a user-chosen destination. */
    fun writeTheme(context: Context, uri: Uri, json: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out -> out.write(json.toByteArray()) }
        true
    }.getOrDefault(false)

    /** Delete a stored wallpaper. Returns true when a file was removed. */
    fun deleteWallpaper(context: Context, name: String): Boolean =
        wallpaperFile(context, name)?.let { it.isFile && it.delete() } ?: false
}
