package com.genz.keyboard

import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests — these need a device or emulator and are **not** run by the JVM unit
 * test task.
 *
 * They verify the things a unit test cannot: that the IME is really declared, really requires
 * the system permission, and is discoverable by the platform. They do not require the keyboard
 * to be enabled by the user, so they pass on a clean device.
 *
 * Run with:  ./gradlew connectedStandardDebugAndroidTest
 *
 * Status: this suite compiles in CI but has **never been executed** — the environment that
 * produced it has no device, no emulator and no KVM. Nothing here is claimed as passing.
 */
@RunWith(AndroidJUnit4::class)
class ImeRegistrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packageNameIsStable() {
        // the application id is part of every release and of the F-Droid metadata
        assertTrue(context.packageName.startsWith("com.genz.keyboard"))
    }

    @Test
    fun theKeyboardIsDiscoverableAsAnInputMethod() {
        val services = context.packageManager.queryIntentServices(
            Intent(InputMethodService.SERVICE_INTERFACE),
            PackageManager.GET_META_DATA
        )
        val ours = services.filter { it.serviceInfo.packageName == context.packageName }
        assertFalse("no InputMethodService declared for this package", ours.isEmpty())
    }

    @Test
    fun theKeyboardRequiresTheSystemBindingPermission() {
        val services = context.packageManager.queryIntentServices(
            Intent(InputMethodService.SERVICE_INTERFACE),
            PackageManager.GET_META_DATA
        )
        val ours = services.firstOrNull { it.serviceInfo.packageName == context.packageName }
        assertTrue("keyboard service not found", ours != null)
        // an IME that does not require BIND_INPUT_METHOD cannot be bound by the system
        assertTrue(
            "the IME service must require android.permission.BIND_INPUT_METHOD",
            ours!!.serviceInfo.permission == "android.permission.BIND_INPUT_METHOD"
        )
    }

    @Test
    fun onlyTheExpectedDangerousPermissionsAreDeclared() {
        // guards the privacy claim: no new dangerous permission may appear without a test change
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val declared = info.requestedPermissions?.toSet().orEmpty()
        val allowed = setOf(
            "android.permission.VIBRATE",
            "android.permission.INTERNET",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.READ_EXTERNAL_STORAGE"
        )
        val unexpected = declared - allowed
        assertTrue("unexpected permission(s) declared: $unexpected", unexpected.isEmpty())
    }
}
