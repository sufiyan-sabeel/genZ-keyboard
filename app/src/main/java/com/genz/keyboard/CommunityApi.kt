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
            "select" to "id,author_id,name,description,tags,category,preview_path,downloads,version,profiles(handle)",
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

    /** Create an unpublished draft row pointing at private storage. */
    fun createDraftTheme(
        token: String, name: String, description: String, tags: List<String>,
        category: String, docJson: String, draftPath: String, contentHash: String?
    ): CommunityResult<String> {
        val tagsJson = tags.joinToString(",", "[", "]") { q(it) }
        val hashField = if (contentHash != null) """"content_hash":${q(contentHash)},""" else ""
        val body = """{"name":${q(name)},"description":${q(description)},"tags":$tagsJson,""" +
            """"category":${q(category)},$hashField"doc":$docJson,""" +
            """"draft_path":${q(draftPath)},"is_public":false,"is_removed":false}"""
        return request("POST", CommunityUrls.table(CommunityConfig.baseUrl, "themes"), token, body)
    }

    /**
     * Point a theme at its public artwork and publish it.
     *
     * `wallpaper_path` is set in the same statement as `is_public`, so the database trigger that
     * refuses a published theme without public artwork cannot fire on a half-finished publish.
     */
    fun publishThemeRow(token: String, themeId: String, publicPath: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes", mapOf("id" to "eq.$themeId"))
        return request("PATCH", url, token,
            """{"wallpaper_path":${q(publicPath)},"is_public":true}""")
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
            "select" to "id,author_id,name,description,tags,category,preview_path,downloads,version,profiles(handle)",
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

    /**
     * Issue a short-lived signed URL for a private object.
     *
     * The Storage API checks that the caller may read the object before signing, so this is the only
     * way a draft's artwork reaches a screen — the bucket itself is private.
     */
    fun createSignedUrl(token: String, bucket: String, path: String, expiresInSeconds: Int = 3600): CommunityResult<String> {
        if (!CommunityConfig.isConfigured) return CommunityResult.NotConfigured
        if (!DownloadGuard.isSafeObjectPath(path)) return CommunityResult.ServerError(0, "unsafe object path")
        val url = "${CommunityConfig.baseUrl}/storage/v1/object/sign/$bucket/$path"
        return when (val r = request("POST", url, token, """{"expiresIn":$expiresInSeconds}""")) {
            is CommunityResult.Ok -> {
                val signed = ThemeJson.decode(r.value)?.get("signedURL") as? String
                    ?: ThemeJson.decode(r.value)?.get("signedUrl") as? String
                if (signed == null) CommunityResult.ServerError(0, "no signed url in response")
                else CommunityResult.Ok(
                    if (signed.startsWith("http")) signed else "${CommunityConfig.baseUrl}/storage/v1$signed"
                )
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /**
     * Server-side copy between buckets.
     *
     * Used to move a draft's artwork into public storage. Copy-then-delete rather than move, so a
     * failure after the copy still leaves the original draft intact and the publish can be retried.
     */
    fun copyObject(
        token: String,
        fromBucket: String,
        fromPath: String,
        toBucket: String,
        toPath: String
    ): CommunityResult<String> {
        if (!CommunityConfig.isConfigured) return CommunityResult.NotConfigured
        if (!DownloadGuard.isSafeObjectPath(fromPath) || !DownloadGuard.isSafeObjectPath(toPath)) {
            return CommunityResult.ServerError(0, "unsafe object path")
        }
        val body = """{"bucketId":${q(fromBucket)},"sourceKey":${q(fromPath)},""" +
            """"destinationBucket":${q(toBucket)},"destinationKey":${q(toPath)}}"""
        return request("POST", "${CommunityConfig.baseUrl}/storage/v1/object/copy", token, body)
    }

    /**
     * Whether a public object is actually readable.
     *
     * Used to confirm a Storage 409 really means "the object is already there" before a publish is
     * treated as successful — a 409 for any other reason must not publish a theme with no artwork.
     */
    fun publicObjectExists(bucket: String, path: String): Boolean {
        if (!CommunityConfig.isConfigured) return false
        if (!DownloadGuard.isSafeObjectPath(path)) return false
        val url = "${CommunityConfig.baseUrl}/storage/v1/object/public/$bucket/$path"
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("apikey", CommunityConfig.anonKey)
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete the caller's own account.
     *
     * Calls a database function that derives the target from `auth.uid()` and takes no argument, so
     * there is no id to forge. Removing an auth user needs privileges the anon key must never have,
     * which is why this is a SECURITY DEFINER function rather than a table delete.
     */
    fun deleteAccount(token: String): CommunityResult<String> =
        request("POST", "${CommunityConfig.baseUrl}/rest/v1/rpc/delete_own_account", token, "{}")

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

    /** Reviews for a theme, newest first. RLS hides removed reviews. */
    fun listReviews(token: String?, themeId: String, limit: Int = 50): CommunityResult<List<ThemeReview>> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "reviews", linkedMapOf(
            "select" to "id,rating,body,created_at,author_id,profiles(handle)",
            "theme_id" to "eq.$themeId",
            "is_removed" to "eq.false",
            "order" to "created_at.desc",
            "limit" to limit.toString()
        ))
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value) ?: return CommunityResult.ServerError(0, "bad json")
                CommunityResult.Ok(arr.filterIsInstance<Map<String, Any?>>().mapNotNull { CommunityModels.review(it) })
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /**
     * The authoritative rating for a theme.
     *
     * Read from the `theme_ratings` view, which aggregates every review server-side. Computing an
     * average from the reviews currently loaded in the app would be wrong the moment a theme has
     * more reviews than one page.
     */
    fun fetchRating(token: String?, themeId: String): CommunityResult<Pair<Double, Int>> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "theme_ratings", linkedMapOf(
            "select" to "average_rating,review_count",
            "theme_id" to "eq.$themeId",
            "limit" to "1"
        ))
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value)
                val row = arr?.filterIsInstance<Map<String, Any?>>()?.firstOrNull()
                if (row == null) CommunityResult.Ok(0.0 to 0)
                else CommunityResult.Ok(
                    ((row["average_rating"] as? Number)?.toDouble() ?: 0.0) to
                        ((row["review_count"] as? Number)?.toInt() ?: 0)
                )
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** The caller's own review for a theme, if one exists. */
    fun myReview(token: String, themeId: String, userId: String): CommunityResult<ThemeReview?> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "reviews", linkedMapOf(
            "select" to "id,rating,body,created_at,author_id,profiles(handle)",
            "theme_id" to "eq.$themeId",
            "author_id" to "eq.$userId",
            "limit" to "1"
        ))
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value)
                CommunityResult.Ok(arr?.filterIsInstance<Map<String, Any?>>()?.firstOrNull()?.let { CommunityModels.review(it) })
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** Submit a review. The unique (theme_id, author_id) index makes a retry a 409, not a duplicate. */
    fun submitReview(token: String, themeId: String, rating: Int, body: String): CommunityResult<String> {
        val json = """{"theme_id":${q(themeId)},"rating":$rating,"body":${q(body)}}"""
        return request("POST", CommunityUrls.table(CommunityConfig.baseUrl, "reviews"), token, json)
    }

    /** Edit the caller's own review. RLS rejects anyone else's. */
    fun updateReview(token: String, reviewId: String, rating: Int, body: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "reviews", linkedMapOf("id" to "eq.$reviewId"))
        return request("PATCH", url, token, """{"rating":$rating,"body":${q(body)}}""")
    }

    /** Delete the caller's own review. */
    fun deleteReview(token: String, reviewId: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "reviews", linkedMapOf("id" to "eq.$reviewId"))
        return request("DELETE", url, token, null)
    }

    /** A creator's public profile. Returns ServerError(404) when the profile is gone. */
    fun fetchProfile(userId: String): CommunityResult<CreatorProfile> {
        if (!CreatorProfiles.isValidUserId(userId)) {
            return CommunityResult.ServerError(400, "invalid user id")
        }
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "public_profiles", linkedMapOf(
            "select" to CreatorProfiles.SELECT,
            "id" to "eq.${userId.trim()}",
            "limit" to "1"
        ))
        return when (val r = request("GET", url, null, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value)
                val row = arr?.filterIsInstance<Map<String, Any?>>()?.firstOrNull()
                val profile = row?.let { CreatorProfiles.parse(it) }
                if (profile == null) CommunityResult.ServerError(404, "profile not found")
                else CommunityResult.Ok(profile)
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /**
     * A creator's public themes, one page at a time.
     *
     * Reads `themes_with_ratings`, which joins the rating aggregate, so a page of twenty themes is
     * one request rather than twenty-one.
     */
    fun fetchCreatorThemes(
        userId: String,
        offset: Int = 0,
        limit: Int = MarketplaceQuery.PAGE_SIZE
    ): CommunityResult<List<ThemeSummary>> {
        if (!CreatorProfiles.isValidUserId(userId)) {
            return CommunityResult.ServerError(400, "invalid user id")
        }
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "themes_with_ratings", linkedMapOf(
            "select" to "id,author_id,name,description,tags,category,preview_path,downloads,version,average_rating,review_count,profiles(handle)",
            "author_id" to "eq.${userId.trim()}",
            "order" to "created_at.desc,id.desc",
            "limit" to limit.toString(),
            "offset" to offset.toString()
        ))
        return when (val r = request("GET", url, null, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value) ?: return CommunityResult.ServerError(0, "bad json")
                CommunityResult.Ok(CommunityModels.themes(arr.filterIsInstance<Map<String, Any?>>(), CommunityConfig.baseUrl))
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /**
     * The signed-in user's saved themes, newest save first.
     *
     * Reads `favourite_themes`, which joins the rating aggregate and drops anything private,
     * removed or unpublished, so a saved theme that was taken down simply stops appearing.
     */
    fun listFavourites(
        token: String,
        userId: String,
        offset: Int = 0,
        limit: Int = MarketplaceQuery.PAGE_SIZE
    ): CommunityResult<List<ThemeSummary>> {
        if (!CreatorProfiles.isValidUserId(userId)) return CommunityResult.ServerError(400, "invalid user id")
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "favourite_themes", linkedMapOf(
            "select" to "id,author_id,name,description,tags,category,preview_path,downloads,version,average_rating,review_count,saved_at",
            "user_id" to "eq.${userId.trim()}",
            "order" to "saved_at.desc,id.desc",
            "limit" to limit.toString(),
            "offset" to offset.toString()
        ))
        return when (val r = request("GET", url, token, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value) ?: return CommunityResult.ServerError(0, "bad json")
                CommunityResult.Ok(CommunityModels.themes(arr.filterIsInstance<Map<String, Any?>>(), CommunityConfig.baseUrl))
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /** A page of the design feed, newest first. Counts come from the view's real rows. */
    fun listPosts(offset: Int = 0, limit: Int = MarketplaceQuery.PAGE_SIZE): CommunityResult<List<DesignPost>> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "post_feed", linkedMapOf(
            "select" to "id,author_id,title,body,tags,image_paths,theme_id,created_at,like_count,comment_count,author_handle,author_name",
            "order" to "created_at.desc,id.desc",
            "limit" to limit.toString(),
            "offset" to offset.toString()
        ))
        return when (val r = request("GET", url, null, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value) ?: return CommunityResult.ServerError(0, "bad json")
                CommunityResult.Ok(arr.filterIsInstance<Map<String, Any?>>().mapNotNull { CommunityModels.post(it) })
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    /**
     * Create a design post.
     *
     * `author_id` is not sent: the column defaults to `auth.uid()` on the server, so a forged
     * author cannot be submitted from the client at all.
     */
    fun createPost(
        token: String, title: String, body: String, tags: List<String>,
        imagePaths: List<String>, themeId: String?
    ): CommunityResult<String> {
        val tagsJson = tags.joinToString(",", "[", "]") { q(it) }
        val imagesJson = imagePaths.joinToString(",", "[", "]") { q(it) }
        val themeField = if (themeId != null) """"theme_id":${q(themeId)},""" else ""
        val json = """{"title":${q(title)},"body":${q(body)},"tags":$tagsJson,""" +
            """"image_paths":$imagesJson,$themeField"is_removed":false}"""
        return request("POST", CommunityUrls.table(CommunityConfig.baseUrl, "design_posts"), token, json)
    }

    /** Edit an owned post. */
    fun updatePost(token: String, postId: String, title: String, body: String, tags: List<String>): CommunityResult<String> {
        val tagsJson = tags.joinToString(",", "[", "]") { q(it) }
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "design_posts", linkedMapOf("id" to "eq.$postId"))
        return request("PATCH", url, token, """{"title":${q(title)},"body":${q(body)},"tags":$tagsJson}""")
    }

    /** Delete an owned post. Its likes and comments cascade. */
    fun deletePost(token: String, postId: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "design_posts", linkedMapOf("id" to "eq.$postId"))
        return request("DELETE", url, token, null)
    }

    fun likePost(token: String, postId: String): CommunityResult<String> =
        request("POST", CommunityUrls.table(CommunityConfig.baseUrl, "post_likes"), token,
            """{"post_id":${q(postId)}}""")

    fun unlikePost(token: String, postId: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "post_likes", linkedMapOf("post_id" to "eq.$postId"))
        return request("DELETE", url, token, null)
    }

    fun isLiked(token: String, postId: String): CommunityResult<Boolean> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "post_likes",
            linkedMapOf("select" to "post_id", "post_id" to "eq.$postId", "limit" to "1"))
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

    fun listComments(postId: String, offset: Int = 0, limit: Int = 50): CommunityResult<List<PostComment>> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "post_comments", linkedMapOf(
            "select" to "id,author_id,body,created_at,profiles(handle)",
            "post_id" to "eq.$postId",
            "is_removed" to "eq.false",
            "order" to "created_at.asc,id.asc",
            "limit" to limit.toString(),
            "offset" to offset.toString()
        ))
        return when (val r = request("GET", url, null, null)) {
            is CommunityResult.Ok -> {
                val arr = ThemeJson.decodeArray(r.value) ?: return CommunityResult.ServerError(0, "bad json")
                CommunityResult.Ok(arr.filterIsInstance<Map<String, Any?>>().mapNotNull { CommunityModels.comment(it) })
            }
            is CommunityResult.NotConfigured -> r
            is CommunityResult.Unauthorized -> r
            is CommunityResult.NetworkError -> r
            is CommunityResult.ServerError -> r
        }
    }

    fun addComment(token: String, postId: String, body: String): CommunityResult<String> =
        request("POST", CommunityUrls.table(CommunityConfig.baseUrl, "post_comments"), token,
            """{"post_id":${q(postId)},"body":${q(body)}}""")

    fun deleteComment(token: String, commentId: String): CommunityResult<String> {
        val url = CommunityUrls.table(CommunityConfig.baseUrl, "post_comments", linkedMapOf("id" to "eq.$commentId"))
        return request("DELETE", url, token, null)
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
