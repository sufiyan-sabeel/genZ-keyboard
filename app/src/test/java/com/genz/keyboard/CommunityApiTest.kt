package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the community client's pure parts.
 *
 * Nothing here touches the network: URL construction and row mapping are the pieces that can be
 * wrong silently, so they are pinned down. The important behavioural rule is that an unconfigured
 * build reports [CommunityResult.NotConfigured] rather than pretending to succeed.
 */
class CommunityApiTest {

    private val base = "https://demo.supabase.co"

    // ---------------------------------------------------------------- urls

    @Test
    fun aTableUrlHasNoQueryWhenThereAreNoParams() {
        assertEquals("https://demo.supabase.co/rest/v1/themes", CommunityUrls.table(base, "themes"))
    }

    @Test
    fun paramsAreEncoded() {
        val url = CommunityUrls.table(base, "themes", linkedMapOf("name" to "ilike.*sun set*"))
        assertTrue(url.contains("name=ilike.%2Asun%20set%2A"))
        assertTrue(url.startsWith("https://demo.supabase.co/rest/v1/themes?"))
    }

    @Test
    fun aTrailingSlashInTheBaseIsNormalised() {
        assertEquals("https://demo.supabase.co/rest/v1/themes",
            CommunityUrls.table("https://demo.supabase.co/", "themes"))
    }

    @Test
    fun authAndStoragePathsAreBuiltCorrectly() {
        assertEquals("https://demo.supabase.co/auth/v1/token?grant_type=password",
            CommunityUrls.auth(base, "token?grant_type=password"))
        assertEquals("https://demo.supabase.co/storage/v1/object/public/theme-previews/a%20b.png",
            CommunityUrls.storage(base, "theme-previews", "a b.png"))
    }

    // ---------------------------------------------------------------- row mapping

    private fun row() = mapOf(
        "id" to "t1",
        "name" to "Sunset",
        "description" to "warm",
        "tags" to listOf("anime", "warm"),
        "category" to "anime",
        "preview_path" to "u1/preview.png",
        "downloads" to 12,
        "version" to 2,
        "profiles" to mapOf("handle" to "riyaz")
    )

    @Test
    fun aRowMapsToAThemeSummary() {
        val t = CommunityModels.theme(row(), base)
        assertNotNull(t)
        assertEquals("Sunset", t!!.name)
        assertEquals("riyaz", t.authorHandle)
        assertEquals(listOf("anime", "warm"), t.tags)
        assertEquals(12, t.downloads)
        assertEquals(2, t.version)
    }

    @Test
    fun aPreviewPathBecomesAStorageUrl() {
        val t = CommunityModels.theme(row(), base)!!
        assertEquals("https://demo.supabase.co/storage/v1/object/public/theme-previews/u1/preview.png", t.previewUrl)
    }

    @Test
    fun aRowWithoutIdOrNameIsDropped() {
        assertNull(CommunityModels.theme(mapOf("name" to "x"), base))
        assertNull(CommunityModels.theme(mapOf("id" to "x"), base))
    }

    @Test
    fun aMissingAuthorDoesNotCrash() {
        val r = row().toMutableMap(); r.remove("profiles")
        assertEquals("", CommunityModels.theme(r, base)!!.authorHandle)
    }

    @Test
    fun aRowWithoutRatingDefaultsToZero() {
        val t = CommunityModels.theme(row(), base)!!
        assertEquals(0.0, t.averageRating, 0.001)
        assertEquals(0, t.reviewCount)
    }

    @Test
    fun reviewsMapToo() {
        val r = CommunityModels.review(mapOf(
            "id" to "r1", "rating" to 5, "body" to "lovely",
            "created_at" to "2026-01-01", "profiles" to mapOf("handle" to "sam")
        ))
        assertNotNull(r)
        assertEquals(5, r!!.rating)
        assertEquals("sam", r.authorHandle)
    }

    // ---------------------------------------------------------------- configuration gate

    @Test
    fun anUnconfiguredBuildReportsNotConfiguredRatherThanFakingSuccess() {
        // the test build has no SUPABASE_URL, which is the same state as a fresh clone
        assertTrue(!CommunityConfig.isConfigured)
        assertEquals(CommunityResult.NotConfigured, CommunityApi.listThemes(null))
        assertEquals(CommunityResult.NotConfigured, CommunityApi.signIn("a@b.c", "x"))
        assertEquals(CommunityResult.NotConfigured, CommunityApi.publishTheme("tok", "n", "d", emptyList(), "c", "{}"))
    }

    @Test
    fun thePublishableKeyIsNeverAServiceKey() {
        // a service-role JWT in the APK would bypass row-level security entirely
        assertTrue("a service key must never be shipped", !CommunityConfig.anonKey.contains("service_role"))
    }
}
