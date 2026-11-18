package com.genz.keyboard

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Result of any community call.
 *
 * [NotConfigured] exists so the UI can say "community features are not set up on this build"
 * instead of showing an empty marketplace or, worse, pretending a publish succeeded.
 */
sealed interface CommunityResult<out T> {
    data class Ok<T>(val value: T) : CommunityResult<T>
    data object NotConfigured : CommunityResult<Nothing>
    data class Unauthorized(val message: String) : CommunityResult<Nothing>
    data class NetworkError(val message: String) : CommunityResult<Nothing>
    data class ServerError(val code: Int, val message: String) : CommunityResult<Nothing>
}

/**
 * Client for the Supabase-backed community backend.
 *
 * Uses the same `HttpURLConnection` stack as the rest of the app, so no new dependency is added.
 * Every call is authorised by the user's JWT; row-level security decides what that JWT may touch.
 */
object CommunityApi {

    private const val TIMEOUT_MS = 15_000

    private fun headers(token: String?): Map<String, String> {
        val h = LinkedHashMap<String, String>()
        h["apikey"] = CommunityConfig.anonKey
        h["Content-Type"] = "application/json"
        if (token != null) h["Authorization"] = "Bearer $token" else h["Authorization"] = "Bearer ${CommunityConfig.anonKey}"
        return h
    }

    private fun request(
        method: String,
        url: String,
        token: String?,
        body: String?
    ): CommunityResult<String> {
        if (!CommunityConfig.isConfigured) return CommunityResult.NotConfigured
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                headers(token).forEach { (k, v) -> setRequestProperty(k, v) }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code in 200..299 -> CommunityResult.Ok(text)
                code == 401 || code == 403 -> CommunityResult.Unauthorized(text.take(300))
                else -> CommunityResult.ServerError(code, text.take(300))
            }
        } catch (e: Exception) {
            CommunityResult.NetworkError(e.message ?: "network error")
        }
    }

    /** Sign in and return the access token. */
    fun signIn(email: String, password: String): CommunityResult<String> {
        val body = """{"email":${q(email)},"password":${q(password)}}"""
        return when (val r = request("POST", CommunityUrls.auth(CommunityConfig.baseUrl, "token?grant_type=password"), null, body)) {
            is CommunityResult.Ok -> {
                val o = ThemeJson.decode(r.value)
                val token = o?.get("access_token") as? String
                if (token == null) CommunityResult.Unauthorized("no access token in response")
                else CommunityResult.Ok(token)
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** Browse public themes, newest first, with an optional search term. */
    fun listThemes(token: String?, search: String? = null, limit: Int = 30): CommunityResult<List<ThemeSummary>> {
        val params = linkedMapOf(
            "select" to "id,name,description,tags,category,preview_path,downloads,version,profiles(handle)",
            "is_public" to "eq.true",
            "is_removed" to "eq.false",
            "order" to "created_at.desc",
            "limit" to limit.toString()
        )
        if (!search.isNullOrBlank()) params["name"] = "ilike.*${search.trim()}*"
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", params)
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value) ?: return CommunityResult.ServerError(0, "bad json")
                val rows = arr.filterIsInstance<Map<String, Any?>>()
                CommunityResult.Ok(CommunityModels.themes(rows, CommunityConfig.baseUrl))
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** Publish a theme. [docJson] must already have passed ThemeCodec validation. */
    fun publishTheme(
        token: String,
        name: String,
        description: String,
        tags: List<String>,
        category: String,
        docJson: String
    ): CommunityResult<String> {
        val tagsJson = tags.joinToString(",", "[", "]") { q(it) }
        val body = """{"name":${q(name)},"description":${q(description)},"tags":$tagsJson,""" +
            """"category":${q(category)},"doc":$docJson}"""
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes")
        return when (val r = request("POST", url, token, body)) {
            is CommunityResult.Ok -> CommunityResult.Ok(r.value)
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** File a report against a theme, review, post, comment or profile. */
    fun report(token: String, subjectType: String, subjectId: String, reason: String): CommunityResult<String> {
        val body = """{"subject_type":${q(subjectType)},"subject_id":${q(subjectId)},"reason":${q(reason)}}"""
        return request("POST", CommunityUrls.table(CommunityConfig.baseUrl, "reports"), token, body)
    }

    /** Post a review; the schema enforces one review per account per theme. */
    fun addReview(token: String, themeId: String, rating: Int, body: String): CommunityResult<String> {
        val json = """{"theme_id":${q(themeId)},"rating":$rating,"body":${q(body)}}"""
        return request("POST", CommunityUrls.table(CommunityConfig.baseUrl, "reviews"), token, json)
    }

    private fun q(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
