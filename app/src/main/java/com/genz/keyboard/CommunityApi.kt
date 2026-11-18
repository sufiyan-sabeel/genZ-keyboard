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
    fun signIn(email: String, password: String): CommunityResult<AuthTokens> {
        val body = """{"email":${q(email)},"password":${q(password)}}"""
        return when (val r = request("POST", CommunityUrls.auth(CommunityConfig.baseUrl, "token?grant_type=password"), null, body)) {
            is CommunityResult.Ok -> AuthResponse.parse(r.value)
                ?.let { CommunityResult.Ok(it) }
                ?: CommunityResult.Unauthorized("no access token in response")
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** Register a new account. The schema creates the profile row on first sign-in. */
    fun signUp(email: String, password: String): CommunityResult<String> {
        val body = """{"email":${q(email)},"password":${q(password)}}"""
        return request("POST", CommunityUrls.auth(CommunityConfig.baseUrl, "signup"), null, body)
    }

    /** Exchange a refresh token for a fresh access token. */
    fun refresh(refreshToken: String): CommunityResult<AuthTokens> {
        val body = """{"refresh_token":${q(refreshToken)}}"""
        val url = CommunityUrls.auth(CommunityConfig.baseUrl, "token?grant_type=refresh_token")
        return when (val r = request("POST", url, null, body)) {
            is CommunityResult.Ok -> AuthResponse.parse(r.value)
                ?.let { CommunityResult.Ok(it) }
                ?: CommunityResult.ServerError(0, "malformed token response")
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

    /** One page of one marketplace section. */
    fun browse(
        token: String?,
        page: MarketplaceQuery.Page,
        search: String? = null,
        category: String? = null
    ): CommunityResult<List<ThemeSummary>> {
        val url = CommunityUrls.table(
            CommunityConfig.baseUrl, "themes",
            MarketplaceQuery.params(page, search, category)
        )
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value) ?: return CommunityResult.ServerError(0, "bad json")
                val rows = arr.filterIsInstance<Map<String, Any?>>()
                CommunityResult.Ok(
                    MarketplaceQuery.rank(page.section, CommunityModels.themes(rows, CommunityConfig.baseUrl))
                )
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
        docJson: String,
        contentHash: String? = null
    ): CommunityResult<String> {
        val tagsJson = tags.joinToString(",", "[", "]") { q(it) }
        val hashField = if (contentHash != null) """"content_hash":${q(contentHash)},""" else ""
        val body = """{"name":${q(name)},"description":${q(description)},"tags":$tagsJson,""" +
            """"category":${q(category)},$hashField"doc":$docJson}"""
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes")
        return when (val r = request("POST", url, token, body)) {
            is CommunityResult.Ok -> CommunityResult.Ok(r.value)
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /**
     * Upload bytes to a Storage bucket.
     *
     * The path's first segment must be the caller's user id: the bucket policy
     * (`storage.foldername(name))[1] = auth.uid()`) rejects anything else, so a forged path fails
     * server-side rather than being trusted from the client.
     */
    fun uploadObject(
        token: String,
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String
    ): CommunityResult<String> {
        if (!CommunityConfig.isConfigured) return CommunityResult.NotConfigured
        val url = "${CommunityConfig.baseUrl}/storage/v1/object/$bucket/$path"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", CommunityConfig.anonKey)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("x-upsert", "false")
                doOutput = true
            }
            conn.outputStream.use { it.write(bytes) }
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

    /** Update an owned theme's metadata. RLS rejects anyone else's. */
    fun updateTheme(
        token: String, themeId: String, name: String, description: String,
        tags: List<String>, category: String
    ): CommunityResult<String> {
        val tagsJson = tags.joinToString(",", "[", "]") { q(it) }
        // version is bumped by a trigger server-side; PostgREST bodies cannot express arithmetic
        val body = """{"name":${q(name)},"description":${q(description)},"tags":$tagsJson,""" +
            """"category":${q(category)}}"""
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", mapOf("id" to "eq.$themeId"))
        return request("PATCH", url, token, body)
    }

    /** Show or hide an owned theme. */
    fun setVisibility(token: String, themeId: String, isPublic: Boolean): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", mapOf("id" to "eq.$themeId"))
        return request("PATCH", url, token, """{"is_public":$isPublic}""")
    }

    /** Point an owned theme at a new wallpaper object. */
    fun setWallpaperPath(token: String, themeId: String, path: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", mapOf("id" to "eq.$themeId"))
        return request("PATCH", url, token, """{"wallpaper_path":${q(path)}}""")
    }

    /** Delete an owned theme. RLS rejects anyone else's. */
    fun deleteTheme(token: String, themeId: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", mapOf("id" to "eq.$themeId"))
        return request("DELETE", url, token, null)
    }

    /** Remove a Storage object, used when a delete or a failed publish leaves a file behind. */
    fun deleteObject(token: String, bucket: String, path: String): CommunityResult<String> {
        if (!DownloadGuard.isSafeObjectPath(path)) {
            return CommunityResult.ServerError(0, "unsafe object path")
        }
        val url = "${CommunityConfig.baseUrl}/storage/v1/object/$bucket/$path"
        return request("DELETE", url, token, null)
    }

    /** Fetch one theme by id, including its document. */
    fun fetchTheme(token: String?, themeId: String): CommunityResult<ThemeSummary> {
        val params = linkedMapOf(
            "select" to "id,name,description,tags,category,preview_path,downloads,version,profiles(handle)",
            "id" to "eq.$themeId",
            "limit" to "1"
        )
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", params)
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value) ?: return CommunityResult.ServerError(0, "bad json")
                val first = arr.filterIsInstance<Map<String, Any?>>().firstOrNull()
                    ?: return CommunityResult.ServerError(404, "theme not found")
                CommunityModels.theme(first, CommunityConfig.baseUrl)
                    ?.let { CommunityResult.Ok(it) }
                    ?: CommunityResult.ServerError(0, "unreadable theme")
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** Is there already a live theme by this author with the same design hash? */
    fun findDuplicate(token: String, contentHash: String): CommunityResult<Boolean> {
        val params = linkedMapOf(
            "select" to "id",
            "content_hash" to "eq.$contentHash",
            "is_removed" to "eq.false",
            "limit" to "1"
        )
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", params)
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value)
                CommunityResult.Ok(arr != null && arr.isNotEmpty())
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** Fetch a theme's document JSON for download. */
    fun fetchThemeDoc(token: String?, themeId: String): CommunityResult<String> {
        val params = linkedMapOf("select" to "doc,wallpaper_path", "id" to "eq.$themeId", "limit" to "1")
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", params)
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value)
                val first = arr?.filterIsInstance<Map<String, Any?>>()?.firstOrNull()
                val doc = first?.get("doc")
                val json = when (doc) {
                    is String -> doc
                    is Map<*, *> -> ThemeJson.encode(doc.entries.associate { it.key.toString() to it.value })
                    else -> null
                }
                if (json == null) CommunityResult.ServerError(0, "theme has no document") else CommunityResult.Ok(json)
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** Download raw bytes from a Storage bucket. */
    fun downloadObject(bucket: String, path: String): CommunityResult<ByteArray> {
        if (!CommunityConfig.isConfigured) return CommunityResult.NotConfigured
        if (!DownloadGuard.isSafeObjectPath(path)) return CommunityResult.ServerError(0, "unsafe object path")
        val url = "${CommunityConfig.baseUrl}/storage/v1/object/public/$bucket/$path"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", CommunityConfig.anonKey)
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return if (code == 401 || code == 403) CommunityResult.Unauthorized("download denied")
                else CommunityResult.ServerError(code, "download failed")
            }
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > DownloadGuard.MAX_WALLPAPER_BYTES) {
                        return CommunityResult.ServerError(0, "file too large")
                    }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            CommunityResult.Ok(bytes)
        } catch (e: Exception) {
            CommunityResult.NetworkError(e.message ?: "network error")
        }
    }

    /** Favourite a theme. The row is keyed by the caller, so a duplicate is a 409, not a forgery. */
    fun addFavourite(token: String, themeId: String): CommunityResult<String> =
        request("POST", CommunityUrls.table(CommunityConfig.baseUrl, "favourites"), token,
            """{"theme_id":${q(themeId)}}""")

    /** Remove a favourite. RLS restricts the delete to the caller's own row. */
    fun removeFavourite(token: String, themeId: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "favourites",
            linkedMapOf("theme_id" to "eq.$themeId"))
        return request("DELETE", url, token, null)
    }

    fun isFavourite(token: String, themeId: String): CommunityResult<Boolean> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "favourites",
            linkedMapOf("select" to "theme_id", "theme_id" to "eq.$themeId", "limit" to "1"))
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value)
                CommunityResult.Ok(arr != null && arr.isNotEmpty())
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /**
     * Increment a theme's download counter.
     *
     * Calls a database function, so the value is decided server-side; a client cannot set an
     * arbitrary count.
     */
    fun registerDownload(token: String?, themeId: String): CommunityResult<String> =
        request("POST", "${CommunityConfig.baseUrl}/rest/v1/rpc/increment_downloads", token,
            """{"p_theme_id":${q(themeId)}}""")

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
