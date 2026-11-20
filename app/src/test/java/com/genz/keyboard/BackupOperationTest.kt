package com.genz.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the single-operation guard.
 *
 * Export and restore both write the themes directory; running them together would let a restore read
 * half-written files. The guard must refuse the second attempt outright rather than queue it.
 */
class BackupOperationTest {

    @After fun tearDown() = BackupOperation.reset()

    @Test
    fun anOperationRunsWhenNothingElseIs() {
        assertEquals(42, BackupOperation.run { 42 })
        assertFalse(BackupOperation.isBusy)
    }

    @Test
    fun aSecondOperationIsRefusedWhileTheFirstRuns() {
        var inner: Int? = 1
        BackupOperation.run {
            assertTrue("guard should be held", BackupOperation.isBusy)
            inner = BackupOperation.run { 99 }
        }
        assertNull("the nested attempt must be refused", inner)
    }

    @Test
    fun theGuardIsReleasedAfterSuccess() {
        BackupOperation.run { 1 }
        assertFalse(BackupOperation.isBusy)
        assertEquals(2, BackupOperation.run { 2 })
    }

    @Test
    fun theGuardIsReleasedAfterAFailure() {
        // a thrown exception must not leave the app permanently unable to back up
        runCatching { BackupOperation.run { error("boom") } }
        assertFalse(BackupOperation.isBusy)
        assertEquals(3, BackupOperation.run { 3 })
    }

    @Test
    fun theGuardReturnsTheBlocksValue() {
        assertEquals("done", BackupOperation.run { "done" })
    }
}
