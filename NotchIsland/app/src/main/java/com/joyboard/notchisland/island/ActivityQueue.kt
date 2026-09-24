package com.joyboard.notchisland.island

import android.os.SystemClock

/**
 * One tap, one step. Returns the next size up, or null to mean "back to resting" — which is what
 * a tap on the fully open island does.
 */
object ExpansionStepper {
    fun next(current: IslandMode): IslandMode? = when (current) {
        IslandMode.HIDDEN, IslandMode.PILL -> IslandMode.COMPACT
        IslandMode.COMPACT -> IslandMode.MEDIUM
        IslandMode.MEDIUM -> IslandMode.EXPANDED
        IslandMode.EXPANDED -> null
    }
}

/**
 * The island can only show one thing at a time, so live activities compete here. The highest
 * [ActivityKind.priority] that has not expired wins; everything else waits its turn and
 * reappears when the winner goes away.
 *
 * Kept free of Android dependencies (the clock is injected) so the ordering rules can be tested.
 */
class ActivityQueue(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {

    private val activities = LinkedHashMap<ActivityKind, LiveActivity>()

    val size: Int get() = activities.size

    /** Adds or replaces the activity for its kind. */
    fun put(activity: LiveActivity) {
        activities[activity.kind] = activity
    }

    /** Returns true when something was actually removed. */
    fun remove(kind: ActivityKind): Boolean = activities.remove(kind) != null

    fun contains(kind: ActivityKind): Boolean = activities.containsKey(kind)

    fun peek(kind: ActivityKind): LiveActivity? = activities[kind]

    fun clear() = activities.clear()

    /** Drops anything whose time is up and returns the activity that should own the island. */
    fun top(): LiveActivity? {
        prune()
        return activities.values.maxByOrNull { it.kind.priority }
    }

    /** Milliseconds until the next activity expires, or null when nothing is on a timer. */
    fun millisUntilNextExpiry(): Long? {
        prune()
        val soonest = activities.values
            .map { it.expiresAt }
            .filter { it != Long.MAX_VALUE }
            .minOrNull() ?: return null
        return (soonest - clock()).coerceAtLeast(0L)
    }

    private fun prune() {
        val now = clock()
        val expired = activities.filterValues { it.expiresAt <= now }.keys.toList()
        expired.forEach { activities.remove(it) }
    }
}
