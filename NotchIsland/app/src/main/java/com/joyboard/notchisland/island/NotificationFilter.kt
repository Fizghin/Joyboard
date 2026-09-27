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
