package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the QR payload formats.
 *
 * These follow the conventions other scanners implement, so the assertions are about what a *scanner*
 * will read, not about what this app happens to write. The escaping cases are the ones that matter:
 * an unescaped separator silently changes the decoded value.
 */
class QrPayloadTest {

    @Test
    fun aBareDomainBecomesHttps() {
        assertEquals("https://example.com", QrPayload.Url("example.com").encode())
    }

    @Test
    fun anExplicitSchemeIsNeverRewritten() {
        // downgrading a deliberate scheme would be a silent security change
        assertEquals("http://example.com", QrPayload.Url("http://example.com").encode())
        assertEquals("mailto:a@b.c", QrPayload.Url("mailto:a@b.c").encode())
    }

    @Test
    fun plainTextIsUnchanged() {
        assertEquals("hello world", QrPayload.Text("hello world").encode())
    }

    @Test
    fun wifiEncodesTheStandardFields() {
        val q = QrPayload.Wifi("Home", "secret", QrPayload.Wifi.Security.WPA)
        assertEquals("WIFI:T:WPA;S:Home;P:secret;;", q.encode())
    }

    @Test
    fun anOpenNetworkOmitsThePassword() {
        val q = QrPayload.Wifi("Cafe", "", QrPayload.Wifi.Security.NONE)
        assertEquals("WIFI:T:nopass;S:Cafe;;", q.encode())
    }

    @Test
    fun wifiSeparatorsInTheSsidAreEscaped() {
        // unescaped, a scanner would read the SSID as "Home" and the rest as another field
        val q = QrPayload.Wifi("Ho;me,net:work", "pw", QrPayload.Wifi.Security.WPA)
        assertTrue(q.encode().contains("S:Ho\\;me\\,net\\:work;"))
    }

    @Test
    fun wifiCredentialsAreMarkedSensitive() {
        assertTrue(QrPayload.Wifi("a", "b", QrPayload.Wifi.Security.WPA).isSensitive())
        assertFalse(QrPayload.Wifi("a", "", QrPayload.Wifi.Security.NONE).isSensitive())
    }

    @Test
    fun emailEncodesSubjectAndBody() {
        val q = QrPayload.Email("a@b.c", "Hi there", "Body")
        assertEquals("mailto:a@b.c?subject=Hi%20there&body=Body", q.encode())
    }

    @Test
    fun emailOmitsEmptyParameters() {
        assertEquals("mailto:a@b.c", QrPayload.Email("a@b.c").encode())
    }

    @Test
    fun phoneKeepsOnlyDialableCharacters() {
        assertEquals("tel:+919876543210", QrPayload.Phone("+91 98765-43210").encode())
    }

    @Test
    fun smsWithoutAMessageUsesTheSimpleForm() {
        assertEquals("sms:123", QrPayload.Sms("123").encode())
        assertEquals("smsto:123:hi", QrPayload.Sms("123", "hi").encode())
    }

    @Test
    fun aContactProducesAValidVcard() {
        val v = QrPayload.Contact("Ada Lovelace", "+44 20 7946 0958", "ada@example.com", "Analytical").encode()
        assertTrue(v.startsWith("BEGIN:VCARD\nVERSION:3.0\n"))
        assertTrue(v.contains("FN:Ada Lovelace"))
        assertTrue(v.contains("ORG:Analytical"))
        assertTrue(v.contains("TEL;TYPE=CELL:+442079460958"))
        assertTrue(v.endsWith("END:VCARD"))
    }

    @Test
    fun vcardSeparatorsAreEscaped() {
        val v = QrPayload.Contact("Smith; John, Jr").encode()
        assertTrue(v.contains("FN:Smith\\; John\\, Jr"))
    }

    @Test
    fun geoEncodesCoordinatesAndAnOptionalLabel() {
        assertEquals("geo:12.34,56.78", QrPayload.Geo(12.34, 56.78).encode())
        assertTrue(QrPayload.Geo(12.34, 56.78, "Office").encode().contains("(Office)"))
    }

    @Test
    fun anEventProducesAValidVevent() {
        val e = QrPayload.Event("Standup", "20261120T090000Z", "20261120T091500Z", "Room 1").encode()
        assertTrue(e.startsWith("BEGIN:VEVENT\n"))
        assertTrue(e.contains("SUMMARY:Standup"))
        assertTrue(e.contains("DTSTART:20261120T090000Z"))
        assertTrue(e.contains("LOCATION:Room 1"))
        assertTrue(e.endsWith("END:VEVENT"))
    }

    @Test
    fun otpProducesAProvisioningUri() {
        val o = QrPayload.Otp("GenZ", "user@example.com", "abcd efgh", 30, 6).encode()
        assertTrue(o.startsWith("otpauth://totp/"))
        assertTrue(o.contains("secret=ABCDEFGH"))
        assertTrue(o.contains("issuer=GenZ"))
        assertTrue(o.contains("digits=6"))
    }

    @Test
    fun anOtpSecretIsNeverInTheSummary() {
        // the secret must not reach history, a log or a share sheet by accident
        val o = QrPayload.Otp("GenZ", "user@example.com", "ABCDEFGH")
        assertTrue(o.isSensitive())
        assertFalse("secret leaked into the summary", o.summary().contains("ABCDEFGH"))
    }

    @Test
    fun everyPayloadTypeProducesANonEmptyString() {
        val all: List<QrPayload> = listOf(
            QrPayload.Url("example.com"),
            QrPayload.Text("t"),
            QrPayload.Wifi("s", "p"),
            QrPayload.Email("a@b.c"),
            QrPayload.Phone("1"),
            QrPayload.Sms("1"),
            QrPayload.Contact("n"),
            QrPayload.Geo(1.0, 2.0),
            QrPayload.Event("t", "a", "b"),
            QrPayload.Otp("i", "a", "ABCDEFGH")
        )
        assertEquals(10, all.size)
        all.forEach { assertTrue("${it::class.simpleName} encoded to nothing", it.encode().isNotEmpty()) }
    }
}
