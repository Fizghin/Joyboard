package com.joyboard.notchisland.island

/** Reads "what plays next" out of a media session's queue. */
object MediaQueue {

    /**
     * The title after the one playing, or null when the app does not publish a queue, the
     * playing item is not in it, or it is the last one.
     *
     * @param items queue ids and titles, in play order
     * @param activeId the id of the item playing now, as the session reports it
     */
    fun nextTitle(items: List<Pair<Long, String?>>?, activeId: Long): String? {
        if (items.isNullOrEmpty()) return null
        val index = items.indexOfFirst { it.first == activeId }
        if (index < 0) return null
        return items.drop(index + 1).firstNotNullOfOrNull { it.second?.takeIf(String::isNotBlank) }
    }
}
