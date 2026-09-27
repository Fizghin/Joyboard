package com.joyboard.notchisland.island

/**
 * Which notifications the island has already announced, so an app updating one in place — a
 * chat re-posting its thread, a player refreshing its timestamp — does not make the island pop,
 * buzz and glow all over again. An update says something new only if the app did not ask to
 * alert once and the words actually changed.
 */
class SeenNotifications(private val limit: Int = 64) {

    private val seen = LinkedHashMap<String, Int>()

    /** True when [key] with this content is worth announcing; remembers it either way. */
    fun shouldAnnounce(key: String, title: String, text: String, alertOnce: Boolean): Boolean {
        val content = (title + '\u0000' + text).hashCode()
        val before = seen.remove(key)
        seen[key] = content
        while (seen.size > limit) seen.remove(seen.keys.first())
        return before == null || (!alertOnce && before != content)
    }

    /** The notification went away; if it comes back it is new. */
    fun forget(key: String) {
        seen.remove(key)
    }
}
