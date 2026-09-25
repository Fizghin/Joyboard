package com.joyboard.notchisland.island

/**
 * Decides what size the island should settle at when nobody is touching it.
 *
 * The rule that matters: a live activity earns the compact readout for a while after something
 * actually happens, then the island goes back to its resting pill. Without the second half, one
 * long-running notification — a download, a navigation route, anything ongoing — would hold the
 * island open forever.
 */
object RestPolicy {

    /**
     * @param userStage the size the user asked for, or null if they are not holding it open
     * @param hasActivity whether any live activity currently owns the island
     * @param msSinceActivityChange how long since an activity last genuinely changed, as opposed
     *   to refreshing its own contents
     * @param stayCompact keep the compact readout up for as long as an activity is alive, the way
     *   iOS does for now-playing
     */
    fun target(
        userStage: IslandMode?,
        hasActivity: Boolean,
        msSinceActivityChange: Long,
        stayCompact: Boolean,
        compactRestMs: Long,
        alwaysShowPill: Boolean,
    ): IslandMode = when {
        userStage != null -> userStage
        hasActivity && (stayCompact || msSinceActivityChange < compactRestMs) -> IslandMode.COMPACT
        alwaysShowPill -> IslandMode.PILL
        else -> IslandMode.HIDDEN
    }

    /**
     * When to look again, so the island settles on its own rather than waiting for the next
     * unrelated event. Null when nothing is pending.
     */
    fun millisUntilSettle(
        userStage: IslandMode?,
        hasActivity: Boolean,
        msSinceActivityChange: Long,
        stayCompact: Boolean,
        compactRestMs: Long,
    ): Long? {
        if (userStage != null || !hasActivity || stayCompact) return null
        val remaining = compactRestMs - msSinceActivityChange
        return if (remaining > 0) remaining else null
    }
}
