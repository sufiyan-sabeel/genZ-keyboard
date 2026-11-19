package com.genz.keyboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Checks for a newer release in the background and offers it once.
 *
 * Runs off the composition thread and never blocks anything: if the network is slow or absent the
 * check simply returns null and nothing is shown.
 */
@Composable
fun UpdateHost(installedCode: Int, installedName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    // Read once: the certificate cannot change while the process is running.
    val installedSignature = remember { InstalledSignature.sha256(context) }

    LaunchedEffect(Unit) {
        scope.launch {
            val client = UpdateClient(context)
            val now = System.currentTimeMillis()
            val latest = client.latestRelease(now) ?: return@launch
            if (!UpdateChecker.shouldNotify(latest, installedCode, installedName)) return@launch
            if (client.recentlyDismissed(latest.versionName, now)) return@launch
            release = latest
        }
    }

    release?.let { r ->
        UpdateDialog(
            release = r,
            installedName = installedName,
            installability = UpdateChecker.installability(r.signature, installedSignature),
            onDismiss = {
                UpdateClient(context).markDismissed(r.versionName, System.currentTimeMillis())
                release = null
            },
            onUpdate = {
                openDownload(context, r.apkUrl)
                release = null
            }
        )
    }
}

/**
 * Opens the release APK in the browser.
 *
 * Deliberately not an automatic install: Android's own confirmation step stays in the loop, and
 * nothing is downloaded or executed by the keyboard itself.
 */
internal fun openDownload(context: Context, url: String) {
    if (!url.startsWith(UpdateChecker.DOWNLOAD_PREFIX)) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun UpdateDialog(
    release: ReleaseInfo,
    installedName: String,
    installability: Installability,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (installability.needsReinstall) "Update Requires Reinstall" else "Update Available",
                fontSize = 18.sp
            )
        },
        text = {
            Column(Modifier.heightIn(max = 360.dp)) {
                Text("New version ${release.versionName}", fontSize = 14.sp)
                Text("You have $installedName", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                // Android will reject this install with INSTALL_FAILED_UPDATE_INCOMPATIBLE because
                // the release is signed with a different key. Saying so before the download is the
                // difference between an inconvenience and a broken promise.
                if (installability.needsReinstall) {
                    Text(
                        "This version is signed with a different key from the one installed, so " +
                            "Android cannot install it over your copy. You will need to uninstall " +
                            "first, which deletes your settings, learned words and clipboard " +
                            "history. Export a backup in Settings before you uninstall.",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                if (release.notes.isNotBlank()) {
                    Text(
                        release.notes.trim(),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Update Now") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}
