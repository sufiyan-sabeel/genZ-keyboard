package com.genz.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A gallery card for one bundled theme.
 *
 * Everything shown comes from the parsed [PresetEntry] — the swatches are the theme's own colours, not
 * a second hardcoded palette that could drift from the real one.
 */
@Composable
internal fun ThemeGalleryCard(
    preset: PresetEntry,
    selected: Boolean,
    dark: Boolean,
    onUse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surface = if (dark) Color(0xFF15191C) else Color(0xFFF3F1EE)
    val title = if (dark) Color(0xFFE8ECEF) else Color(0xFF14181B)
    val muted = if (dark) Color(0xFF8A9299) else Color(0xFF5C6469)
    val outline = if (selected) Color(0xFF6FD3D6) else Color.Transparent

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .then(if (selected) Modifier.background(outline.copy(alpha = 0.12f)) else Modifier)
            .padding(10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(preset.name)
                    if (selected) append(", currently selected")
                    append(". ").append(preset.description)
                }
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // the theme's own wallpaper, sampled from the same seven tokens the keyboard draws with
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(preset.colours["key"] ?: 0xFF1E1E1E.toInt()))
                    .semantics { contentDescription = "${preset.name} artwork preview" }
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(preset.colours["accent"] ?: 0xFF6FD3D6.toInt()))
                )
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, color = title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (selected) {
                        Text(
                            "  •  Selected",
                            color = Color(0xFF6FD3D6), fontSize = 10.sp
                        )
                    }
                }
                Text(
                    preset.description,
                    color = muted, fontSize = 10.sp,
                    maxLines = 2
                )
            }
            TextButton(onClick = onUse) {
                Text(if (selected) "Editing" else "Use", color = Color(0xFF6FD3D6), fontSize = 12.sp)
            }
        }

        // the theme's real colours, in the order the renderer consumes them
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ThemeContrast.PAIRS.map { (fg, bg) -> bg }.distinct().forEach { token ->
                preset.colours[token]?.let { argb ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(argb))
                            .semantics { contentDescription = "$token ${ThemeApply.formatColor(argb)}" }
                    )
                }
            }
        }
    }
}

/**
 * A reserved position: artwork is not licensed yet, so there is nothing to show or apply.
 *
 * Deliberately not clickable — no onClick, no button, muted colours — so it can never be mistaken for
 * a theme the user can select. The label says so in words as well, because a disabled-looking card
 * alone is ambiguous.
 */
@Composable
internal fun ReservedSlotCard(
    slot: ThemePresetIndex.ReservedSlot,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    val surface = if (dark) Color(0xFF101315) else Color(0xFFEDEBE7)
    val muted = if (dark) Color(0xFF5A6268) else Color(0xFF8B9196)

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .padding(10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Reserved slot. Artwork coming soon. Not available."
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (dark) Color(0xFF1A1E21) else Color(0xFFE2DFDA))
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("Reserved", color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("Artwork coming soon", color = muted, fontSize = 10.sp)
        }
    }
}
