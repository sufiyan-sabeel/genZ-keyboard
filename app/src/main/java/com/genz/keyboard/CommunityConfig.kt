package com.genz.keyboard

/**
 * Community backend configuration.
 *
 * Only the **publishable** anon key is ever shipped. That key is safe to distribute precisely
 * because the database enforces row-level security (see `supabase/schema.sql`); the service-role
 * key never leaves the server. Both values come from `local.properties` like the existing GIF
 * provider keys, so a build with no configuration still compiles.
 */
object CommunityConfig {
    val baseUrl: String get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    /** True only when a real endpoint and key are present. */
    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") && anonKey.isNotEmpty()
}

/** URL construction, kept pure so it can be tested without a network or BuildConfig. */
object CommunityUrls {

    fun table(base: String, table: String, params: Map<String, String> = emptyMap()): String {
        val root = base.trimEnd('/')
        if (params.isEmpty()) return "$root/rest/v1/$table"
        val q = params.entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        return "$root/rest/v1/$table?$q"
    }

    fun auth(base: String, path: String): String = "${base.trimEnd('/')}/auth/v1/$path"

    fun storage(base: String, bucket: String, path: String): String =
        "${base.trimEnd('/')}/storage/v1/object/public/$bucket/${encodePath(path)}"

    /** PostgREST operators such as `eq.`, `ilike.` must survive encoding, so only the value is encoded. */
    internal fun encode(v: String): String = java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    internal fun encodePath(p: String): String = p.split('/').joinToString("/") { encode(it) }
}
