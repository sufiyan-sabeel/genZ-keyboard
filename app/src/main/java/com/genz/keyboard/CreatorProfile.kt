package com.genz.keyboard

/**
 * A creator's public identity.
 *
 * Deliberately narrow: only what a profile page shows. Email and any authentication material live
 * outside this shape, so they cannot be rendered here even by mistake.
 */
data class CreatorProfile(
    val id: String,
    val handle: String,
    val displayName: String,
    val bio: String?,
    val avatarUrl: String?,
    val joinedAt: String?
) {
    /** The initial to show when there is no avatar. */
    val initial: String
        get() = displayName.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: handle.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: "?"
}

/** Maps a profile row to the model. Pure, so the fallbacks are tested. */
object CreatorProfiles {

    /** Columns requested from `public_profiles` — never `select=*`. */
    const val SELECT = "id,handle,display_name,bio,avatar_url,created_at"

    /**
     * @param row a row from `public_profiles`
     * @return the profile, or null when the row has no id (a deleted or malformed row)
     */
    fun parse(row: Map<String, Any?>): CreatorProfile? {
        val id = (row["id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val handle = (row["handle"] as? String)?.trim().orEmpty()
        val name = (row["display_name"] as? String)?.trim().orEmpty()
        return CreatorProfile(
            id = id,
            handle = handle,
            // a profile with neither name nor handle is still identifiable by id, so it is not dropped
            displayName = name.ifBlank { handle.ifBlank { "Unknown creator" } },
            bio = (row["bio"] as? String)?.trim()?.takeIf { it.isNotBlank() },
            avatarUrl = (row["avatar_url"] as? String)?.trim()?.takeIf { it.isNotBlank() },
            joinedAt = (row["created_at"] as? String)?.takeIf { it.isNotBlank() }
        )
    }

    /** A user id must be a UUID before it is put into a query. */
    fun isValidUserId(id: String?): Boolean {
        if (id == null) return false
        val v = id.trim()
        if (v.length != 36) return false
        return v.count { it == '-' } == 4 && v.all { it.isLetterOrDigit() || it == '-' }
    }
}
