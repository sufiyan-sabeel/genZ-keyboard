package com.genz.keyboard

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Community entry point.
 *
 * Shows the sign-in form when there is no session and the marketplace when there is. Every state
 * is explicit — loading, empty, and error are distinct, so a network failure never looks like
 * "no themes exist".
 */
@Composable
fun CommunityScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(CommunitySession.isSignedIn(context)) }
    var handle by remember { mutableStateOf(CommunitySession.handle(context)) }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1013))) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (signedIn) "Community" else "Sign in",
                color = Color(0xFFE8ECEF), fontSize = 20.sp, fontFamily = FontFamily.SansSerif
            )
            TextButton(onClick = onBack) { Text("Back", color = Color(0xFF6FD3D6)) }
        }

        if (!CommunityConfig.isConfigured) {
            NotConfiguredNotice()
            return@Column
        }

        if (signedIn) {
            MarketplaceBody(
                handle = handle,
                onSignOut = {
                    CommunitySession.clear(context)
                    signedIn = false
                    handle = null
                }
            )
        } else {
            SignInForm(onSignedIn = { h ->
                handle = h
                signedIn = true
            })
        }
    }
}

@Composable
private fun NotConfiguredNotice() {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Community is not set up on this build.", color = Color(0xFFE0A94F), fontSize = 14.sp)
        Text(
            "The app needs a backend URL and publishable key in local.properties. " +
                "The marketplace, reviews and publishing stay disabled until then.",
            color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun SignInForm(onSignedIn: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun run(register: Boolean) {
        if (email.isBlank() || password.length < 6) {
            error = "Enter an email and a password of at least 6 characters"
            return
        }
        busy = true; error = null; notice = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (register) CommunityApi.signUp(email.trim(), password)
                else CommunityApi.signIn(email.trim(), password)
            }
            busy = false
            when (result) {
                is CommunityResult.Ok -> {
                    if (register) {
                        // sign-up may require email confirmation before a token is issued
                        notice = "Account created. Check your email to confirm, then sign in."
                    } else {
                        // signUp returns CommunityResult<String>, so the merged type is Any
                        val tokens = result.value as? AuthTokens
                        if (tokens == null) {
                            error = "Unexpected sign-in response"
                        } else {
                            val subject = AuthResponse.subjectOf(tokens.accessToken)
                            CommunitySession.save(context, tokens, subject)
                            onSignedIn(subject)
                        }
                    }
                }
                is CommunityResult.NotConfigured -> error = "Community is not configured on this build"
                is CommunityResult.Unauthorized -> error = "Email or password is incorrect"
                is CommunityResult.NetworkError -> error = "Could not reach the server. Check your connection."
                is CommunityResult.ServerError -> error = "Sign-in failed (${result.code})"
            }
            password = ""
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        error?.let { Text(it, color = Color(0xFFD67A7A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        notice?.let { Text(it, color = Color(0xFF6FD3D6), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }

        if (busy) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color(0xFF6FD3D6))
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run(false) }, modifier = Modifier.weight(1f)) { Text("Sign in") }
                TextButton(onClick = { run(true) }) { Text("Create account", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun MarketplaceBody(handle: String?, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var themes by remember { mutableStateOf<List<ThemeSummary>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadedOnce by remember { mutableStateOf(false) }

    fun refreshList() {
        busy = true; error = null
        scope.launch {
            val session: AuthTokens? = CommunitySession.load(context)
            val token: String? = session?.accessToken
            val result = withContext(Dispatchers.IO) { CommunityApi.listThemes(token, search.ifBlank { null }) }
            busy = false; loadedOnce = true
            when (result) {
                is CommunityResult.Ok -> themes = result.value
                is CommunityResult.NotConfigured -> error = "Community is not configured"
                is CommunityResult.Unauthorized -> error = "Session expired. Sign in again."
                is CommunityResult.NetworkError -> error = "Could not reach the server."
                is CommunityResult.ServerError -> error = "Server error (${result.code})"
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { refreshList() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(handle?.let { "Signed in" } ?: "Signed in", color = Color(0xFFB9C2C8), fontSize = 12.sp)
            TextButton(onClick = onSignOut) { Text("Sign out", color = Color(0xFFD67A7A), fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, label = { Text("Search themes") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { refreshList() }) { Text("Go") }
        }

        error?.let {
            Text(it, color = Color(0xFFD67A7A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { refreshList() }) { Text("Retry", color = Color(0xFF6FD3D6), fontSize = 12.sp) }
        }

        if (busy) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Color(0xFF6FD3D6))
            }
        } else if (error == null && themes.isEmpty() && loadedOnce) {
            Text("No themes published yet.", color = Color(0xFFB9C2C8), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(520.dp).padding(top = 8.dp)) {
                items(themes) { t ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(t.name, color = Color(0xFFE8ECEF), fontSize = 14.sp)
                        Text(
                            buildString {
                                if (t.authorHandle.isNotBlank()) append("@${t.authorHandle}  ")
                                append("%.1f★ (%d)  %d downloads".format(t.averageRating, t.reviewCount, t.downloads))
                            },
                            color = Color(0xFFB9C2C8), fontSize = 11.sp
                        )
                        if (t.tags.isNotEmpty()) {
                            Text(t.tags.joinToString(" · "), color = Color(0xFF6FD3D6), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
