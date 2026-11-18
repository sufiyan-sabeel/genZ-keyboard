package com.genz.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reviews on a theme.
 *
 * The headline average and count are the server's aggregate, never recomputed from the page shown
 * here. The user's own review is offered for editing rather than a second submission, because the
 * database would refuse it anyway.
 */
@Composable
fun ReviewsSection(themeId: String, onRatingChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<CommunityReviews.State>(CommunityReviews.State.Idle) }
    var order by remember { mutableStateOf(ReviewRules.Sort.NEWEST) }
    var myReview by remember { mutableStateOf<ThemeReview?>(null) }
    var rating by remember { mutableStateOf(5) }
    var body by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<CommunityReviews.State>(CommunityReviews.State.Idle) }

    fun refresh() {
        scope.launch {
            state = withContext(Dispatchers.IO) { CommunityReviews.load(context, themeId, order) }
            val token = CommunityAuth.currentToken(context)
            val uid = token?.let { AuthResponse.subjectOf(it) }
            if (token != null && uid != null) {
                val mine = withContext(Dispatchers.IO) { CommunityApi.myReview(token, themeId, uid) }
                if (mine is CommunityResult.Ok) {
                    myReview = mine.value
                    mine.value?.let { rating = it.rating; body = it.body }
                }
            }
        }
    }

    LaunchedEffect(themeId, order) { refresh() }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        val loaded = state as? CommunityReviews.State.Loaded
        Text(
            if (loaded != null) "Reviews · ${loaded.average}★ · ${loaded.count}" else "Reviews",
            color = Color(0xFFE8ECEF), fontSize = 14.sp
        )

        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReviewRules.Sort.entries.forEach { s ->
                TextButton(onClick = { order = s }) {
                    Text(s.label, color = if (s == order) Color(0xFF6FD3D6) else Color(0xFFB9C2C8), fontSize = 11.sp)
                }
            }
        }

        when (val s = state) {
            is CommunityReviews.State.Failed -> Text(s.reason, color = Color(0xFFD67A7A), fontSize = 12.sp)
            is CommunityReviews.State.Loaded ->
                if (s.reviews.isEmpty()) Text("No reviews yet.", color = Color(0xFFB9C2C8), fontSize = 12.sp)
                else s.reviews.forEach { r ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("${r.rating}★  @${r.authorHandle}", color = Color(0xFFE8ECEF), fontSize = 12.sp)
                        if (r.body.isNotBlank()) Text(r.body, color = Color(0xFFB9C2C8), fontSize = 11.sp)
                    }
                }
            else -> Unit
        }

        // the caller's own review: submit once, then edit or delete
        Column(Modifier.fillMaxWidth().background(Color(0xFF171A1E)).padding(10.dp).padding(top = 8.dp)) {
            Text(if (myReview != null) "Your review" else "Write a review", color = Color(0xFFB9C2C8), fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (ReviewRules.MIN_RATING..ReviewRules.MAX_RATING).forEach { star ->
                    TextButton(onClick = { rating = star }) {
                        Text(
                            if (star <= rating) "★" else "☆",
                            color = if (star <= rating) Color(0xFFE0A94F) else Color(0xFFB9C2C8),
                            fontSize = 16.sp
                        )
                    }
                }
            }
            OutlinedTextField(
                body, { body = it },
                label = { Text("Review (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    action = CommunityReviews.State.Working
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val mine = myReview
                            if (mine != null) CommunityReviews.edit(context, mine.id, rating, body)
                            else CommunityReviews.submit(context, themeId, rating, body)
                        }
                        action = result
                        if (result is CommunityReviews.State.Done) { refresh(); onRatingChanged() }
                    }
                }) { Text(if (myReview != null) "Update" else "Post") }

                myReview?.let { mine ->
                    TextButton(onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { CommunityReviews.delete(context, mine.id) }
                            action = result
                            if (result is CommunityReviews.State.Done) {
                                myReview = null; body = ""; rating = 5; refresh(); onRatingChanged()
                            }
                        }
                    }) { Text("Delete", color = Color(0xFFD67A7A), fontSize = 12.sp) }
                }
            }
            when (val a = action) {
                is CommunityReviews.State.Done -> Text(a.message, color = Color(0xFF6FD3D6), fontSize = 11.sp)
                is CommunityReviews.State.Failed -> Text(a.reason, color = Color(0xFFD67A7A), fontSize = 11.sp)
                else -> Unit
            }
        }
    }
}
