package com.joyboard.notchisland.island

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/** How large the island is drawn right now. */
/**
 * The sizes the island steps through. [MEDIUM] is the halfway card: the header and nothing else,
 * which is what a second tap opens before the full panel.
 */
enum class IslandMode { HIDDEN, PILL, COMPACT, MEDIUM, EXPANDED }

/**
 * Every source of island content is a "live activity". Higher [priority] wins when several are
 * alive at once, exactly like the way iOS picks which activity owns the island.
 */
enum class ActivityKind(val priority: Int) {
    IDLE(0),
    /** A note the person pinned, kept until they tick it off. Below music: it can wait. */
    NOTE(18),
    MEDIA(20),
    /** The next calendar event, counting down. Above music, below anything more urgent. */
    CALENDAR(22),
    /** Someone else's long-running notification: navigation, a download, a delivery. */
    /** Download and upload speed while something is transferring fast. */
    NETWORK(24),
    ONGOING(25),
    /** The torch, while it is on. */
    FLASHLIGHT(27),
    TIMER(30),
    STOPWATCH(32),
    /** Turn-by-turn directions from a maps app, for as long as it is navigating. */
    NAVIGATION(33),
    /** Rain due within the hour: a short heads-up. */
    WEATHER(35),
    CHARGING(40),
    BATTERY_LOW(45),
    /** The battery running hot. */
    BATTERY_HOT(46),
    /** Headphones or earbuds connecting or going. */
    HEADPHONES(48),
    RINGER(50),
    /** Do Not Disturb switching on or off. */
    FOCUS(52),
    VOLUME(55),
    /** Going offline, coming back, airplane mode. */
    CONNECTIVITY(57),
    PRIVACY(60),
    UNLOCK(65),
    /** A message another app asked the island to show, through the automation intents. */
    EXTERNAL(68),
    NOTIFICATION(70),
    /** A timer that has run out and is ringing until it is stopped. */
    ALARM(85),
    /** A ringing or connected call always wins, and stays until it ends. */
    CALL(90),
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
    data class Charging(
        val level: Int,
        val plugged: Boolean,
        val fast: Boolean,
        /** The system's estimate of the time to a full charge, when it has one. */
        val fullInMs: Long? = null,
        /** Drawn as the low-battery warning rather than a plug or unplug. */
        val warning: Boolean = false,
        /** The battery's temperature, shown beside the rest when battery heat is switched on. */
        val temperatureC: Float? = null,
    ) : ExpandedBody
    data class Timer(val remainingMs: Long, val totalMs: Long, val running: Boolean) : ExpandedBody
    /** A title and a line of detail, with an optional button that acts on the island's content. */
    data class Message(val title: String, val subtitle: String?, val actionLabel: String? = null) : ExpandedBody
    /** A countdown that has run out: stop it, or run it again. */
    data class TimerDone(val totalMs: Long) : ExpandedBody
    /** Directions: the turn, how far to it, and when you will arrive. */
    data class Navigation(val item: NotificationItem, val info: NavigationInfo) : ExpandedBody
}

/** The complete description of what the island should be drawing. */
data class Presentation(
    val kind: ActivityKind,
    val leadingIcon: Drawable? = null,
    val leadingTint: Int? = null,
    val leadingBitmap: Bitmap? = null,
    val trailing: Trailing = Trailing.None,
    val accent: Int = 0xFF3B82F6.toInt(),
    /**
     * A colour that carries meaning — see [IslandColors] — and so is used whatever accent the
     * person has chosen. Null for activities that simply wear the accent.
     */
    val fixedAccent: Int? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val body: ExpandedBody = ExpandedBody.QuickPanel,
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
    /** The next title in the app's queue, when it publishes one. */
    val upNext: String? = null,
    /** Synced lyrics, when they are switched on and were found. */
    val lyrics: List<LyricLine>? = null,
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
    /** From the notification's Ranking: how important the phone considers it. */
    val importance: Int = NotificationFilter.AUDIBLE_IMPORTANCE,
    /** False when Do Not Disturb stopped this notification from interrupting. */
    val passesDoNotDisturb: Boolean = true,
    /** The phone has already tucked this one away as low priority. */
    val ambient: Boolean = false,
    /** The small line apps put beside their name — for a maps app, the arrival time. */
    val subText: String = "",
    /** A music or video player's own notification; the media session speaks for it instead. */
    val media: Boolean = false,
    /** It shows a running clock — a recording, a workout, a call in progress. */
    val chronometer: Boolean = false,
    /** Android 16's promoted "live update": the app itself says this is live. */
    val promoted: Boolean = false,
    /** The app asked to alert once: later updates of it are quiet. */
    val alertOnce: Boolean = false,
) {
    val isCall: Boolean get() = category == "call"
    /** Turn-by-turn directions from a maps app. */
    val isNavigation: Boolean get() = NavigationText.isNavigation(category, packageName, ongoing)

    val hasProgress: Boolean get() = progress >= 0 && progressMax > 0

    /** An ongoing notification with a state worth watching, rather than an app saying it runs. */
    val isLiveOngoing: Boolean
        get() = NotificationFilter.ongoingIsLive(
            category, hasProgress, progressIndeterminate, chronometer, promoted, isNavigation,
        )
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
) {
    /** Stays until whatever it reflects ends, rather than passing through on a timer. */
    val longLived: Boolean get() = expiresAt == Long.MAX_VALUE
}
