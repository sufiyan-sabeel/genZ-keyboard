package com.genz.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The marketplace.
 *
 * Sections, filters and paging all come from [MarketplaceQuery]; every row shown came from the
 * server. An empty section renders as "nothing here yet", never as a populated list of filler, and
 * a failed request is distinguishable from an empty one.
 */
@Composable
fun MarketplaceScreen(onOpenTheme: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var section by remember { mutableStateOf(MarketplaceQuery.Section.NEWEST) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }

    var items by remember { mutableStateOf<List<ThemeSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var exhausted by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }

    val accumulator = remember { MarketplaceQuery.PageAccumulator(section) }
    val listState = rememberLazyListState()

    fun queryKey() = "${section.name}|${search.trim()}|${category ?: ""}"

    fun reload() {
        loading = true; error = null; exhausted = false
        accumulator.reset(queryKey())
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                CommunityApi.browse(
                    CommunityAuth.currentToken(context),
                    MarketplaceQuery.Page(section, 0),
                    search.ifBlank { null },
                    category
                )
            }
            loading = false; loadedOnce = true
            when (result) {
                is CommunityResult.Ok -> {
                    accumulator.addPage(result.value)
                    items = accumulator.items()
                    exhausted = result.value.size < MarketplaceQuery.PAGE_SIZE
                }
                is CommunityResult.NotConfigured -> error = "Community is not configured"
                is CommunityResult.Unauthorized -> error = "Session expired. Sign in again."
                is CommunityResult.NetworkError -> error = "You appear to be offline"
                is CommunityResult.ServerError -> error = "Server error (${result.code})"
            }
        }
    }

    fun loadMore() {
        if (loadingMore || exhausted || loading) return
        loadingMore = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                CommunityApi.browse(
                    CommunityAuth.currentToken(context),
                    MarketplaceQuery.Page(section, accumulator.size()),
                    search.ifBlank { null },
                    category
                )
            }
            loadingMore = false
            when (result) {
                is CommunityResult.Ok -> {
                    // a stale page for an abandoned query is dropped rather than appended
                    if (accumulator.queryKey == queryKey()) {
                        val added = accumulator.addPage(result.value)
                        items = accumulator.items()
                        exhausted = result.value.size < MarketplaceQuery.PAGE_SIZE || added == 0
                    }
                }
                else -> exhausted = true
            }
        }
    }

    LaunchedEffect(section, category) { reload() }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            search, { search = it },
            label = { Text("Search themes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { reload() }) { Text("Search") }
            TextButton(onClick = { reload() }) { Text("Refresh", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
        }

        // sections
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MarketplaceQuery.Section.entries
                .filter { it != MarketplaceQuery.Section.TRENDING }
                .forEach { s ->
                    TextButton(onClick = { section = s }) {
                        Text(
                            s.label,
                            color = if (s == section) Color(0xFF6FD3D6) else Color(0xFFB9C2C8),
                            fontSize = 12.sp
                        )
                    }
                }
        }

        // categories
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(onClick = { category = null }) {
                Text("All", color = if (category == null) Color(0xFF6FD3D6) else Color(0xFFB9C2C8), fontSize = 11.sp)
            }
            MarketplaceQuery.CATEGORIES.forEach { c ->
                TextButton(onClick = { category = if (category == c) null else c }) {
                    Text(c, color = if (category == c) Color(0xFF6FD3D6) else Color(0xFFB9C2C8), fontSize = 11.sp)
                }
            }
        }

        when {
            loading -> Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color(0xFF6FD3D6))
            }
            error != null -> {
                Text(error!!, color = Color(0xFFD67A7A), fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { reload() }) { Text("Retry", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            }
            items.isEmpty() && loadedOnce -> Text(
                "Nothing here yet in ${section.label.lowercase()}.",
                color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(16.dp)
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(430.dp)) {
                items(items, key = { it.id }) { t ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onOpenTheme(t.id) }
                    ) {
                        Text(t.name, color = Color(0xFFE8ECEF), fontSize = 14.sp)
                        Text(
                            buildString {
                                if (t.authorHandle.isNotBlank()) append("@${t.authorHandle}  ")
                                append("%.1f★ (%d)  %d downloads".format(t.averageRating, t.reviewCount, t.downloads))
                            },
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
