package com.genz.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A community theme, in full.
 *
 * The wallpaper is fetched from Storage and shown large; the metadata shown is what the backend
 * returned, not what a previous list screen cached, so a stale rating cannot be displayed here.
 */
@Composable
fun ThemeDetailScreen(themeId: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var theme by remember { mutableStateOf<ThemeSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var manageState by remember { mutableStateOf<CommunityManage.State>(CommunityManage.State.Idle) }

    fun load() {
        loading = true; error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                CommunityApi.fetchTheme(CommunityAuth.currentToken(context), themeId)
            }
            loading = false
            when (result) {
                is CommunityResult.Ok -> theme = result.value
                is CommunityResult.NotConfigured -> error = "Community is not configured"
                is CommunityResult.Unauthorized -> error = "Sign in to view this theme"
                is CommunityResult.NetworkError -> error = "Could not reach the server"
                is CommunityResult.ServerError ->
                    error = if (result.code == 404) "This theme is no longer available" else "Server error (${result.code})"
            }
        }
    }

    LaunchedEffect(themeId) { load() }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1013))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Theme", color = Color(0xFFE8ECEF), fontSize = 20.sp)
            TextButton(onClick = onBack) { Text("Back", color = Color(0xFF6FD3D6)) }
        }

        when {
            loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color(0xFF6FD3D6))
            }
            error != null -> {
                Text(error!!, color = Color(0xFFD67A7A), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { load() }) { Text("Retry", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            }
            theme != null -> {
                val t = theme!!
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (t.previewUrl != null) {
                        AsyncImage(
                            model = t.previewUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )
                    }
                    Text(t.name, color = Color(0xFFE8ECEF), fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
                    if (t.authorHandle.isNotBlank()) {
                        Text("by @${t.authorHandle}", color = Color(0xFF6FD3D6), fontSize = 12.sp)
                    }
                    Text(
                        "%.1f★ · %d reviews · %d downloads · v%d".format(t.averageRating, t.reviewCount, t.downloads, t.version),
                        color = Color(0xFFB9C2C8), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                    if (t.description.isNotBlank()) {
                        Text(t.description, color = Color(0xFFE8ECEF), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (t.tags.isNotEmpty()) {
                        Text(t.tags.joinToString(" · "), color = Color(0xFF6FD3D6), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Text("Category: ${t.category}", color = Color(0xFFB9C2C8), fontSize = 11.sp)

                    when (val s = manageState) {
                        is CommunityManage.State.Failed -> Text(s.reason, color = Color(0xFFD67A7A), fontSize = 12.sp)
                        is CommunityManage.State.Done -> Text("Done", color = Color(0xFF6FD3D6), fontSize = 12.sp)
                        is CommunityManage.State.Working -> Text("Working…", color = Color(0xFFB9C2C8), fontSize = 12.sp)
                        is CommunityManage.State.Idle -> Unit
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            manageState = CommunityManage.State.Working
                            scope.launch {
                                manageState = withContext(Dispatchers.IO) {
                                    CommunityManage.unpublish(context, t.id)
                                }
                                load()
                            }
                        }) { Text("Unpublish") }
                        TextButton(onClick = {
                            manageState = CommunityManage.State.Working
                            scope.launch {
                                manageState = withContext(Dispatchers.IO) {
                                    CommunityManage.delete(context, t.id, null)
                                }
                                onBack()
                            }
                        }) { Text("Delete", color = Color(0xFFD67A7A), fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}
