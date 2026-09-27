package com.joyboard.notchisland.island

/** What kind of island activity a notification would become. */
enum class NotificationKind { PREVIEW, CALL, ONGOING }

/**
 * Whether a notification gets to take over the island. The phone has already ranked it — how
 * important it is, and whether Do Not Disturb let it through — and the island should respect
 * that rather than treat every notification as urgent.
 *
 * Kept free of Android types so the rules can be tested; the listener supplies the values from
 * the notification's Ranking.
 */
object NotificationFilter {

    /** NotificationManager.IMPORTANCE_DEFAULT: the lowest level that makes a sound. */
    const val AUDIBLE_IMPORTANCE = 3

    /**
     * Whether a notification that stays in the shade is a live activity — something with a state
     * worth watching — or just an app saying it is running. Only the first belongs on the island:
     * otherwise every VPN, fitness tracker and background service would hold it forever.
     */
    fun ongoingIsLive(
        category: String?,
        hasProgress: Boolean,
        indeterminate: Boolean,
        chronometer: Boolean,
        promoted: Boolean,
        navigation: Boolean,
    ): Boolean = navigation || promoted || hasProgress || indeterminate || chronometer ||
        category in LIVE_CATEGORIES

    /** Categories that are live by nature: a download, a stopwatch, a workout, a shared location. */
    private val LIVE_CATEGORIES = setOf("progress", "stopwatch", "workout", "location_sharing", "navigation")

    fun shouldShow(
        kind: NotificationKind,
        packageBlocked: Boolean,
        importance: Int,
        passesDoNotDisturb: Boolean,
        ambient: Boolean,
        showSilent: Boolean,
        respectDoNotDisturb: Boolean,
    ): Boolean {
        if (packageBlocked) return false
        return when (kind) {
            // Navigation, downloads and recordings are routinely posted at low importance;
            // they are status, not interruptions, so neither silence nor DND applies to them.
            NotificationKind.ONGOING -> true
            // If Do Not Disturb stopped the phone ringing, the island should not ring either.
            NotificationKind.CALL -> !respectDoNotDisturb || passesDoNotDisturb
            NotificationKind.PREVIEW -> {
                if (respectDoNotDisturb && !passesDoNotDisturb) return false
                val silent = ambient || importance in 0 until AUDIBLE_IMPORTANCE
                !silent || showSilent
            }
        }
    }
}
