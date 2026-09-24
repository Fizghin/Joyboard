package com.joyboard.notchisland.util

import java.util.Locale

/** Clock-style durations, shared by the media scrubber, timers and the stopwatch. */
fun formatDuration(ms: Long, forceMinutes: Boolean = false): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        forceMinutes || minutes > 0 -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
        else -> String.format(Locale.US, "0:%02d", seconds)
    }
}

/** Hundredths-of-a-second precision, for the stopwatch. */
fun formatStopwatch(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    val hundredths = (safe % 1000) / 10
    val totalSeconds = safe / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
}

/** "3 min ago" style stamps for the notification history. */
fun formatRelative(nowMs: Long, thenMs: Long): String {
    val delta = (nowMs - thenMs).coerceAtLeast(0)
    val minutes = delta / 60_000
    val hours = delta / 3_600_000
    return when {
        delta < 60_000 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${hours / 24}d ago"
    }
}
