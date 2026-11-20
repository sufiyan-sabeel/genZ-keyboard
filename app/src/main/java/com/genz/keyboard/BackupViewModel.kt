package com.genz.keyboard

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns a running backup or restore.
 *
 * Previously the operation ran on `rememberCoroutineScope()`, which is bound to the composition: a
 * rotation cancelled it mid-write, leaving temp files behind and no rollback. A ViewModel outlives
 * the composition, so the same instance survives rotation, navigation and activity recreation — and
 * the UI reconnects to it rather than starting a second operation.
 *
 * It does NOT survive process death. Nothing here is persisted across a process kill; an operation
 * interrupted that way is simply gone, which is why cleanup runs in [NonCancellable].
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val busy: Boolean = false,
        val label: String = "",
        val done: Int = 0,
        val total: Int = 0,
        val isRestore: Boolean = false,
        val cancelling: Boolean = false,
        val message: String? = null,
        /** Set when an archive was inspected and is waiting for the user to confirm. */
        val pendingRestore: File? = null,
        val pendingThemes: Int = 0,
        val pendingImages: Int = 0,
        val pendingWords: Int = 0,
        val pendingBigrams: Int = 0
    ) {
        /** Percentage, or null when the total is not yet known. */
        val percent: Int? get() = if (total > 0) done * 100 / total else null
    }

    var state by mutableStateOf(UiState())
        private set

    private val cancelFlag = AtomicBoolean(false)

    fun clearMessage() { state = state.copy(message = null) }

    /** Stop the running operation. A second request is ignored. */
    fun cancel() {
        if (!state.busy || state.cancelling) return
        cancelFlag.set(true)
        state = state.copy(cancelling = true)
    }

    fun dismissPending() {
        state.pendingRestore?.delete()
        state = state.copy(pendingRestore = null)
    }

    // ---------------------------------------------------------------- export

    fun startExport(uri: Uri) {
        if (state.busy) return                      // reconnection: never start a second operation
        val app = getApplication<Application>()
        cancelFlag.set(false)
        state = state.copy(busy = true, label = app.getString(android.R.string.ok).let { "" }
            .ifEmpty { "Export backup" }, done = 0, total = 0, isRestore = false, cancelling = false, message = null)

        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                val tmp = File(app.cacheDir, "backup-export.zip")
                try {
                    BackupOperation.run {
                        val (w, b) = BackupData.words(app)
                        val r = BackupService.export(
                            tmp, BackupData.settingsJson(app), w, b, BackupData.themes(app),
                            imageBytes = { name -> BackupData.imageBytes(app, name) },
                            progress = { d, t -> state = state.copy(done = d, total = t) },
                            isCancelled = { cancelFlag.get() }
                        )
                        when (r) {
                            is BackupService.Result.Ok ->
                                if (copyTo(app, tmp, uri)) "Backup saved: ${r.themes} themes, ${r.words} words"
                                else "Could not write the file"
                            is BackupService.Result.Invalid -> "Error: ${r.reason}"
                            is BackupService.Result.Failed -> "Error: ${r.reason}"
                            BackupService.Result.Cancelled -> "Operation cancelled"
                        }
                    } ?: "Another operation is already running"
                } finally {
                    // cleanup must run even if this coroutine is cancelled
                    withContext(NonCancellable) { tmp.delete() }
                }
            }
            state = state.copy(busy = false, label = "", cancelling = false, message = msg)
        }
    }

    // ---------------------------------------------------------------- import

    /** Stage and inspect an archive; nothing is applied until the user confirms. */
    fun startInspect(uri: Uri) {
        if (state.busy) return
        val app = getApplication<Application>()
        state = state.copy(busy = true, label = "Import backup", done = 0, total = 0, cancelling = false, message = null)

        viewModelScope.launch {
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(app.cacheDir, "backup-import.zip")
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("Could not open the file")
                    tmp
                }.getOrNull()
            }
            if (staged == null) {
                state = state.copy(busy = false, label = "", message = "Could not open the file")
                return@launch
            }
            when (val info = withContext(Dispatchers.IO) { BackupService.inspect(staged) }) {
                is BackupService.Result.Ok -> state = state.copy(
                    busy = false, label = "", pendingRestore = staged,
                    pendingThemes = info.themes, pendingImages = info.images,
                    pendingWords = info.words, pendingBigrams = info.bigrams
                )
                is BackupService.Result.Invalid -> {
                    withContext(NonCancellable) { staged.delete() }
                    state = state.copy(busy = false, label = "", message = "Error: ${info.reason}")
                }
                is BackupService.Result.Failed -> {
                    withContext(NonCancellable) { staged.delete() }
                    state = state.copy(busy = false, label = "", message = "Error: ${info.reason}")
                }
                BackupService.Result.Cancelled -> {
                    withContext(NonCancellable) { staged.delete() }
                    state = state.copy(busy = false, label = "", message = "Operation cancelled")
                }
            }
        }
    }

    fun confirmRestore() {
        val file = state.pendingRestore ?: return
        val app = getApplication<Application>()
        cancelFlag.set(false)
        state = state.copy(busy = true, label = "Import backup", isRestore = true,
            pendingRestore = null, done = 0, total = 0, cancelling = false, message = null)

        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    BackupOperation.run {
                        when (val r = BackupService.restore(
                            file, ThemeAssets.themeDir(app), ThemeAssets.imageDir(app),
                            { json -> KeyboardPrefs.importJson(app, json) },
                            { w, b -> BackupData.applyWords(app, w, b) },
                            progress = { d, t -> state = state.copy(done = d, total = t) },
                            isCancelled = { cancelFlag.get() },
                            beforeSettings = { BackupData.settingsJson(app) },
                            beforeWords = { BackupData.words(app) }
                        )) {
                            is BackupService.Result.Ok -> "Restored: ${r.themes} themes, ${r.words} words"
                            is BackupService.Result.Invalid -> "Error: ${r.reason}"
                            is BackupService.Result.Failed -> "Error: ${r.reason}"
                            BackupService.Result.Cancelled -> "Operation cancelled"
                        }
                    } ?: "Another operation is already running"
                } finally {
                    withContext(NonCancellable) { file.delete() }
                }
            }
            state = state.copy(busy = false, label = "", cancelling = false, message = msg)
        }
    }

    private fun copyTo(app: Application, src: File, uri: Uri): Boolean = runCatching {
        app.contentResolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
            true
        } ?: false
    }.getOrDefault(false)
}
