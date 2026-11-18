package com.genz.keyboard

import java.security.MessageDigest

/**
 * A stable content hash for a theme.
 *
 * Used to stop the same theme being published twice by the same person. It hashes only what makes
 * two themes *the same design* — the base preset and the colour overrides — and deliberately
 * ignores the name, description and wallpaper file name, because re-publishing with a different
 * title is exactly the duplicate this is meant to catch.
 */
object ThemeContentHash {

    /**
     * @return a 32-character hex digest, stable across runs and platforms.
     */
    fun of(doc: ThemeDoc): String {
        val canonical = buildString {
            append(doc.basePreset).append('\u0000')
            // sorted so key order cannot change the hash
            doc.overrides.toSortedMap().forEach { (k, v) ->
                append(k).append('=').append(v).append('\u0001')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /** True when two themes would publish as the same design. */
    fun isDuplicate(a: ThemeDoc, b: ThemeDoc): Boolean = of(a) == of(b)
}
