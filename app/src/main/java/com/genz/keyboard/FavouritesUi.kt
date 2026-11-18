package com.genz.keyboard

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The signed-in user's saved themes.
 *
 * Everything here belongs to the account: signing out clears the list rather than leaving another
 * user's saved themes on screen.
 */
@Composable
fun FavouritesScreen(
    onBack: () -> Unit = {},
    onOpenTheme: (String) -> Unit = {},
    onBrowse: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ThemeSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var exhausted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun userId(): String? = CommunityAuth.currentToken(context)?.let { AuthResponse.subjectOf(it) }

    fun load() {
        val uid = userId()
        if (uid == null) {
            items = emptyList(); loading = false
            error = "Sign in to see your saved themes"
            return
        }
        loading = true; error = null; exhausted = false
        scope.launch {
            val token = CommunityAuth.currentToken(context)
            val result = withContext(Dispatchers.IO) {
                if (token == null) CommunityResult.Unauthorized("signed out")
                else CommunityApi.listFavourites(token, uid, 0)
            }
            loading = false
            when (result) {
                is CommunityResult.Ok -> {
                    items = result.value
                    exhausted = result.value.size < MarketplaceQuery.PAGE_SIZE
                }
                is CommunityResult.NotConfigured -> error = "Community is not configured"
                is CommunityResult.Unauthorized -> error = "Sign in to see your saved themes"
                is CommunityResult.NetworkError -> error = "You appear to be offline"
                is CommunityResult.ServerError -> error = "Server error (${result.code})"
            }
        }
    }

    fun loadMore() {
        val uid = userId() ?: return
        if (loadingMore || exhausted) return
        loadingMore = true
        scope.launch {
            val token = CommunityAuth.currentToken(context)
            val result = withContext(Dispatchers.IO) {
                if (token == null) CommunityResult.Unauthorized("signed out")
                else CommunityApi.listFavourites(token, uid, items.size)
            }
            loadingMore = false
            if (result is CommunityResult.Ok) {
                val known = items.map { it.id }.toSet()
                items = items + result.value.filter { it.id !in known }
                exhausted = result.value.size < MarketplaceQuery.PAGE_SIZE
            } else {
                exhausted = true
            }
        }
    }

    fun remove(themeId: String) {
        if (themeId in busyIds) return          // duplicate taps must not fire twice
        busyIds = busyIds + themeId
        scope.launch {
            val result = withContext(Dispatchers.IO) { CommunitySocial.toggleFavourite(context, themeId, true) }
            busyIds = busyIds - themeId
            when (result) {
                is CommunitySocial.State.Done -> items = items.filter { it.id != themeId }
                is CommunitySocial.State.Failed -> error = result.reason
                else -> Unit
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1013))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Saved themes", color = Color(0xFFE8ECEF), fontSize = 20.sp)
            TextButton(onClick = onBack) { Text("Back", color = Color(0xFF6FD3D6)) }
        }

        when {
            loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color(0xFF6FD3D6))
            }
            error != null -> {
                Text(error!!, color = Color(0xFFD67A7A), fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { load() }) { Text("Retry", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            }
            items.isEmpty() -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Nothing saved yet.", color = Color(0xFFB9C2C8), fontSize = 13.sp)
                TextButton(onClick = onBrowse) { Text("Browse the marketplace", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            }
            else -> LazyColumn(Modifier.fillMaxWidth().height(560.dp)) {
                items(items, key = { it.id }) { t ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Text(
                            t.name, color = Color(0xFFE8ECEF), fontSize = 14.sp,
                            modifier = Modifier.clickable { onOpenTheme(t.id) }
                        )
                        Text(
                            buildString {
                                if (t.authorHandle.isNotBlank()) append("@${t.authorHandle}  ")
                                append("%.1f★ (%d)  %s".format(t.averageRating, t.reviewCount, t.category))
                            },
                            color = Color(0xFFB9C2C8), fontSize = 11.sp
                        )
                        TextButton(onClick = { remove(t.id) }, enabled = t.id !in busyIds) {
                            Text(if (t.id in busyIds) "Removing…" else "Remove", color = Color(0xFFD67A7A), fontSize = 11.sp)
                        }
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
