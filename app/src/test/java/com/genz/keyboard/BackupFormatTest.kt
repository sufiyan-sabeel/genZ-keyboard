package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the backup manifest codec and the preference schema.
 *
 * The manifest is the archive's authority over its own contents, so a malformed or hostile one must
 * be refused before anything is extracted.
 */
class BackupFormatTest {

    private fun manifest(
        schema: Int = BackupFormat.SCHEMA,
        code: Int = 12,
        entries: List<ManifestEntry> = listOf(
            ManifestEntry("themes/user.1.json", "a".repeat(64), 512)
        )
    ) = BackupManifest(
        schema = schema,
        appVersionCode = code,
        appVersionName = "1.0.7",
        exportedAt = "2026-11-18T00:00:00Z",
        entries = entries,
        includesThemes = true,
        includesPreferences = true
    )

    // ---------------------------------------------------------------- round trip

    @Test
    fun aManifestSurvivesEncodeAndDecode() {
        val m = manifest()
        val back = BackupManifestCodec.decode(BackupManifestCodec.encode(m))
        assertEquals(m, back)
    }

    @Test
    fun optionalFlagsDefaultToFalse() {
        val back = BackupManifestCodec.decode(BackupManifestCodec.encode(manifest()))!!
        assertTrue(back.includesThemes)
        assertTrue(!back.includesLearnedWords)
        assertTrue(!back.includesAssets)
    }

    @Test
    fun anEmptyEntryListRoundTrips() {
        val back = BackupManifestCodec.decode(BackupManifestCodec.encode(manifest(entries = emptyList())))
        assertNotNull(back)
        assertTrue(back!!.entries.isEmpty())
    }

    // ---------------------------------------------------------------- rejections

    @Test
    fun malformedJsonIsRejected() {
        assertNull(BackupManifestCodec.decode("{ not json"))
        assertNull(BackupManifestCodec.decode(""))
    }

    @Test
    fun aMissingSchemaIsRejected() {
        assertNull(BackupManifestCodec.decode("""{"appVersionCode":12,"entries":[]}"""))
    }

    @Test
    fun aMissingVersionCodeIsRejected() {
        assertNull(BackupManifestCodec.decode("""{"schema":1,"entries":[]}"""))
    }

    @Test
    fun anEntryWithABadShaIsRejected() {
        val bad = """{"schema":1,"appVersionCode":12,"entries":[{"path":"themes/a.json","sha256":"zz","bytes":1}]}"""
        assertNull(BackupManifestCodec.decode(bad))
    }

    @Test
    fun anEntryWithNegativeSizeIsRejected() {
        val bad = """{"schema":1,"appVersionCode":12,"entries":[{"path":"themes/a.json","sha256":"${"a".repeat(64)}","bytes":-5}]}"""
        assertNull(BackupManifestCodec.decode(bad))
    }

    @Test
    fun anOversizedManifestIsRejectedBeforeParsing() {
        val huge = """{"schema":1,"appVersionCode":12,"pad":"${"x".repeat(70_000)}","entries":[]}"""
        assertNull(BackupManifestCodec.decode(huge))
    }

    @Test
    fun tooManyEntriesIsRejected() {
        val entries = (0..BackupFormat.MAX_ENTRIES).joinToString(",") {
            """{"path":"themes/$it.json","sha256":"${"a".repeat(64)}","bytes":1}"""
        }
        assertNull(BackupManifestCodec.decode("""{"schema":1,"appVersionCode":12,"entries":[$entries]}"""))
    }

    // ---------------------------------------------------------------- preference schema

    @Test
    fun everyAllowlistedKeyHasAType() {
        assertTrue(BackupFormat.PREF_TYPES.isNotEmpty())
        BackupFormat.PREF_TYPES.forEach { (k, t) ->
            assertTrue("key $k is empty", k.isNotBlank())
            assertNotNull("key $k has no type", t)
        }
    }

    @Test
    fun noAllowlistedKeyIsAlsoAForbiddenKey() {
        // a key must never be both exportable and secret
        val overlap = BackupFormat.PREF_TYPES.keys intersect BackupFormat.FORBIDDEN_KEYS
        assertTrue("these keys are both allowlisted and forbidden: $overlap", overlap.isEmpty())
    }

    @Test
    fun theForbiddenListCoversTheEncryptedSecretKeys() {
        // mirrors SecretBox.SECRET_PREFS
        val expected = setOf(
            "anthropic_key", "gemini_key", "openai_key", "groq_key",
            "openrouter_key", "giphy_key", "klipy_key", "ai_custom"
        )
        assertTrue(BackupFormat.FORBIDDEN_KEYS.containsAll(expected))
    }

    @Test
    fun theAllowlistIsDeliberatelyASubsetOfAllSettings() {
        // the app reads 131 keys; exporting all of them would ship device-specific junk
        assertTrue("allowlist grew suspiciously large", BackupFormat.PREF_TYPES.size < 60)
        assertTrue("allowlist is suspiciously small", BackupFormat.PREF_TYPES.size >= 20)
    }

    @Test
    fun theSchemaAndMinimumVersionAreSane() {
        assertEquals(1, BackupFormat.SCHEMA)
        assertTrue(BackupFormat.MIN_APP_VERSION_CODE >= 12)
    }

    @Test
    fun theLimitsAreOrderedSensibly() {
        assertTrue(BackupFormat.MAX_ENTRY_BYTES <= BackupFormat.MAX_ARCHIVE_BYTES)
        assertTrue(BackupFormat.MAX_MANIFEST_BYTES < BackupFormat.MAX_ENTRY_BYTES)
        assertTrue(BackupFormat.MAX_ASSET_SOUND_BYTES < BackupFormat.MAX_ASSET_FONT_BYTES)
    }
}
