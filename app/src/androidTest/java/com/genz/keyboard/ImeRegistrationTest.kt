package com.genz.keyboard

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests — these need a device or emulator and are **not** run by the JVM unit
 * test task. Run with:
 *
 *     ./gradlew connectedStandardDebugAndroidTest
 *
 * They verify what a unit test cannot: that the IME is really declared, really requires the
 * system binding permission, and is discoverable by the platform. None of them require the user
 * to have enabled the keyboard, so they pass on a clean device.
 *
 * Status: this suite **compiles in CI** (step "Compile instrumentation tests") but has **never
 * been executed** — the environment that produced it has no device, no emulator and no KVM.
 * Nothing here is claimed as passing.
 */
@RunWith(AndroidJUnit4::class)
class ImeRegistrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** `PackageInfo.PROTECTION_SIGNATURE` is API 28+; the raw value keeps this readable on older stubs. */
    private object PackageInfoProtection {
        const val SIGNATURE = 2
    }

    /**
     * `InputMethodService.SERVICE_INTERFACE`. Written out as a literal because referencing the
     * framework class directly did not resolve in the androidTest compilation of this project;
     * the value is fixed by the platform API and cannot change.
     */
    private val imeServiceAction = "android.view.InputMethod"

    private fun imeServices() = context.packageManager
        .queryIntentServices(Intent(imeServiceAction), PackageManager.GET_META_DATA)
        .orEmpty()

    @Test
    fun theActionStringFindsInputMethodsOnThisDevice() {
        // guards the literal above: every Android device ships at least one IME, so an empty
        // result means the action string is wrong, not that the device is unusual
        assertFalse("no input method services found for $imeServiceAction", imeServices().isEmpty())
    }

    @Test
    fun packageNameIsStable() {
        // the application id is part of every release and of the F-Droid metadata
        assertTrue(context.packageName.startsWith("com.genz.keyboard"))
    }

    @Test
    fun theKeyboardIsDiscoverableAsAnInputMethod() {
        val ours = imeServices().filter { it.serviceInfo.packageName == context.packageName }
        assertFalse("no InputMethodService declared for this package", ours.isEmpty())
    }

    @Test
    fun theKeyboardRequiresTheSystemBindingPermission() {
        val ours = imeServices().firstOrNull { it.serviceInfo.packageName == context.packageName }
        assertTrue("keyboard service not found", ours != null)
        // an IME that does not require BIND_INPUT_METHOD cannot be bound by the system
        assertTrue(
            "the IME service must require android.permission.BIND_INPUT_METHOD",
            ours?.serviceInfo?.permission == "android.permission.BIND_INPUT_METHOD"
        )
    }

    @Test
    fun onlyTheExpectedPermissionsAreDeclared() {
        // guards the privacy claim: a new dangerous permission must fail this test, not slip in
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
        /*
         * One permission is expected but not in the list above, because AndroidX generates it rather
         * than the app declaring it: androidx.core adds
         * `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` at manifest-merge time when the
         * app targets SDK 33+, so runtime-registered receivers can be marked not-exported.
         *
         * Matched by its EXACT name, not by package prefix. A prefix rule would silently absorb any
         * future permission an attacker-ish dependency might inject under our namespace; the exact
         * name can only ever excuse this one thing.
         */
        val generated = "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val unexpected = declared - allowed - setOf(generated)
        assertTrue("unexpected permission(s) declared: $unexpected", unexpected.isEmpty())

        // The excuse above is only sound if the permission really is what it claims to be, so verify
        // its protection level and ownership at runtime instead of trusting the name.
        val perm = context.packageManager.getPermissionInfo(generated, 0)
        assertEquals(
            "the generated permission must be signature-level, not dangerous",
            PackageInfoProtection.SIGNATURE, perm.protection
        )
        assertEquals(
            "the generated permission must belong to this package",
            context.packageName, perm.packageName
        )
    }
}
