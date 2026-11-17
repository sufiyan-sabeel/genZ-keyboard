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
}
