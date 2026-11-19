package com.genz.keyboard

import android.content.Context

/**
 * Account deletion.
 *
 * Deleting an account destroys every theme, review, post and favourite that belongs to it, so it is
 * irreversible and needs a deliberate confirmation. The server function takes no argument and acts
 * only on `auth.uid()`, so a client cannot ask for anyone else's account to be removed.
 */
object CommunityAccount {

    /** The exact word the user must type to confirm. */
    const val CONFIRM_WORD = "DELETE"

    sealed interface State {
        data object Idle : State
        data object Working : State
        data object Deleted : State
        data class Failed(val reason: String) : State
    }

    /**
     * Whether the typed confirmation is sufficient.
     *
     * Case-insensitive and whitespace-trimmed, because the point is deliberate intent, not
     * punctuation accuracy.
     */
    fun isConfirmed(typed: String): Boolean =
        typed.trim().equals(CONFIRM_WORD, ignoreCase = true)

    /**
     * Delete the signed-in user's account and clear the local session.
     *
     * The local session is cleared only after the server confirms, so a failure leaves the user
     * signed in and able to retry rather than stranded with a token for an account that still exists.
     */
    fun deleteAccount(context: Context): State {
        val token = CommunityAuth.currentToken(context)
            ?: return State.Failed("Sign in first")

        return when (val r = CommunityApi.deleteAccount(token)) {
            is CommunityResult.Ok -> {
                CommunitySession.clear(context)
                State.Deleted
            }
            is CommunityResult.Unauthorized -> {
                // the token is already useless; drop it so the UI shows a signed-out state
                CommunitySession.clear(context)
                State.Failed("Your session expired. Sign in again.")
            }
            is CommunityResult.NotConfigured -> State.Failed("Community is not configured")
            is CommunityResult.NetworkError -> State.Failed("Could not reach the server. Try again.")
            is CommunityResult.ServerError -> State.Failed("Deletion failed (${r.code})")
        }
    }
}
