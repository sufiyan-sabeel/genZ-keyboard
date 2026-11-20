package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the GitHub release parsing and the update decision.
 *
 * The network is never touched: every case runs against a fixed JSON body, so the behaviour is
 * deterministic and covers the responses GitHub actually returns — including the ones that must be
 * ignored.
 */
class UpdateCheckerTest {

    private fun release(
        tag: String = "v1.0.6",
        draft: Boolean = false,
        prerelease: Boolean = false,
        body: String = "What changed",
        assetName: String = "GenZKeyboard-v1.0.6.apk",
        assetUrl: String = UpdateChecker.DOWNLOAD_PREFIX + "v1.0.6/GenZKeyboard-v1.0.6.apk"
    ): String {
        val escaped = body
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """{"tag_name":"$tag","name":"GenZ Keyboard $tag","body":"$escaped",""" +
            """"draft":$draft,"prerelease":$prerelease,"published_at":"2026-11-17T10:00:00Z",""" +
            """"assets":[{"name":"$assetName","browser_download_url":"$assetUrl"}]}"""
    }

    // ---------------------------------------------------------------- parsing

    @Test
    fun aNormalReleaseParses() {
        val r = UpdateChecker.parseRelease(release())
        assertNotNull(r)
        assertEquals("1.0.6", r!!.versionName)
        assertEquals("What changed", r.notes)
        assertTrue(r.apkUrl.endsWith(".apk"))
        assertFalse(r.draft)
        assertFalse(r.prerelease)
    }

    @Test
    fun aTagWithoutTheVPrefixStillParses() {
        assertEquals("1.0.6", UpdateChecker.parseRelease(release(tag = "1.0.6"))?.versionName)
    }

    @Test
    fun malformedJsonIsRejectedWithoutThrowing() {
        assertNull(UpdateChecker.parseRelease("{ not json"))
        assertNull(UpdateChecker.parseRelease(""))
    }

    @Test
    fun aReleaseWithNoTagIsRejected() {
        assertNull(UpdateChecker.parseRelease("""{"body":"x","assets":[]}"""))
    }

    @Test
    fun aNonSemverTagIsRejected() {
        assertNull(UpdateChecker.parseRelease(release(tag = "nightly")))
        assertNull(UpdateChecker.parseRelease(release(tag = "v1.0")))
    }

    @Test
    fun aReleaseWithNoApkAssetIsRejected() {
        assertNull(UpdateChecker.parseRelease(release(assetName = "notes.txt")))
    }

    @Test
    fun anApkHostedSomewhereElseIsRejected() {
        // an APK from an arbitrary URL must never be offered
        val json = release(assetUrl = "https://evil.example.com/GenZKeyboard.apk")
        assertNull(UpdateChecker.parseRelease(json))
    }

    @Test
    fun notesAreCappedSoTheDialogStaysSane() {
        val r = UpdateChecker.parseRelease(release(body = "x".repeat(10_000)))
        assertTrue(r!!.notes.length <= UpdateChecker.MAX_NOTES)
    }

    @Test
    fun anExplicitVersionCodeInTheBodyIsRead() {
        val r = UpdateChecker.parseRelease(release(body = "Fixes\nversionCode: 11\n"))
        assertEquals(11, r!!.versionCode)
    }

    @Test
    fun noVersionCodeMarkerMeansNull() {
        assertNull(UpdateChecker.parseRelease(release(body = "Fixes only"))!!.versionCode)
    }

    // ---------------------------------------------------------------- comparison

    @Test
    fun aNewerPatchIsDetected() {
        assertTrue(UpdateChecker.isNewer("1.0.6", "1.0.5"))
    }

    @Test
    fun aNewerMinorAndMajorAreDetected() {
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun theSameVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0.5", "1.0.5"))
    }

    @Test
    fun anOlderVersionIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("1.0.4", "1.0.5"))
    }

    @Test
    fun unparseableVersionsNeverClaimAnUpdate() {
        assertFalse(UpdateChecker.isNewer("nightly", "1.0.5"))
        assertFalse(UpdateChecker.isNewer("1.0.6", "unknown"))
    }

    // ---------------------------------------------------------------- the decision

    @Test
    fun anOlderInstallDetectsTheNewerRelease() {
        // verification case 1
        val r = UpdateChecker.parseRelease(release())
        assertTrue(UpdateChecker.shouldNotify(r, 10, "1.0.5"))
    }

    @Test
    fun theCurrentVersionShowsNothing() {
        // verification case 2
        val r = UpdateChecker.parseRelease(release(tag = "v1.0.6"))
        assertFalse(UpdateChecker.shouldNotify(r, 11, "1.0.6"))
    }

    @Test
    fun noResponseMeansNoDialog() {
        // verification case 3: offline, rate limited or failed all arrive as null
        assertFalse(UpdateChecker.shouldNotify(null, 10, "1.0.5"))
    }

    @Test
    fun aPrereleaseIsIgnored() {
        // verification case 5
        val r = UpdateChecker.parseRelease(release(tag = "v9.0.0", prerelease = true))
        assertFalse(UpdateChecker.shouldNotify(r, 11, "1.0.6"))
    }

    @Test
    fun aDraftIsIgnored() {
        val r = UpdateChecker.parseRelease(release(tag = "v9.0.0", draft = true))
        assertFalse(UpdateChecker.shouldNotify(r, 11, "1.0.6"))
    }

    @Test
    fun theExplicitVersionCodeTakesPrecedenceWhenPresent() {
        // same versionName, higher code: still an update
        val r = UpdateChecker.parseRelease(release(tag = "v1.0.6", body = "versionCode: 12"))
        assertTrue(UpdateChecker.shouldNotify(r, 11, "1.0.6"))
    }

    @Test
    fun aLowerVersionCodeDoesNotOverrideANewerName() {
        val r = UpdateChecker.parseRelease(release(tag = "v1.0.7", body = "versionCode: 5"))
        assertTrue(UpdateChecker.shouldNotify(r, 11, "1.0.6"))
    }

    // ------------------------------------------------- the generated release body
    //
    // The release workflow writes this body from version.properties and the F-Droid changelog, so
    // the marker can no longer be typed by hand. These cases pin the contract it depends on: the
    // marker is read out of the body, and a body without one falls back to the tag.

    private val generatedBody = """
        GenZ Keyboard - an Android keyboard built in Kotlin and Jetpack Compose.

        Created by Umaiz Sufiyan (https://github.com/sufiyan-sabeel).

        versionCode: 12

        ### What changed in 1.0.7

        - Six new wallpaper themes.

        ### Notes

        - This APK is the standard flavour.
    """.trimIndent()

    @Test
    fun theGeneratedReleaseBodyCarriesTheVersionCode() {
        val r = UpdateChecker.parseRelease(release(tag = "v1.0.7", body = generatedBody))
        assertNotNull(r)
        assertEquals("1.0.7", r!!.versionName)
        assertEquals(12, r.versionCode)
        assertTrue(r.notes.contains("Six new wallpaper themes"))
    }

    @Test
    fun theGeneratedBodyUpgradesAnOlderInstall() {
        // 1.0.6 shipped as versionCode 11; the 1.0.7 body says 12
        val r = UpdateChecker.parseRelease(release(tag = "v1.0.7", body = generatedBody))
        assertTrue(UpdateChecker.shouldNotify(r, 11, "1.0.6"))
    }

    @Test
    fun aBodyForTheInstalledVersionDoesNotOfferAnUpdate() {
        // the failure this replaced: notes claiming a code the build does not have
        val r = UpdateChecker.parseRelease(release(tag = "v1.0.7", body = generatedBody))
        assertFalse(UpdateChecker.shouldNotify(r, 12, "1.0.7"))
    }

    @Test
    fun theMarkerSurvivesSpacingAndAnEqualsSign() {
        assertEquals(7, UpdateChecker.versionCodeMarker("versionCode:7"))
        assertEquals(7, UpdateChecker.versionCodeMarker("versionCode :  7"))
        assertEquals(7, UpdateChecker.versionCodeMarker("versionCode=7"))
        assertEquals(7, UpdateChecker.versionCodeMarker("VersionCode: 7"))
    }

    @Test
    fun theFirstMarkerInTheBodyWins() {
        // the generated body puts it near the top; anything later is prose
        assertEquals(12, UpdateChecker.versionCodeMarker("versionCode: 12\n\n...\nversionCode: 4"))
    }

    @Test
    fun anAbsurdlyLongNumberIsNotAMarker() {
        assertNull(UpdateChecker.versionCodeMarker("versionCode: 1234567890"))
    }

    @Test
    fun aMarkerWithNoDigitsIsNotAMarker() {
        assertNull(UpdateChecker.versionCodeMarker("versionCode: none"))
        assertNull(UpdateChecker.versionCodeMarker("versionCode"))
    }

    @Test
    fun aBodyWithoutAMarkerStillComparesByTag() {
        val r = UpdateChecker.parseRelease(release(tag = "v1.0.7", body = "Notes only"))
        assertNull(r!!.versionCode)
        assertTrue(UpdateChecker.shouldNotify(r, 11, "1.0.6"))
    }

    // ------------------------------------------------- signature and installability
    //
    // Android refuses an update signed with a different key. Seven of this project's published
    // releases each used a freshly generated debug key, so the updater has to say so before the
    // user downloads something that cannot install.

    private val sigA = "02d069b2d38a291cb8e63d1ba3daef57a9f38654bbace2633fa758c7206e477d"
    private val sigB = "6a29135be4c1f231f580579d78f70a209daf2b70f851fdfe4af993c4d3845151"

    @Test
    fun theSignatureLineIsReadFromTheBody() {
        val body = "versionCode: 12\nsignature: $sigA\nsha256: abc"
        assertEquals(sigA, UpdateChecker.signatureMarker(body))
    }

    @Test
    fun aSignatureIsNormalisedToLowercase() {
        assertEquals(sigA, UpdateChecker.signatureMarker("signature: " + sigA.uppercase()))
    }

    @Test
    fun aBodyWithoutASignatureYieldsNull() {
        assertNull(UpdateChecker.signatureMarker("versionCode: 12"))
        assertNull(UpdateChecker.signatureMarker(""))
    }

    @Test
    fun aMalformedSignatureIsRejectedRatherThanGuessed() {
        assertNull(UpdateChecker.signatureMarker("signature: not-a-hash"))
        assertNull(UpdateChecker.signatureMarker("signature: " + sigA.dropLast(1)))
        assertNull(UpdateChecker.signatureMarker("signature: " + sigA + "ff"))
    }

    @Test
    fun aReleaseCarriesItsSignatureThroughParsing() {
        val r = UpdateChecker.parseRelease(release(tag = "v1.0.7", body = "signature: $sigA"))
        assertEquals(sigA, r!!.signature)
    }

    @Test
    fun matchingSignaturesAreCompatible() {
        assertEquals(Installability.COMPATIBLE, UpdateChecker.installability(sigA, sigA))
        assertEquals(Installability.COMPATIBLE, UpdateChecker.installability(sigA, sigA.uppercase()))
    }

    @Test
    fun differentSignaturesRequireAReinstall() {
        assertEquals(Installability.INCOMPATIBLE, UpdateChecker.installability(sigA, sigB))
        assertTrue(UpdateChecker.installability(sigA, sigB).needsReinstall)
    }

    @Test
    fun anUnknownSideIsUnknownRatherThanIncompatible() {
        // never claim an update is uninstallable when the evidence is missing
        assertEquals(Installability.UNKNOWN, UpdateChecker.installability(null, sigA))
        assertEquals(Installability.UNKNOWN, UpdateChecker.installability(sigA, null))
        assertEquals(Installability.UNKNOWN, UpdateChecker.installability("", ""))
        assertFalse(Installability.UNKNOWN.needsReinstall)
    }

    @Test
    fun aRealV105ToV106PairIsIncompatible() {
        // the two certificates taken from the published APKs by the audit
        assertEquals(Installability.INCOMPATIBLE, UpdateChecker.installability(sigA, sigB))
    }

    // ------------------------------------------------- the tag parser the CI gate relies on

    @Test
    fun theTagParserMatchesWhatTheReleaseGateAccepts() {
        // scripts/check-release-version.sh rejects anything that is not MAJOR.MINOR.PATCH
        assertEquals("1.0.7", UpdateChecker.versionFromTag("v1.0.7"))
        assertEquals("1.0.7", UpdateChecker.versionFromTag("V1.0.7"))
        assertEquals("1.0.7", UpdateChecker.versionFromTag("1.0.7"))
        assertEquals("1.0.7", UpdateChecker.versionFromTag(" v1.0.7 "))
        assertNull(UpdateChecker.versionFromTag("v1.0"))
        assertNull(UpdateChecker.versionFromTag("release-1.0.7"))
        assertNull(UpdateChecker.versionFromTag("v1.0.7-beta"))
    }
}
