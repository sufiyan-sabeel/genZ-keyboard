package com.genz.keyboard

enum class GifSearchMode {
    /** Only the built-in catalogue; nothing leaves the device. */
    LOCAL_ONLY,

    /** A configured GIF service may be queried over HTTPS. */
    NETWORK
}

/**
 * Decides whether GIF search may use the network.
 *
 * The query a person types into the GIF search bar is sent to a third-party GIF service. In a
 * private session that must not happen, so the network path is closed and the built-in catalogue —
 * which is filtered locally by title — remains available. Nothing else is transmitted instead.
 *
 * Pure: takes the two signals the UI actually has, so the rule can be tested without a device.
 */
object GifSearchPolicy {

    /**
     * @param manualIncognito the user's incognito switch
     * @param privateField the current editor demands privacy (password / no learning)
     * @param hasKey a GIF service is configured at all
     */
    fun mode(manualIncognito: Boolean, privateField: Boolean, hasKey: Boolean): GifSearchMode =
        if (manualIncognito || privateField || !hasKey) GifSearchMode.LOCAL_ONLY else GifSearchMode.NETWORK

    fun allowsNetwork(mode: GifSearchMode): Boolean = mode == GifSearchMode.NETWORK

    /**
     * True when the restriction is caused by privacy rather than by missing keys — the UI explains
     * it differently in each case, and only the privacy case is worth mentioning.
     */
    fun blockedByPrivacy(manualIncognito: Boolean, privateField: Boolean): Boolean =
        manualIncognito || privateField
}
