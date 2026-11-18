package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for creator profile parsing and identity checks.
 *
 * The profile payload is deliberately narrow, so these tests also pin what a profile must never
 * contain: no email, no token.
 */
class CreatorProfileTest {

    private val uuid = "3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"

    private fun row(overrides: Map<String, Any?> = emptyMap()) = linkedMapOf<String, Any?>(
        "id" to uuid,
        "handle" to "riyaz",
        "display_name" to "Riyaz",
        "bio" to "Themes and type",
        "avatar_url" to "https://example.com/a.png",
        "created_at" to "2026-01-01"
    ).apply { putAll(overrides) }

    @Test
    fun aProfileParses() {
        val p = CreatorProfiles.parse(row())!!
        assertEquals(uuid, p.id)
        assertEquals("riyaz", p.handle)
        assertEquals("Riyaz", p.displayName)
        assertEquals("Themes and type", p.bio)
        assertNotNull(p.avatarUrl)
    }

    @Test
    fun aRowWithoutAnIdIsRejected() {
        assertNull(CreatorProfiles.parse(row(mapOf("id" to null))))
        assertNull(CreatorProfiles.parse(row(mapOf("id" to ""))))
    }

    @Test
    fun aMissingAvatarFallsBackToAnInitial() {
        val p = CreatorProfiles.parse(row(mapOf("avatar_url" to null)))!!
        assertNull(p.avatarUrl)
        assertEquals("R", p.initial)
    }

    @Test
    fun aMissingBioIsAbsentRatherThanBlank() {
        assertNull(CreatorProfiles.parse(row(mapOf("bio" to "   ")))!!.bio)
    }

    @Test
    fun aMissingDisplayNameFallsBackToTheHandle() {
        assertEquals("riyaz", CreatorProfiles.parse(row(mapOf("display_name" to "")))!!.displayName)
    }

    @Test
    fun aProfileWithNeitherNameNorHandleIsStillIdentifiable() {
        val p = CreatorProfiles.parse(row(mapOf("display_name" to "", "handle" to "")))!!
        assertEquals("Unknown creator", p.displayName)
        assertEquals(uuid, p.id)
    }

    @Test
    fun privateFieldsAreNotPartOfTheModel() {
        // the select list is explicit, and the model has nowhere to put an email
        assertFalse(CreatorProfiles.SELECT.contains("email"))
        val p = CreatorProfiles.parse(row(mapOf("email" to "a@b.c")))!!
        assertEquals("riyaz", p.handle)
        // nothing in the rendered model carries it
        assertFalse(p.toString().contains("a@b.c"))
    }

    @Test
    fun onlyUuidShapedIdsAreAccepted() {
        assertTrue(CreatorProfiles.isValidUserId(uuid))
        assertFalse(CreatorProfiles.isValidUserId(null))
        assertFalse(CreatorProfiles.isValidUserId(""))
        assertFalse(CreatorProfiles.isValidUserId("riyaz"))
        assertFalse(CreatorProfiles.isValidUserId("3f1b2c4d5e6f4a7b8c9d0e1f2a3b4c5d"))
        assertFalse(CreatorProfiles.isValidUserId("3f1b2c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5"))
    }

    @Test
    fun anInvalidIdFailsBeforeReachingTheNetwork() {
        // a malformed id must not be interpolated into a query
        assertTrue(CommunityApi.fetchProfile("not-a-uuid") is CommunityResult.ServerError)
        assertTrue(CommunityApi.fetchCreatorThemes("not-a-uuid") is CommunityResult.ServerError)
    }

    @Test
    fun anUnconfiguredBuildReportsNotConfigured() {
        assertTrue(CommunityApi.fetchProfile(uuid) is CommunityResult.NotConfigured)
        assertTrue(CommunityApi.fetchCreatorThemes(uuid) is CommunityResult.NotConfigured)
    }
}
