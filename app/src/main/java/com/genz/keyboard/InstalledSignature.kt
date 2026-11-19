package com.genz.keyboard

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * The SHA-256 of this installation's signing certificate.
 *
 * The value is the same one `apksigner verify --print-certs` prints and the same one the release
 * workflow writes into the release notes as `signature:`. Comparing the two is how the updater can
 * tell, before the user downloads anything, that Android will refuse the install because the keys
 * differ.
 *
 * Returns null rather than throwing when the platform will not say: an unknown identity must not
 * be mistaken for a matching one.
 */
object InstalledSignature {

    fun sha256(context: Context): String? = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners ?: return@runCatching null
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures ?: return@runCatching null
        }
        val first = signatures.firstOrNull() ?: return@runCatching null
        hex(MessageDigest.getInstance("SHA-256").digest(first.toByteArray()))
    }.getOrNull()

    /** Lower-case hex, the form apksigner and the release notes use. */
    internal fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 0x10) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }
}
