package com.genz.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
 * The design community feed.
 *
 * Counts come from the backend's own aggregates; nothing here is computed locally or filled in.
 */
@Composable
fun DesignFeedScreen(
    onBack: () -> Unit = {},
    onOpenPost: (String) -> Unit = {},
    onOpenCreator: (String) -> Unit = {},
    onCreate: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<DesignPost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var exhausted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadedOnce by remember { mutableStateOf(false) }

    fun load() {
        loading = true; error = null; exhausted = false
        scope.launch {
            val result = withContext(Dispatchers.IO) { CommunityApi.listPosts(0) }
            loading = false; loadedOnce = true
            when (result) {
                is CommunityResult.Ok -> {
                    posts = result.value
                    exhausted = PostPages.isExhausted(result.value.size)
                }
                is CommunityResult.NotConfigured -> error = "Community is not configured"
                is CommunityResult.NetworkError -> error = "You appear to be offline"
                is CommunityResult.ServerError -> error = "Server error (${result.code})"
                is CommunityResult.Unauthorized -> error = "Sign in to see the community"
            }
        }
    }

    fun loadMore() {
        if (loadingMore || exhausted) return
        loadingMore = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { CommunityApi.listPosts(posts.size) }
            loadingMore = false
            if (result is CommunityResult.Ok) {
                posts = PostPages.merge(posts, result.value)
                exhausted = PostPages.isExhausted(result.value.size)
            } else {
                exhausted = true
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1013))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Design community", color = Color(0xFFE8ECEF), fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCreate) { Text("Post", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
                TextButton(onClick = onBack) { Text("Back", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            }
        }

        when {
            loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color(0xFF6FD3D6))
            }
            error != null -> {
                Text(error!!, color = Color(0xFFD67A7A), fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { load() }) { Text("Retry", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            }
            posts.isEmpty() && loadedOnce -> Text(
                "No designs shared yet.", color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(16.dp)
            )
            else -> LazyColumn(Modifier.fillMaxWidth().height(540.dp)) {
                items(posts, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            p.title, color = Color(0xFFE8ECEF), fontSize = 15.sp,
                            modifier = Modifier.clickable { onOpenPost(p.id) }
                        )
                        if (p.authorHandle.isNotBlank()) {
                            Text(
                                "by @${p.authorHandle}", color = Color(0xFF6FD3D6), fontSize = 11.sp,
                                modifier = Modifier.clickable { if (p.authorId.isNotBlank()) onOpenCreator(p.authorId) }
                            )
                        }
                        if (p.body.isNotBlank()) {
                            Text(p.body, color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        p.imagePaths.firstOrNull()?.let { path ->
                            AsyncImage(
                                model = CommunityUrls.storage(CommunityConfig.baseUrl, "post-images", path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 6.dp)
                            )
                        }
                        Text(
                            "${p.likeCount} likes · ${p.commentCount} comments" +
                                if (p.themeId != null) " · has a theme" else "",
                            color = Color(0xFFB9C2C8), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
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
