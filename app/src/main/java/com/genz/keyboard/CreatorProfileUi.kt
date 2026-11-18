package com.genz.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
 * A creator's public page.
 *
 * Shows only what `public_profiles` exposes — display name, handle, bio, avatar. There is no email
 * or token in the payload to leak, because the query never asks for one.
 */
@Composable
fun CreatorProfileScreen(userId: String, onBack: () -> Unit = {}, onOpenTheme: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<CreatorProfile?>(null) }
    var themes by remember { mutableStateOf<List<ThemeSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var exhausted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true; error = null; exhausted = false
        scope.launch {
            val p = withContext(Dispatchers.IO) { CommunityApi.fetchProfile(userId) }
            loading = false
            when (p) {
                is CommunityResult.Ok -> {
                    profile = p.value
                    val t = withContext(Dispatchers.IO) { CommunityApi.fetchCreatorThemes(userId, 0) }
                    when (t) {
                        is CommunityResult.Ok -> {
                            themes = t.value
                            exhausted = t.value.size < MarketplaceQuery.PAGE_SIZE
                        }
                        is CommunityResult.NetworkError -> error = "You appear to be offline"
                        is CommunityResult.ServerError -> error = "Could not load themes (${t.code})"
                        else -> error = "Could not load themes"
                    }
                }
                is CommunityResult.NotConfigured -> error = "Community is not configured"
                is CommunityResult.NetworkError -> error = "You appear to be offline"
                is CommunityResult.ServerError ->
                    error = if (p.code == 404) "This creator is no longer available"
                    else if (p.code == 400) "Invalid creator" else "Server error (${p.code})"
                is CommunityResult.Unauthorized -> error = "This creator is not available"
            }
        }
    }

    fun loadMore() {
        if (loadingMore || exhausted) return
        loadingMore = true
        scope.launch {
            val t = withContext(Dispatchers.IO) { CommunityApi.fetchCreatorThemes(userId, themes.size) }
            loadingMore = false
            if (t is CommunityResult.Ok) {
                val known = themes.map { it.id }.toSet()
                themes = themes + t.value.filter { it.id !in known }
                exhausted = t.value.size < MarketplaceQuery.PAGE_SIZE
            } else {
                exhausted = true
            }
        }
    }

    LaunchedEffect(userId) { load() }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1013))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Creator", color = Color(0xFFE8ECEF), fontSize = 20.sp)
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
            profile != null -> {
                val p = profile!!
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p.avatarUrl != null) {
                            AsyncImage(
                                model = p.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp)
                            )
                        } else {
                            // initial-based fallback rather than a broken image
                            Box(
                                Modifier.size(56.dp).background(Color(0xFF2E3A3C), CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text(p.initial, color = Color(0xFF6FD3D6), fontSize = 22.sp) }
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(p.displayName, color = Color(0xFFE8ECEF), fontSize = 16.sp)
                            if (p.handle.isNotBlank()) {
                                Text("@${p.handle}", color = Color(0xFF6FD3D6), fontSize = 12.sp)
                            }
                        }
                    }
                    p.bio?.let {
                        Text(it, color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text("${themes.size} published themes", color = Color(0xFFB9C2C8), fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }

                if (themes.isEmpty()) {
                    Text("No public themes yet.", color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(480.dp)) {
                        items(themes, key = { it.id }) { t ->
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clickable { onOpenTheme(t.id) }
                            ) {
                                Text(t.name, color = Color(0xFFE8ECEF), fontSize = 14.sp)
                                Text(
                                    "%.1f★ (%d)  %d downloads".format(t.averageRating, t.reviewCount, t.downloads),
                                    color = Color(0xFFB9C2C8), fontSize = 11.sp
                                )
                            }
                        }
                        if (!exhausted) {
                            item {
                                TextButton(onClick = { loadMore() }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (loadingMore) "Loading…" else "Load more", color = Color(0xFF6FD3D6), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
