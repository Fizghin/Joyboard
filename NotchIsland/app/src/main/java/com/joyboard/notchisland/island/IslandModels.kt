package com.joyboard.notchisland.island

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/** How large the island is drawn right now. */
enum class IslandMode { HIDDEN, PILL, COMPACT, EXPANDED }

/**
 * Every source of island content is a "live activity". Higher [priority] wins when several are
 * alive at once, exactly like the way iOS picks which activity owns the island.
 */
enum class ActivityKind(val priority: Int, val persistent: Boolean) {
    IDLE(0, true),
    MEDIA(20, true),
    TIMER(30, true),
    CHARGING(40, false),
    BATTERY_LOW(45, false),
    RINGER(50, false),
    VOLUME(55, false),
    PRIVACY(60, false),
    UNLOCK(65, false),
    NOTIFICATION(70, false),
}

/** What the right-hand side of the compact island shows. */
sealed interface Trailing {
    data object None : Trailing
    data class Text(val text: String, val color: Int? = null) : Trailing
    data class Icon(val drawable: Drawable?, val tint: Int? = null) : Trailing
    data class Ring(val progress: Float, val color: Int, val label: String? = null) : Trailing
    data class Waveform(val playing: Boolean, val color: Int) : Trailing
}

/** What the expanded island shows below the header. */
sealed interface ExpandedBody {
    data object QuickPanel : ExpandedBody
    data class Media(val media: MediaSnapshot) : ExpandedBody
    data class Notification(val item: NotificationItem) : ExpandedBody
    data class Charging(val level: Int, val plugged: Boolean, val fast: Boolean) : ExpandedBody
    data class Timer(val remainingMs: Long, val totalMs: Long, val running: Boolean) : ExpandedBody
    data class Message(val title: String, val subtitle: String?) : ExpandedBody
}

/** The complete description of what the island should be drawing. */
data class Presentation(
    val kind: ActivityKind,
    val leadingIcon: Drawable? = null,
    val leadingTint: Int? = null,
    val leadingBitmap: Bitmap? = null,
    val trailing: Trailing = Trailing.None,
    val accent: Int = 0xFF3B82F6.toInt(),
    val title: String? = null,
    val subtitle: String? = null,
    val body: ExpandedBody = ExpandedBody.QuickPanel,
    val expandable: Boolean = true,
    val tapIntent: PendingIntent? = null,
)

data class MediaSnapshot(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artwork: Bitmap?,
    val appIcon: Drawable?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val accent: Int,
    val canSkipNext: Boolean = true,
    val canSkipPrev: Boolean = true,
    val canSeek: Boolean = true,
)

data class NotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val smallIcon: Drawable?,
    val largeIcon: Bitmap?,
    val appIcon: Drawable?,
    val accent: Int,
    val whenMs: Long,
    val contentIntent: PendingIntent?,
    val actions: List<NotificationAction>,
    val isConversation: Boolean,
)

data class NotificationAction(val title: String, val intent: PendingIntent?)

/** A live activity currently competing for the island. */
data class LiveActivity(
    val kind: ActivityKind,
    val presentation: Presentation,
    val expiresAt: Long,            // SystemClock.elapsedRealtime(); Long.MAX_VALUE = sticky
    val autoExpand: Boolean = false,
)
