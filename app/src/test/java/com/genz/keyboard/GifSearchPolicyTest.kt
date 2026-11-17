package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the GIF search privacy rule.
 *
 * The query typed into the GIF bar is sent to a third-party service. These cases pin that a
 * private session closes that path while keeping the built-in catalogue usable.
 */
class GifSearchPolicyTest {

    private fun mode(manual: Boolean = false, field: Boolean = false, key: Boolean = true) =
        GifSearchPolicy.mode(manual, field, key)

    // ---------------------------------------------------------------- the matrix

    @Test
    fun aPublicSessionWithAConfiguredServiceUsesTheNetwork() {
        assertEquals(GifSearchMode.NETWORK, mode())
        assertTrue(GifSearchPolicy.allowsNetwork(mode()))
    }

    @Test
    fun manualIncognitoClosesTheNetworkPathEvenWithAKey() {
        assertEquals(GifSearchMode.LOCAL_ONLY, mode(manual = true))
        assertFalse(GifSearchPolicy.allowsNetwork(mode(manual = true)))
    }

    @Test
    fun aPasswordFieldClosesTheNetworkPath() {
        assertEquals(GifSearchMode.LOCAL_ONLY, mode(field = true))
    }

    @Test
    fun bothPrivacySignalsTogetherCloseIt() {
        assertEquals(GifSearchMode.LOCAL_ONLY, mode(manual = true, field = true))
    }

    @Test
    fun noConfiguredServiceMeansLocalOnlyRegardlessOfPrivacy() {
        assertEquals(GifSearchMode.LOCAL_ONLY, mode(key = false))
        assertEquals(GifSearchMode.LOCAL_ONLY, mode(manual = true, key = false))
    }

    @Test
    fun privacyIsReportedAsTheReasonOnlyWhenItIsTheReason() {
        // the UI explains privacy, but not a missing key
        assertTrue(GifSearchPolicy.blockedByPrivacy(manualIncognito = true, privateField = false))
        assertTrue(GifSearchPolicy.blockedByPrivacy(manualIncognito = false, privateField = true))
        assertFalse(GifSearchPolicy.blockedByPrivacy(manualIncognito = false, privateField = false))
    }

    @Test
    fun missingKeyIsNotReportedAsAPrivacyBlock() {
        // local-only because nothing is configured must not claim incognito
        assertEquals(GifSearchMode.LOCAL_ONLY, mode(key = false))
        assertFalse(GifSearchPolicy.blockedByPrivacy(manualIncognito = false, privateField = false))
    }

    // ---------------------------------------------------------------- consistency

    @Test
    fun theGifRuleAgreesWithTheInlineAutofillRuleOnPrivacy() {
        // both take the same two signals; a divergence would mean one surface leaks
        listOf(
            false to false, true to false, false to true, true to true
        ).forEach { (manual, field) ->
            assertEquals(
                "disagreement at manual=$manual field=$field",
                InlineAutofillPolicy.mayShow(manual, field),
                GifSearchPolicy.allowsNetwork(GifSearchPolicy.mode(manual, field, hasKey = true))
            )
        }
    }

    @Test
    fun everyPrivacyCombinationThatBlocksAutofillAlsoBlocksGifSearch() {
        listOf(true to false, false to true, true to true).forEach { (manual, field) ->
            assertFalse(
                "GIF network must be closed at manual=$manual field=$field",
                GifSearchPolicy.allowsNetwork(GifSearchPolicy.mode(manual, field, hasKey = true))
            )
        }
    }
}
