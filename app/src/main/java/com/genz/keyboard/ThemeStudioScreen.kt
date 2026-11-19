package com.genz.keyboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Entry point used by the settings navigation. */
@Composable
internal fun StudioEntryButton(onOpen: () -> Unit) {
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Theme Studio")
    }
}

/**
 * The Theme Studio.
 *
 * The preview is the production `KeyArea` driven by the production `rememberKeyPalette`, fed a
 * settings copy from [ThemeApply]. There is no second renderer, so the preview cannot drift from
 * what the keyboard draws.
 */
@Composable
fun ThemeStudioScreen(onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { ThemeStore(File(context.filesDir, "themes")) }
    val base = remember { KeyboardPrefs.current(context) }

    var saved by remember { mutableStateOf<List<ThemeDoc>>(emptyList()) }
    var draft by remember { mutableStateOf<ThemeDoc?>(null) }
    var pendingDelete by remember { mutableStateOf<ThemeDoc?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var importedText by remember { mutableStateOf<String?>(null) }
    var publishOpen by remember { mutableStateOf(false) }
    var publishState by remember { mutableStateOf<ThemePublishing.State>(ThemePublishing.State.Idle) }

    // Bundled themes, filtered to those whose artwork this build actually ships.
    val gallery = remember {
        runCatching {
            val files = context.assets.list("plugins").orEmpty()
                .filter { ThemePresetIndex.isGalleryFile(it) }
                .mapNotNull { n ->
                    runCatching {
                        n to context.assets.open("plugins/$n").bufferedReader().use { it.readText() }
                    }.getOrNull()
                }
            val walls = context.assets.list("anime").orEmpty().map { "anime/$it" }.toSet()
            ThemePresetIndex.build(files, walls)
        }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) { saved = store.list() }
    fun refresh() { saved = store.list() }

    val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = ThemeAssets.readTheme(context, uri)
        if (text == null) { message = "Could not read that file"; return@rememberLauncherForActivityResult }
        when (val v = ThemeCodec.decode(text)) {
            is ThemeValidation.Ok -> {
                if (store.exists(v.doc.id)) {
                    importedText = text          // ask before overwriting
                    message = "\"${v.doc.name}\" already exists"
                } else {
                    store.save(v.doc); draft = v.doc; refresh(); message = "Imported \"${v.doc.name}\""
                }
            }
            is ThemeValidation.Invalid -> message = "Not a valid theme: ${v.reason}"
        }
    }

    val exportTheme = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val d = draft ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        message = if (ThemeAssets.writeTheme(context, uri, ThemeCodec.encode(d))) "Exported \"${d.name}\"" else "Export failed"
    }

    fun extractColours(d: ThemeDoc) {
        val f = d.wallpaper?.let { ThemeAssets.wallpaperFile(context, it) } ?: run {
            message = "Pick a wallpaper first"; return
        }
        val bmp = runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
        if (bmp == null) { message = "Could not read the wallpaper"; return }
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val pal = PaletteExtractor.extract(px, w, h)
        val ov = d.overrides.toMutableMap()
        ov.putAll(PaletteExtractor.toOverrides(pal))
        draft = d.copy(overrides = ov)
        message = "Colours taken from the wallpaper"
    }

    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val d = draft ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val name = ThemeAssets.importWallpaper(context, uri)
        if (name == null) message = "That image could not be used (too large or not an image)"
        else { draft = d.copy(wallpaper = name); message = "Wallpaper added" }
    }

    Column(Modifier.fillMaxWidth().background(Color(0xFF0E1013)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Theme Studio", color = Color(0xFFE8ECEF), fontSize = 20.sp, fontFamily = FontFamily.SansSerif)
            TextButton(onClick = onClose) { Text("Close", color = Color(0xFF6FD3D6)) }
        }

        ThemePreview(draft, base, Modifier.fillMaxWidth().height(170.dp).padding(vertical = 8.dp))
        message?.let { Text(it, color = Color(0xFF6FD3D6), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Button(onClick = { draft = ThemeApply.capture("My theme " + (saved.size + 1), "builtin.preset.windmill", base) }) { Text("Create") }
            Button(enabled = draft != null, onClick = {
                draft?.let { d -> message = if (store.save(d)) "Saved \"${d.name}\"" else "Could not save"; refresh() }
            }) { Text("Save") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            TextButton(onClick = { importTheme.launch(arrayOf("application/json", "text/plain")) }) { Text("Import", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            TextButton(enabled = draft != null, onClick = { exportTheme.launch((draft?.name ?: "theme") + ".json") }) { Text("Export", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            TextButton(enabled = draft != null, onClick = { pickWallpaper.launch(arrayOf("image/*")) }) { Text("Wallpaper", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
        }

        draft?.let { d ->
            ThemeEditor(d, onChange = { draft = it }, onExtract = { extractColours(d) }, onPublish = {
                publishState = ThemePublishing.State.Idle
                publishOpen = true
            }, onApply = {
                message = if (ThemeApply.applyAndPersist(context, d, base)) "Applied \"${d.name}\"" else "Could not apply"
            }, onRemoveWallpaper = {
                d.wallpaper?.let { ThemeAssets.deleteWallpaper(context, it) }
                draft = d.copy(wallpaper = null)
            })
        }

        Text("Bundled themes", color = Color(0xFFB9C2C8), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        LazyColumn(Modifier.fillMaxWidth().height(150.dp)) {
            items(gallery) { preset ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, color = Color(0xFFE8ECEF), fontSize = 13.sp)
                    TextButton(onClick = {
                        val wall = preset.wallpaper?.let { ThemeAssets.importBundled(context, it) }
                        draft = ThemeDoc(
                            id = ThemeCodec.idFor(preset.name, preset.id, emptyMap()),
                            name = preset.name,
                            basePreset = preset.id,
                            overrides = preset.colours.mapValues { ThemeApply.formatColor(it.value) },
                            wallpaper = wall
                        )
                        message = if (preset.wallpaper != null && wall == null) {
                            "Wallpaper unavailable in this build; using colours only"
                        } else {
                            "Editing \"${preset.name}\""
                        }
                    }) { Text("Use", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
                }
            }
        }

        Text("Saved themes", color = Color(0xFFB9C2C8), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        LazyColumn(Modifier.fillMaxWidth().height(170.dp)) {
            items(saved) { doc ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(doc.name, color = Color(0xFFE8ECEF), fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { draft = doc }) { Text("Edit", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
                        TextButton(onClick = { store.duplicate(doc.id, doc.name + " copy")?.let { draft = it }; refresh() }) { Text("Copy", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
                        TextButton(onClick = { pendingDelete = doc }) { Text("Delete", color = Color(0xFFD67A7A), fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    importedText?.let { text ->
        AlertDialog(
            onDismissRequest = { importedText = null },
            title = { Text("Replace existing theme?") },
            text = { Text("A theme with this id already exists. Replace it?") },
            confirmButton = {
                TextButton(onClick = {
                    (ThemeCodec.decode(text) as? ThemeValidation.Ok)?.doc?.let { store.save(it); draft = it; refresh(); message = "Replaced" }
                    importedText = null
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { importedText = null }) { Text("Cancel") } }
        )
    }

    if (publishOpen && draft != null) {
        PublishDialog(
            doc = draft!!,
            state = publishState,
            onDismiss = { publishOpen = false },
            onPublish = { title, description, category, tags ->
                draft?.let { d ->
                    publishState = ThemePublishing.saveDraft(
                        context = context,
                        doc = d,
                        title = title,
                        description = description,
                        tags = tags,
                        category = category,
                        onState = { publishState = it }
                    )
                }
            }
        )
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete theme?") },
            text = { Text("\"${doc.name}\" will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { store.delete(doc.id); if (draft?.id == doc.id) draft = null; pendingDelete = null; refresh() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ThemeEditor(
    doc: ThemeDoc,
    onChange: (ThemeDoc) -> Unit,
    onExtract: () -> Unit,
    onPublish: () -> Unit,
    onApply: (ThemeDoc) -> Unit,
    onRemoveWallpaper: () -> Unit
) {
    val contextForPrefs = LocalContext.current
    var name by remember(doc.id) { mutableStateOf(doc.name) }
    Column(Modifier.fillMaxWidth().background(Color(0xFF171A1E), RoundedCornerShape(12.dp)).padding(12.dp)) {
        OutlinedTextField(name, onValueChange = { name = it; onChange(doc.copy(name = it)) }, label = { Text("Theme name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ThemeApply.COLOR_KEYS.forEach { key -> ColorRow(key, doc, onChange) }
        Text("Key opacity", color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Slider(
            value = ThemeApply.float(doc, ThemeApply.KEY_OPACITY, 1f),
            onValueChange = { v -> val ov = doc.overrides.toMutableMap(); ov[ThemeApply.KEY_OPACITY] = v.toString(); onChange(doc.copy(overrides = ov)) },
            valueRange = 0.2f..1f
        )
        Text("Corner radius", color = Color(0xFFB9C2C8), fontSize = 12.sp)
        Slider(
            value = ThemeApply.float(doc, ThemeApply.CORNER_RADIUS, 16f).coerceIn(6f, 30f),
            onValueChange = { v -> val ov = doc.overrides.toMutableMap(); ov[ThemeApply.CORNER_RADIUS] = v.toString(); onChange(doc.copy(overrides = ov)) },
            valueRange = 6f..30f
        )
        val tuning = WallpaperTuning.from(doc)
        fun tune(t: WallpaperTuning) { onChange(WallpaperTuning.apply(doc, t)) }

        Text("Wallpaper opacity", color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Slider(
            value = tuning.opacity,
            onValueChange = { tune(tuning.copy(opacity = it)) },
            valueRange = WallpaperTuning.MIN_OPACITY..WallpaperTuning.MAX_OPACITY
        )
        Text("Wallpaper blur", color = Color(0xFFB9C2C8), fontSize = 12.sp)
        Slider(
            value = tuning.blurDp,
            onValueChange = { tune(tuning.copy(blurDp = it)) },
            valueRange = WallpaperTuning.MIN_BLUR..WallpaperTuning.MAX_BLUR
        )
        Text("Wallpaper brightness", color = Color(0xFFB9C2C8), fontSize = 12.sp)
        Slider(
            value = tuning.brightness,
            onValueChange = { tune(tuning.copy(brightness = it)) },
            valueRange = WallpaperTuning.MIN_BRIGHTNESS..WallpaperTuning.MAX_BRIGHTNESS
        )
        Text("Wallpaper zoom", color = Color(0xFFB9C2C8), fontSize = 12.sp)
        Slider(
            value = tuning.scale,
            onValueChange = { tune(tuning.copy(scale = it)) },
            valueRange = WallpaperTuning.MIN_SCALE..WallpaperTuning.MAX_SCALE
        )
        Text("Horizontal position", color = Color(0xFFB9C2C8), fontSize = 12.sp)
        Slider(
            value = tuning.offsetX,
            onValueChange = { tune(tuning.copy(offsetX = it)) },
            valueRange = -WallpaperTuning.MAX_PAN..WallpaperTuning.MAX_PAN
        )
        Text("Vertical position", color = Color(0xFFB9C2C8), fontSize = 12.sp)
        Slider(
            value = tuning.offsetY,
            onValueChange = { tune(tuning.copy(offsetY = it)) },
            valueRange = -WallpaperTuning.MAX_PAN..WallpaperTuning.MAX_PAN
        )
        TextButton(onClick = onExtract) { Text("Colours from wallpaper", color = Color(0xFF6FD3D6), fontSize = 12.sp) }

        doc.wallpaper?.let { w ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(w, color = Color(0xFFB9C2C8), fontSize = 11.sp)
                TextButton(onClick = onRemoveWallpaper) { Text("Remove", color = Color(0xFFD67A7A), fontSize = 11.sp) }
            }
        }
        val issues = remember(doc) { ThemeApply.readabilityIssues(doc, KeyboardPrefs.current(contextForPrefs)) }
        if (issues.isNotEmpty()) {
            Text(
                "Low contrast: " + issues.joinToString(", "),
                color = Color(0xFFE0A94F),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Text("Contrast OK (4.5:1 or better)", color = Color(0xFF6FD3D6), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Button(onClick = { onApply(doc) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            TextButton(onClick = { onChange(ThemeApply.resetToPreset(doc)) }) { Text("Reset", color = Color(0xFFB9C2C8), fontSize = 12.sp) }
        }
        TextButton(
            onClick = onPublish,
            enabled = !doc.wallpaper.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Publish to community", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
    }
}

/** A hex field plus HSV sliders — a real picker, not a text box. */
@Composable
private fun ColorRow(key: String, doc: ThemeDoc, onChange: (ThemeDoc) -> Unit) {
    val current = ThemeApply.color(doc, key)
    var text by remember(doc.id, key, current) { mutableStateOf(current?.let { ThemeApply.formatColor(it) } ?: "") }
    val rgb = current?.let { Color(it) } ?: Color.Transparent
    val hsv = remember(current) { FloatArray(3).also { android.graphics.Color.colorToHSV(current ?: 0xFF000000.toInt(), it) } }

    fun set(v: Int) {
        val ov = doc.overrides.toMutableMap()
        ov[key] = ThemeApply.formatColor(v)
        onChange(doc.copy(overrides = ov))
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(22.dp).fillMaxWidth(0.12f).background(rgb, RoundedCornerShape(6.dp)))
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v
                    val parsed = ThemeApply.parseColor(v)
                    if (parsed != null) set(parsed) else {
                        val ov = doc.overrides.toMutableMap(); ov.remove(key); onChange(doc.copy(overrides = ov))
                    }
                },
                label = { Text(key.removePrefix("color"), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Slider(
            value = hsv[0],
            onValueChange = { h -> val out = FloatArray(3); android.graphics.Color.colorToHSV(current ?: 0xFF000000.toInt(), out); out[0] = h; set(android.graphics.Color.HSVToColor(out)) },
            valueRange = 0f..360f
        )
        Slider(
            value = hsv[1],
            onValueChange = { s -> val out = FloatArray(3); android.graphics.Color.colorToHSV(current ?: 0xFF000000.toInt(), out); out[1] = s; set(android.graphics.Color.HSVToColor(out)) },
            valueRange = 0f..1f
        )
        Slider(
            value = hsv[2],
            onValueChange = { v -> val out = FloatArray(3); android.graphics.Color.colorToHSV(current ?: 0xFF000000.toInt(), out); out[2] = v; set(android.graphics.Color.HSVToColor(out)) },
            valueRange = 0f..1f
        )
    }
}

@Composable
private fun ThemePreview(doc: ThemeDoc?, base: KeyboardSettings, modifier: Modifier) {
    val settings = remember(doc, base) { doc?.let { ThemeApply.applyTo(it, base) } ?: base }
    val palette = rememberKeyPalette(settings)
    val state = remember { KeyboardState() }
    val popup = remember { PopupController() }
    val layer = remember { CoordsHolder() }
    val glide = remember { GlideController() }
    val context = LocalContext.current
    val tuning = remember(doc) { doc?.let { WallpaperTuning.from(it) } ?: WallpaperTuning() }
    val wallpaper = remember(doc?.wallpaper) {
        doc?.wallpaper?.let { ThemeAssets.wallpaperFile(context, it) }
    }
    Box(modifier) {
        if (wallpaper != null && wallpaper.isFile) {
            AsyncImage(
                model = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = tuning.scale,
                        scaleY = tuning.scale,
                        translationX = tuning.offsetX * 600f,
                        translationY = tuning.offsetY * 600f
                    )
                    .alpha(tuning.opacity)
                    .blur(tuning.blurDp.dp)
            )
            if (tuning.brightness < 1f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - tuning.brightness)))
            } else if (tuning.brightness > 1f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = (tuning.brightness - 1f) * 0.4f)))
            }
        }
        KeyArea(state, PreviewActions, settings, palette, FontFamily.Default, popup, layer, glide)
    }
}

/**
 * Publish sheet.
 *
 * Shows genuine progress: validating, uploading, saving, then the server's answer. A failure keeps
 * the dialog open with the reason, because closing it would leave the user unsure whether anything
 * was published.
 */
@Composable
private fun PublishDialog(
    doc: ThemeDoc,
    state: ThemePublishing.State,
    onDismiss: () -> Unit,
    onPublish: (title: String, description: String, category: String, tags: List<String>) -> Unit
) {
    var title by remember(doc.id) { mutableStateOf(doc.name) }
    var description by remember(doc.id) { mutableStateOf("") }
    var tagsText by remember(doc.id) { mutableStateOf("") }
    var category by remember(doc.id) { mutableStateOf(PublishRules.CATEGORIES.first()) }

    val busy = state is ThemePublishing.State.Validating ||
        state is ThemePublishing.State.Uploading ||
        state is ThemePublishing.State.Saving ||
        state is ThemePublishing.State.Transferring ||
        state is ThemePublishing.State.Publishing

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Publish theme") },
        text = {
            Column {
                when (val s = state) {
                    is ThemePublishing.State.Done -> {
                        Text("Published. Other people can find it in the community now.", color = Color(0xFF6FD3D6))
                        Text("id: ${s.themeId}", color = Color(0xFFB9C2C8), fontSize = 10.sp)
                    }
                    is ThemePublishing.State.Failed -> {
                        Text(s.reason, color = Color(0xFFD67A7A))
                    }
                    is ThemePublishing.State.Uploading -> {
                        Text("Uploading wallpaper…", color = Color(0xFFB9C2C8))
                        Slider(value = s.fraction, onValueChange = {}, valueRange = 0f..1f)
                    }
                    is ThemePublishing.State.Saving -> Text("Saving draft…", color = Color(0xFFB9C2C8))
                    is ThemePublishing.State.DraftSaved -> Text("Draft saved. Publish it from My creations.", color = Color(0xFF6FD3D6))
                    is ThemePublishing.State.Transferring -> Text("Moving artwork to public storage…", color = Color(0xFFB9C2C8))
                    is ThemePublishing.State.Publishing -> Text("Publishing…", color = Color(0xFFB9C2C8))
                    is ThemePublishing.State.Validating -> Text("Checking…", color = Color(0xFFB9C2C8))
                    is ThemePublishing.State.Idle -> {
                        OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            description, { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            tagsText, { tagsText = it },
                            label = { Text("Tags, comma separated") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        Text("Category", color = Color(0xFFB9C2C8), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                val i = PublishRules.CATEGORIES.indexOf(category)
                                category = PublishRules.CATEGORIES[(i + 1) % PublishRules.CATEGORIES.size]
                            }) { Text(category, color = Color(0xFF6FD3D6), fontSize = 12.sp) }
                        }
                        if (doc.wallpaper.isNullOrBlank()) {
                            Text("This theme has no wallpaper, so it cannot be published yet.", color = Color(0xFFE0A94F), fontSize = 11.sp)
                        }
                    }
                }
                if (busy) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Color(0xFF6FD3D6))
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is ThemePublishing.State.Done -> TextButton(onClick = onDismiss) { Text("Close") }
                is ThemePublishing.State.Failed -> TextButton(onClick = onDismiss) { Text("Close") }
                else -> TextButton(
                    enabled = !busy,
                    onClick = { onPublish(title, description, category, PublishRules.parseTags(tagsText)) }
                ) { Text("Publish") }
            }
        },
        dismissButton = { if (!busy) TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
