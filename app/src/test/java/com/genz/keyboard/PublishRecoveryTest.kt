package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the retry decisions in the publish state machine.
 *
 * Publishing spans Storage and Postgres with no shared transaction, so these decisions determine
 * whether a retry duplicates a publication or destroys live artwork.
 */
class PublishRecoveryTest {

    @Test
    fun aSuccessfulCopyIsTransferred() {
        assertEquals(PublishRecovery.CopyOutcome.TRANSFERRED, PublishRecovery.copyOutcome(200))
    }

    @Test
    fun anExistingDestinationMeansAnEarlierAttemptAlreadyCopied() {
        assertEquals(PublishRecovery.CopyOutcome.ALREADY_PRESENT, PublishRecovery.copyOutcome(409))
    }

    @Test
    fun aServerErrorIsRetryable() {
        assertEquals(PublishRecovery.CopyOutcome.RETRY_LATER, PublishRecovery.copyOutcome(503))
        assertEquals(PublishRecovery.RowOutcome.RETRY_LATER, PublishRecovery.rowOutcome(500))
    }

    @Test
    fun aClientErrorIsNotRetryable() {
        assertEquals(PublishRecovery.CopyOutcome.REJECTED, PublishRecovery.copyOutcome(403))
        assertEquals(PublishRecovery.RowOutcome.REJECTED, PublishRecovery.rowOutcome(401))
    }

    @Test
    fun aDuplicateDraftIsRecognisedAsAlreadySaved() {
        assertTrue(PublishRecovery.isDuplicateDraft(409))
        assertFalse(PublishRecovery.isDuplicateDraft(400))
    }

    @Test
    fun liveArtworkIsNotDeletedWhenTheCopyFoundItAlreadyThere() {
        assertFalse(PublishRecovery.shouldDeletePublicAfterFailure(PublishRecovery.CopyOutcome.ALREADY_PRESENT))
        assertTrue(PublishRecovery.shouldDeletePublicAfterFailure(PublishRecovery.CopyOutcome.TRANSFERRED))
    }

    @Test
    fun theDraftIsOnlyDeletedAfterTheRowIsPublished() {
        assertTrue(PublishRecovery.shouldDeleteDraftAfter(PublishRecovery.RowOutcome.PUBLISHED))
        assertFalse(PublishRecovery.shouldDeleteDraftAfter(PublishRecovery.RowOutcome.RETRY_LATER))
        assertFalse(PublishRecovery.shouldDeleteDraftAfter(PublishRecovery.RowOutcome.REJECTED))
    }

    @Test
    fun aConflictIsNotProofTheObjectIsPresent() {
        // ALREADY_PRESENT is a hint, not evidence: the caller must confirm the object before
        // treating the publish as successful. This test pins that a 409 maps to the "verify" path
        // rather than straight to success.
        assertEquals(PublishRecovery.CopyOutcome.ALREADY_PRESENT, PublishRecovery.copyOutcome(409))
        assertTrue(PublishRecovery.requiresVerification(PublishRecovery.CopyOutcome.ALREADY_PRESENT))
        assertFalse(PublishRecovery.requiresVerification(PublishRecovery.CopyOutcome.TRANSFERRED))
    }

    @Test
    fun anObjectOutsideTheCallersFolderIsNotTheirs() {
        val owner = "11111111-1111-4111-8111-111111111111"
        assertTrue(PublishRecovery.isOwnedPath("$owner/wall.webp", owner))
        assertFalse(PublishRecovery.isOwnedPath("22222222-2222-4222-8222-222222222222/wall.webp", owner))
        assertFalse(PublishRecovery.isOwnedPath("wall.webp", owner))
        assertFalse(PublishRecovery.isOwnedPath("$owner/wall.webp", null))
    }
}
