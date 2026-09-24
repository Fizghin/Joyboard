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
    /** Someone else's long-running notification: navigation, a download, a delivery. */
    ONGOING(25, true),
    TIMER(30, true),
    STOPWATCH(32, true),
    CHARGING(40, false),
    BATTERY_LOW(45, false),
    RINGER(50, false),
    VOLUME(55, false),
    PRIVACY(60, false),
    UNLOCK(65, false),
    NOTIFICATION(70, false),
    /** A ringing or connected call always wins, and stays until it ends. */
    CALL(90, true),
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
    data class Ongoing(val item: NotificationItem) : ExpandedBody
    data class Call(val item: NotificationItem) : ExpandedBody
    data class Stopwatch(val elapsedMs: Long, val running: Boolean, val laps: List<Long>) :
        ExpandedBody
    data class History(val items: List<NotificationItem>) : ExpandedBody
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
    /** Set when the activity is backed by a notification, so removal can retire it. */
    val notificationKey: String? = null,
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
    /** Long-running notifications become sticky activities instead of passing previews. */
    val ongoing: Boolean = false,
    val category: String? = null,
    val progress: Int = -1,
    val progressMax: Int = 0,
    val progressIndeterminate: Boolean = false,
    /** A one-time passcode found in the text, offered as a one-tap copy. */
    val otp: String? = null,
    /** The notification's own inline-reply action, when it has one. */
    val reply: ReplyAction? = null,
) {
    val isCall: Boolean get() = category == "call"

    val hasProgress: Boolean get() = progress >= 0 && progressMax > 0
}

data class NotificationAction(val title: String, val intent: PendingIntent?)

/** An inline reply the island can send without opening the app. */
data class ReplyAction(
    val title: String,
    val intent: PendingIntent?,
    val resultKey: String,
    val remoteInputs: Array<android.app.RemoteInput>,
) {
    override fun equals(other: Any?): Boolean =
        other is ReplyAction && other.resultKey == resultKey && other.title == title

    override fun hashCode(): Int = 31 * resultKey.hashCode() + title.hashCode()
}

/** A live activity currently competing for the island. */
data class LiveActivity(
    val kind: ActivityKind,
    val presentation: Presentation,
    val expiresAt: Long,            // SystemClock.elapsedRealtime(); Long.MAX_VALUE = sticky
    val autoExpand: Boolean = false,
)
