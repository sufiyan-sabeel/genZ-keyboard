package com.genz.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the account-deletion confirmation gate.
 *
 * Deletion is irreversible and destroys every theme, review and post the user owns, so the
 * confirmation must be deliberate rather than accidental.
 */
class CommunityAccountTest {

    @Test
    fun theExactWordConfirms() {
        assertTrue(CommunityAccount.isConfirmed("DELETE"))
    }

    @Test
    fun caseAndSurroundingSpaceDoNotMatter() {
        assertTrue(CommunityAccount.isConfirmed("delete"))
        assertTrue(CommunityAccount.isConfirmed("  Delete  "))
    }

    @Test
    fun anythingElseDoesNot() {
        assertFalse(CommunityAccount.isConfirmed(""))
        assertFalse(CommunityAccount.isConfirmed("   "))
        assertFalse(CommunityAccount.isConfirmed("DEL"))
        assertFalse(CommunityAccount.isConfirmed("DELETED"))
        assertFalse(CommunityAccount.isConfirmed("yes"))
        assertFalse(CommunityAccount.isConfirmed("delete my account"))
    }

    @Test
    fun anUnconfiguredBuildCannotDeleteAnything() {
        // no backend means no deletion; the client must not pretend it succeeded
        assertTrue(CommunityApi.deleteAccount("token") is CommunityResult.NotConfigured)
    }
}
