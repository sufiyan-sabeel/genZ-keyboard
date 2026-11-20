@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class
)

package com.genz.keyboard

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface as AndroidTypeface
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// НАСТРОЙКИ: РЕЗЕРВНАЯ КОПИЯ
// ============================================================================

@Composable
internal fun BackupPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    // The operation lives in a ViewModel, so a rotation no longer cancels it mid-write. The screen
    // reconnects to whatever is already running instead of starting a second operation.
    val vm: BackupViewModel = viewModel()
    val op = vm.state

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) vm.startExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.startInspect(uri)
    }
    // legacy settings-only import path, kept for backups taken by earlier builds
    val legacyImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Файл не открылся")
                    KeyboardPrefs.importJson(context, raw.trimStart { it.code == 0xFEFF || it.isWhitespace() })
                }
            }
            r.onSuccess { Toast.makeText(context, tr("Восстановлено параметров: {0}", it), Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, tr("Ошибка: {0}", it.message), Toast.LENGTH_LONG).show() }
        }
    }
    if (op.busy) {
        // progress with a real Cancel button: a long backup must never be an unstoppable frozen screen
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { contentDescription = op.label }
        ) {
            Text(op.label, color = Color(0xFFE8EDF0), fontSize = 12.sp)
            Text(
                op.percent?.let { if (op.isRestore) tr("Восстановление: {0}%", it) else tr("Сохранение: {0}%", it) }
                    ?: tr("Подготовка…"),
                color = Color(0xFF8A9299), fontSize = 11.sp,
                modifier = Modifier.semantics { contentDescription = "${op.percent ?: 0}%" }
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { vm.cancel() }, enabled = !op.cancelling) {
                    Text(
                        if (op.cancelling) tr("Отмена") else tr("Отменить"),
                        color = if (op.cancelling) Color(0xFF5A6268) else Color(0xFFD67A7A),
                        fontSize = 11.sp,
                        modifier = Modifier.semantics { contentDescription = "Cancel the running operation" }
                    )
                }
            }
        }
    }
    op.message?.let { m ->
        Text(
            m, color = Color(0xFFB9C2C8), fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { contentDescription = m }
        )
    }

    SettingsSection("Настройки") {
        ActionItem(Icons.Rounded.Upload, "Экспорт резервной копии", "Темы, обои, слова и настройки в .zip", onClick = {
            exportLauncher.launch("genz-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())}.zip")
        })
        ActionItem(Icons.Rounded.Download, "Импорт резервной копии", "Восстановить из .zip", onClick = {
            runCatching { importLauncher.launch(arrayOf("*/*")) }.onFailure {
                Toast.makeText(context, tr("На телефоне нет приложения для выбора файлов"), Toast.LENGTH_LONG).show()
            }
        })
        ActionItem(Icons.Rounded.Download, "Импорт настроек (.json)", "Старый формат, только параметры", onClick = {
            // список из нескольких типов вместе с */* на части телефонов (MIUI, One UI) прячет или блокирует файлы
            runCatching { legacyImportLauncher.launch(arrayOf("*/*")) }.onFailure {
                Toast.makeText(context, tr("На телефоне нет приложения для выбора файлов"), Toast.LENGTH_LONG).show()
            }
        })
        ActionItem(Icons.Rounded.RestartAlt, "Сбросить настройки", "Словарь, плагины и недавние эмодзи сохранятся", onClick = { confirm = "settings" })
    }
    SettingsSection("Данные") {
        ActionItem(Icons.Rounded.AutoFixHigh, "Очистить выученные слова", "Подсказки забудут ваши слова и пары слов", onClick = { confirm = "dict" })
        ActionItem(Icons.Rounded.History, "Очистить недавние эмодзи", null, onClick = {
            EmojiRecents.clear(context)
            Toast.makeText(context, tr("Готово"), Toast.LENGTH_SHORT).show()
        })
        ActionItem(Icons.Rounded.DeleteForever, "Удалить все личные данные", "Выученные слова, история буфера, недавние эмодзи и ключи API", onClick = { confirm = "all" })
    }
    op.pendingRestore?.let { file ->
        AlertDialog(
            onDismissRequest = { vm.dismissPending() },
            title = { Text("Восстановить резервную копию?") },
            text = {
                Column(Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Backup contains ${op.pendingThemes} themes, ${op.pendingImages} wallpapers, ${op.pendingWords} words"
                }) {
                    Text(
                        tr("Тем: {0}, обоев: {1}, слов: {2}, пар: {3}",
                            op.pendingThemes, op.pendingImages, op.pendingWords, op.pendingBigrams),
                        color = Color(0xFFB9C2C8), fontSize = 12.sp
                    )
                    Text(
                        tr("Существующие темы не перезаписываются: совпадающие добавятся как копии."),
                        color = Color(0xFF8A9299), fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmRestore() }) {
                    Text("Восстановить", color = Color(0xFF6FD3D6))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissPending() }) { Text("Отмена") }
            }
        )
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Rounded.RestartAlt, null) },
            title = { Text(tr(when (what) { "dict" -> "Очистить словарь?"; "all" -> "Удалить личные данные?"; else -> "Сбросить настройки?" })) },
            text = {
                Text(
                    tr(when (what) {
                        "dict" -> "Все выученные слова будут удалены."
                        "all" -> "Клавиатура забудет ваши слова, историю буфера обмена, недавние эмодзи и ключи ИИ-сервисов и GIF. Настройки и темы останутся."
                        else -> "Размеры, цвета, тема, шрифт, фон и вибрация вернутся к значениям по умолчанию."
                    })
                )
            },
            confirmButton = {
                Button(onClick = {
                    when (what) {
                        "dict" -> {
                            WordPredictorEngine.clearUserData()
                            CjkUser.clear()
                        }
                        "all" -> {
                            PrivateData.wipe(context)
                            Toast.makeText(context, tr("Личные данные удалены"), Toast.LENGTH_SHORT).show()
                        }
                        else -> KeyboardPrefs.resetAll(context)
                    }
                    confirm = null
                }) { Text(tr(when (what) { "dict" -> "Очистить"; "all" -> "Удалить"; else -> "Сбросить" })) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("Отмена")) } }
        )
    }
}

// ----------------------------------------------------------------------- о приложении

/** Copy a finished archive to the document the user picked. */
private fun copyTo(context: android.content.Context, src: File, uri: android.net.Uri): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
            true          // copyTo returns the byte count, which is not the result we want
        } ?: false
    }.getOrDefault(false)
