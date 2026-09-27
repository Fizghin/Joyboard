package com.joyboard.notchisland.island

/** One occurrence of a calendar event, in wall-clock milliseconds. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val beginMs: Long,
    val endMs: Long,
    val location: String? = null,
    val allDay: Boolean = false,
)

/** Which event, if any, has earned the island right now. */
object CalendarPicker {

    /** How long an event stays up after it has started, as a "happening now" reminder. */
    const val GRACE_MS = 5 * 60_000L

    /**
     * The soonest timed event whose countdown window is open: from [leadMs] before it starts
     * until a few minutes in, or until it ends if that is sooner. All-day events never count.
     */
    fun pick(events: List<CalendarEvent>, nowMs: Long, leadMs: Long): CalendarEvent? =
        events
            .filter { !it.allDay && it.endMs > it.beginMs }
            .filter { nowMs >= it.beginMs - leadMs && nowMs < minOf(it.beginMs + GRACE_MS, it.endMs) }
            .minWithOrNull(compareBy<CalendarEvent> { it.beginMs }.thenBy { it.id })

    /** Whole minutes until the start, rounded up so "in 1 min" lasts the full last minute. */
    fun minutesUntil(event: CalendarEvent, nowMs: Long): Int {
        val ms = event.beginMs - nowMs
        if (ms <= 0) return 0
        return ((ms + 59_999) / 60_000).toInt()
    }

    /** When the countdown next changes, so the island can refresh on the minute, not poll. */
    fun millisUntilNextChange(event: CalendarEvent, nowMs: Long): Long {
        val ms = event.beginMs - nowMs
        return if (ms > 0) ((ms - 1) % 60_000) + 1 else (event.beginMs + GRACE_MS - nowMs).coerceAtLeast(1)
    }
}
