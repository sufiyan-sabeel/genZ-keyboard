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
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<File?>(null) }

    /** The complete archive is built in the cache first, then copied to the user's chosen file. */
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            if (busy) return@launch
            busy = true
            val msg = withContext(Dispatchers.IO) {
                BackupOperation.run {
                    val tmp = File(context.cacheDir, "backup-export.zip")
                    val (w, b) = BackupData.words(context)
                    val r = BackupService.export(
                        tmp, BackupData.settingsJson(context), w, b, BackupData.themes(context),
                        imageBytes = { name -> BackupData.imageBytes(context, name) }
                    )
                    val text = when (r) {
                        is BackupService.Result.Ok ->
                            if (copyTo(context, tmp, uri)) {
                                tmp.delete()
                                tr("Резервная копия сохранена: тем {0}, слов {1}", r.themes, r.words)
                            } else tr("Не удалось записать файл")
                        is BackupService.Result.Invalid -> tr("Ошибка: {0}", r.reason)
                        is BackupService.Result.Failed -> tr("Ошибка: {0}", r.reason)
                        BackupService.Result.Cancelled -> tr("Операция отменена")
                    }
                    tmp.delete()
                    text
                } ?: tr("Другая операция уже выполняется")
            }
            busy = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            if (busy) { Toast.makeText(context, tr("Другая операция уже выполняется"), Toast.LENGTH_SHORT).show(); return@launch }
            busy = true
            // inspect the archive before touching any data, then ask for confirmation
            val staged = withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(context.cacheDir, "backup-import.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("Файл не открылся")
                    tmp
                }.getOrNull()
            }
            busy = false
            if (staged == null) {
                Toast.makeText(context, tr("Файл не открылся"), Toast.LENGTH_LONG).show()
            } else when (val info = withContext(Dispatchers.IO) { BackupService.inspect(staged) }) {
                is BackupService.Result.Ok -> pendingRestore = staged
                is BackupService.Result.Invalid -> {
                    staged.delete()
                    Toast.makeText(context, tr("Ошибка: {0}", info.reason), Toast.LENGTH_LONG).show()
                }
                is BackupService.Result.Failed -> {
                    staged.delete()
                    Toast.makeText(context, tr("Ошибка: {0}", info.reason), Toast.LENGTH_LONG).show()
                }
                BackupService.Result.Cancelled -> staged.delete()
            }
        }
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
    pendingRestore?.let { file ->
        var counts by remember(file) { mutableStateOf<BackupService.Result.Ok?>(null) }
        LaunchedEffect(file) {
            counts = withContext(Dispatchers.IO) { BackupService.inspect(file) } as? BackupService.Result.Ok
        }
        AlertDialog(
            onDismissRequest = { file.delete(); pendingRestore = null },
            title = { Text("Восстановить резервную копию?") },
            text = {
                Column {
                    val c = counts
                    Text(
                        if (c == null) "Проверка файла…"
                        else "Тем: ${c.themes}, обоев: ${c.images}, слов: ${c.words}, пар: ${c.bigrams}",
                        color = Color(0xFFB9C2C8), fontSize = 12.sp
                    )
                    Text(
                        "Существующие темы не перезаписываются: совпадающие добавятся как копии.",
                        color = Color(0xFF8A9299), fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = file
                    pendingRestore = null
                    scope.launch {
                        busy = true
                        val msg = withContext(Dispatchers.IO) {
                            BackupOperation.run {
                                when (val r = BackupService.restore(
                                    f, ThemeAssets.themeDir(context), ThemeAssets.imageDir(context),
                                    { json -> KeyboardPrefs.importJson(context, json) },
                                    { w, b -> BackupData.applyWords(context, w, b) }
                                )) {
                                    is BackupService.Result.Ok ->
                                        tr("Восстановлено: тем {0}, слов {1}", r.themes, r.words)
                                    is BackupService.Result.Invalid -> tr("Ошибка: {0}", r.reason)
                                    is BackupService.Result.Failed -> tr("Ошибка: {0}", r.reason)
                                    BackupService.Result.Cancelled -> tr("Операция отменена")
                                }
                            } ?: tr("Другая операция уже выполняется")
                        }
                        f.delete()
                        busy = false
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text("Восстановить", color = Color(0xFF6FD3D6)) }
            },
            dismissButton = {
                TextButton(onClick = { file.delete(); pendingRestore = null }) { Text("Отмена") }
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
