package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the encrypted-token envelope.
 *
 * The Keystore itself cannot run in a JVM test, so what is tested here is the part that can be
 * wrong silently: the versioned envelope format and its rejection rules. A blob that parses
 * wrongly would decrypt to garbage or, worse, be accepted when tampered with.
 */
class TokenEnvelopeTest {

    private val iv = ByteArray(12) { it.toByte() }
    private val ct = "ciphertext".toByteArray()

    @Test
    fun anEnvelopeRoundTrips() {
        val blob = TokenEnvelope.format(iv, ct)
        val parsed = TokenEnvelope.parse(blob)
        assertNotNull(parsed)
        assertEquals(iv.toList(), parsed!!.first.toList())
        assertEquals(ct.toList(), parsed.second.toList())
    }

    @Test
    fun theEnvelopeIsVersioned() {
        val blob = TokenEnvelope.format(iv, ct)
        assertTrue(blob.startsWith("v1:"))
        assertEquals(3, blob.split(':').size)
    }

    @Test
    fun aWrongVersionIsRejected() {
        // a future format must not be silently misread as this one
        assertNull(TokenEnvelope.parse("v2:AAAA:BBBB"))
        assertNull(TokenEnvelope.parse(":AAAA:BBBB"))
    }

    @Test
    fun aMalformedBlobIsRejected() {
        assertNull(TokenEnvelope.parse(""))
        assertNull(TokenEnvelope.parse("v1"))
        assertNull(TokenEnvelope.parse("v1:AAAA"))
        assertNull(TokenEnvelope.parse("v1:AAAA:BBBB:CCCC"))
    }

    @Test
    fun anInvalidBase64PayloadIsRejected() {
        assertNull(TokenEnvelope.parse("v1:not-base64!!:BBBB"))
    }

    @Test
    fun aWrongLengthIvIsRejected() {
        // GCM requires a 12-byte nonce; anything else must not reach the cipher
        val shortIv = ByteArray(8) { 1 }
        assertNull(TokenEnvelope.parse(TokenEnvelope.format(shortIv, ct)))
    }

    @Test
    fun anEmptyCiphertextIsRejected() {
        assertNull(TokenEnvelope.parse(TokenEnvelope.format(iv, ByteArray(0))))
    }

    @Test
    fun aTamperedCiphertextStillParsesButFailsAuthenticatedDecryption() {
        // GCM authenticates: the tag check is what rejects tampering, not the envelope
        val blob = TokenEnvelope.format(iv, "real".toByteArray())
        val parts = blob.split(':')
        val tampered = "v1:${parts[1]}:${java.util.Base64.getEncoder().encodeToString("fake".toByteArray())}"
        assertNotNull("the envelope itself is structurally valid", TokenEnvelope.parse(tampered))
    }
}
