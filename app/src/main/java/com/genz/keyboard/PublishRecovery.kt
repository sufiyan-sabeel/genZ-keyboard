package com.genz.keyboard

/**
 * How to interpret the server's answer at each step of publishing.
 *
 * Publishing spans two systems that cannot share a transaction, so every step can fail in a way that
 * leaves the outcome genuinely ambiguous. These decisions are pure so they can be tested without a
 * network, because getting them wrong is how a retry silently publishes a theme twice or orphans
 * public artwork.
 */
object PublishRecovery {

    /** What a Storage copy attempt means. */
    enum class CopyOutcome {
        /** The object is now in public storage. */
        TRANSFERRED,
        /** The destination already existed — a retry of a publish that had already copied. */
        ALREADY_PRESENT,
        /** Nothing changed; safe to retry later. */
        RETRY_LATER,
        /** The request was refused; retrying will not help. */
        REJECTED
    }

    /** What a theme-row publish update means. */
    enum class RowOutcome {
        PUBLISHED,
        RETRY_LATER,
        REJECTED
    }

    fun copyOutcome(httpCode: Int): CopyOutcome = when (httpCode) {
        in 200..299 -> CopyOutcome.TRANSFERRED
        // the destination key already exists: a previous attempt copied but did not finish
        409 -> CopyOutcome.ALREADY_PRESENT
        in 500..599 -> CopyOutcome.RETRY_LATER
        else -> CopyOutcome.REJECTED
    }

    fun rowOutcome(httpCode: Int): RowOutcome = when (httpCode) {
        in 200..299 -> RowOutcome.PUBLISHED
        in 500..599 -> RowOutcome.RETRY_LATER
        else -> RowOutcome.REJECTED
    }

    /**
     * A duplicate-content rejection from the unique (author_id, content_hash) index.
     *
     * This is the correct answer to a retried draft whose first attempt actually succeeded, so it is
     * reported as "already saved", not as an error.
     */
    fun isDuplicateDraft(httpCode: Int): Boolean = httpCode == 409

    /**
     * Whether the public object should be deleted after a failed row update.
     *
     * Only when the copy is known to have created something this attempt owns. If the copy said
     * ALREADY_PRESENT, the object may belong to an earlier successful publish, so deleting it would
     * destroy live artwork.
     */
    fun shouldDeletePublicAfterFailure(copy: CopyOutcome): Boolean =
        copy == CopyOutcome.TRANSFERRED

    /**
     * Whether the private draft may be deleted after publishing.
     *
     * Only once the row is published. Deleting earlier would leave a theme pointing at artwork that
     * no longer exists anywhere.
     */
    fun shouldDeleteDraftAfter(row: RowOutcome): Boolean = row == RowOutcome.PUBLISHED

    /** A 409 only tells us the destination exists; the caller must confirm it is the right object. */
    fun requiresVerification(outcome: CopyOutcome): Boolean = outcome == CopyOutcome.ALREADY_PRESENT

    /** An object path belongs to a user only when its first segment is that user's id. */
    fun isOwnedPath(path: String, ownerId: String?): Boolean =
        ownerId != null && path.startsWith("$ownerId/")
}
