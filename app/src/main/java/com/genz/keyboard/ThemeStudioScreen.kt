package com.genz.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * The Theme Studio.
 *
 * The preview is the production `KeyArea` driven by the production `rememberKeyPalette`, fed a
 * settings copy produced by [ThemeApply]. There is no second renderer and no separate preview
 * palette, so what the studio shows is what the keyboard draws.
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

    LaunchedEffect(Unit) { saved = store.list() }

    fun refresh() { saved = store.list() }

    Column(Modifier.fillMaxWidth().background(Color(0xFF0E1013)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Theme Studio", color = Color(0xFFE8ECEF), fontSize = 20.sp, fontFamily = FontFamily.SansSerif)
            TextButton(onClick = onClose) { Text("Close", color = Color(0xFF6FD3D6)) }
        }

        ThemePreview(draft, base, Modifier.fillMaxWidth().height(180.dp).padding(vertical = 8.dp))

        message?.let { Text(it, color = Color(0xFF6FD3D6), fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Button(onClick = {
                val name = "My theme " + (saved.size + 1)
                draft = ThemeApply.capture(name, "builtin.preset.windmill", base)
            }) { Text("Create") }
            Button(onClick = {
                draft?.let { d ->
                    val ok = store.save(d)
                    message = if (ok) "Saved \"${d.name}\"" else "Could not save"
                    refresh()
                }
            }, enabled = draft != null) { Text("Save") }
        }

        draft?.let { d -> ThemeEditor(d, onChange = { draft = it }, onApply = { message = "Applied" }) }

        Text("Saved themes", color = Color(0xFFB9C2C8), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        LazyColumn(Modifier.fillMaxWidth().height(200.dp)) {
            items(saved) { doc ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(doc.name, color = Color(0xFFE8ECEF), fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { draft = doc }) { Text("Edit", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
                        TextButton(onClick = {
                            store.duplicate(doc.id, doc.name + " copy")?.let { draft = it }
                            refresh()
                        }) { Text("Duplicate", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
                        TextButton(onClick = { pendingDelete = doc }) { Text("Delete", color = Color(0xFFD67A7A), fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete theme?") },
            text = { Text("\"${doc.name}\" will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(doc.id)
                    if (draft?.id == doc.id) draft = null
                    pendingDelete = null
                    refresh()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ThemeEditor(doc: ThemeDoc, onChange: (ThemeDoc) -> Unit, onApply: (ThemeDoc) -> Unit) {
    var name by remember(doc.id) { mutableStateOf(doc.name) }
    Column(Modifier.fillMaxWidth().background(Color(0xFF171A1E), RoundedCornerShape(12.dp)).padding(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onChange(doc.copy(id = doc.id, name = it))
            },
            label = { Text("Theme name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ThemeApply.COLOR_KEYS.forEach { key ->
            var text by remember(doc.id, key) { mutableStateOf(doc.overrides[key] ?: "") }
            OutlinedTextField(
                value = text,
                onValueChange = { v ->
                    text = v
                    val ov = doc.overrides.toMutableMap()
                    if (ThemeApply.parseColor(v) != null) ov[key] = v else ov.remove(key)
                    onChange(doc.copy(overrides = ov))
                },
                label = { Text(key.removePrefix("color")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
        Text("Key opacity", color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = ThemeApply.float(doc, ThemeApply.KEY_OPACITY, 1f),
            onValueChange = { v ->
                val ov = doc.overrides.toMutableMap()
                ov[ThemeApply.KEY_OPACITY] = v.toString()
                onChange(doc.copy(overrides = ov))
            },
            valueRange = 0.2f..1f
        )
        Text("Corner radius", color = Color(0xFFB9C2C8), fontSize = 12.sp)
        Slider(
            value = ThemeApply.float(doc, ThemeApply.CORNER_RADIUS, 16f),
            onValueChange = { v ->
                val ov = doc.overrides.toMutableMap()
                ov[ThemeApply.CORNER_RADIUS] = v.toString()
                onChange(doc.copy(overrides = ov))
            },
            valueRange = 0f..40f
        )
        TextButton(onClick = { onApply(doc) }) { Text("Apply to keyboard", color = Color(0xFF6FD3D6)) }
    }
}

/**
 * Live preview: the production key grid, rendered from the draft theme.
 */
@Composable
private fun ThemePreview(doc: ThemeDoc?, base: KeyboardSettings, modifier: Modifier) {
    val settings = remember(doc, base) { doc?.let { ThemeApply.applyTo(it, base) } ?: base }
    val palette = rememberKeyPalette(settings)
    val state = remember { KeyboardState() }
    val popup = remember { PopupController() }
    val layer = remember { CoordsHolder() }
    val glide = remember { GlideController() }
    Box(modifier) {
        KeyArea(
            state = state,
            actions = PreviewActions,
            settings = settings,
            palette = palette,
            font = FontFamily.Default,
            popup = popup,
            layer = layer,
            glide = glide
        )
    }
}
