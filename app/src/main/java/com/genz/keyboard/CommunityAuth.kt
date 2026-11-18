package com.genz.keyboard

import android.content.Context

/**
 * Token lifecycle.
 *
 * Refreshes shortly before expiry rather than after a 401, so a request is not wasted. A revoked or
 * invalid refresh token clears the session instead of looping; a transient network failure keeps the
 * current token, because losing a session to a flaky connection would be worse than one retry.
 */
object CommunityAuth {

    /** @return a usable access token, or null when the user must sign in again. */
    fun currentToken(context: Context): String? {
        val stored = CommunitySession.load(context) ?: return null
        val tokens = stored.first
        if (!AuthResponse.needsRefresh(tokens)) return tokens.accessToken

        return when (val result = CommunityApi.refresh(tokens.refreshToken)) {
            is CommunityResult.Ok -> {
                CommunitySession.save(context, result.value, CommunitySession.handle(context))
                result.value.accessToken
            }
            is CommunityResult.Unauthorized -> {
                // revoked or invalid: the session is over
                CommunitySession.clear(context)
                null
            }
            is CommunityResult.NotConfigured -> null
            is CommunityResult.NetworkError -> tokens.accessToken
            is CommunityResult.ServerError -> tokens.accessToken
        }
    }

    /** Sign out: wipe the encrypted session and the Keystore-backed blob with it. */
    fun signOut(context: Context) {
        CommunitySession.clear(context)
    }
}
