package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for auth response parsing and token handling.
 *
 * The security-relevant assertion is [tokensAreRedactedWhenPrinted]: a token that reaches a log is
 * a token that has leaked.
 */
class CommunitySessionTest {

    private val json = """{"access_token":"abc.def.ghi","refresh_token":"r1","expires_in":3600,"expires_at":1700000000}"""

    @Test
    fun aTokenResponseParses() {
        val t = AuthResponse.parse(json)
        assertNotNull(t)
        assertEquals("abc.def.ghi", t!!.accessToken)
        assertEquals("r1", t.refreshToken)
        assertEquals(1700000000L, t.expiresAtEpochSeconds)
    }

    @Test
    fun expiresInIsUsedWhenExpiresAtIsAbsent() {
        val t = AuthResponse.parse("""{"access_token":"a","refresh_token":"r","expires_in":120}""")
        assertNotNull(t)
        assertTrue(t!!.expiresAtEpochSeconds > 0)
    }

    @Test
    fun aResponseMissingTokensIsRejected() {
        assertNull(AuthResponse.parse("""{"refresh_token":"r"}"""))
        assertNull(AuthResponse.parse("""{"access_token":"a"}"""))
        assertNull(AuthResponse.parse("""{"access_token":"","refresh_token":""}"""))
        assertNull(AuthResponse.parse("not json"))
    }

    @Test
    fun anExpiredTokenNeedsRefresh() {
        val t = AuthTokens("a", "r", 1000L)
        assertTrue(AuthResponse.needsRefresh(t, nowEpochSeconds = 1000L))
        assertTrue(AuthResponse.needsRefresh(t, nowEpochSeconds = 950L))   // within the 60s window
        assertFalse(AuthResponse.needsRefresh(t, nowEpochSeconds = 500L))
    }

    @Test
    fun tokensAreRedactedWhenPrinted() {
        val t = AuthTokens("super-secret-access", "super-secret-refresh", 123L)
        val printed = t.toString()
        assertFalse("access token leaked into toString", printed.contains("super-secret-access"))
        assertFalse("refresh token leaked into toString", printed.contains("super-secret-refresh"))
        assertTrue(printed.contains("redacted"))
    }
}
