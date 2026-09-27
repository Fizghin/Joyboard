package com.joyboard.notchisland.island

import android.content.Context
import android.graphics.drawable.Drawable
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.NotificationStyle
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.util.formatStopwatch

/**
 * What each kind of live activity looks like: icon, readout, colours, titles and the panel it
 * opens to. Pure construction, split out of IslandController, which decides *when* an activity
 * appears and leaves the *what* to here.
 */
internal class Presentations(
    private val context: Context,
    /** The island's resolved accent, for activities with no colour of their own. */
    private val accent: () -> Int,
    private val settings: () -> IslandSettings,
) {

    /** Now playing. */
    fun media(snapshot: MediaSnapshot) = Presentation(
        kind = ActivityKind.MEDIA,
        leadingBitmap = snapshot.artwork,
        leadingIcon = snapshot.appIcon ?: drawable(R.drawable.ic_music),
        trailing = Trailing.Waveform(snapshot.playing, snapshot.accent),
        accent = snapshot.accent,
        title = snapshot.title,
        subtitle = "${snapshot.artist} · ${snapshot.appLabel}",
        body = ExpandedBody.Media(snapshot),
    )

    /** A ringing or connected call. */
    fun call(item: NotificationItem) = Presentation(
        kind = ActivityKind.CALL,
        leadingIcon = item.appIcon ?: item.smallIcon,
        leadingBitmap = item.largeIcon,
        trailing = Trailing.Waveform(true, 0xFF34C759.toInt()),
        accent = 0xFF34C759.toInt(),
        title = item.title.ifBlank { item.appLabel },
        subtitle = item.text.ifBlank { context.getString(R.string.call) },
        body = ExpandedBody.Call(item),
        tapIntent = item.contentIntent,
        notificationKey = item.key,
    )

    /** A long-running notification: navigation, a download, a delivery. */
    fun ongoing(item: NotificationItem) = Presentation(
        kind = ActivityKind.ONGOING,
        leadingIcon = item.appIcon ?: item.smallIcon,
        leadingBitmap = item.largeIcon,
        trailing = if (item.hasProgress) {
            Trailing.Ring(
                item.progress.toFloat() / item.progressMax.coerceAtLeast(1),
                item.accent
            )
        } else {
            Trailing.Icon(item.smallIcon, item.accent)
        },
        accent = item.accent,
        title = item.title.ifBlank { item.appLabel },
        subtitle = item.text.ifBlank { item.appLabel },
        body = ExpandedBody.Ongoing(item),
        tapIntent = item.contentIntent,
        notificationKey = item.key,
    )

    /** A passing notification preview. */
    fun notification(item: NotificationItem) = Presentation(
        kind = ActivityKind.NOTIFICATION,
        leadingIcon = item.appIcon ?: item.smallIcon,
        leadingBitmap = item.largeIcon,
        trailing = when {
            item.otp != null -> Trailing.Text(item.otp, item.accent)
            settings().notificationStyle == NotificationStyle.PREVIEW ->
                Trailing.Text(item.title.take(18), item.accent)
            settings().notificationStyle == NotificationStyle.MINIMAL ->
                Trailing.Text(item.appLabel.take(14), item.accent)
            else -> Trailing.Icon(item.smallIcon, item.accent)
        },
        accent = item.accent,
        title = item.title.ifBlank { item.appLabel },
        subtitle = item.text.ifBlank { item.appLabel },
        body = ExpandedBody.Notification(item),
        tapIntent = item.contentIntent,
        notificationKey = item.key,
    )

    /** The low-battery warning. */
    fun batteryLow(state: BatteryState) = Presentation(
        kind = ActivityKind.BATTERY_LOW,
        leadingIcon = drawable(R.drawable.ic_battery_full),
        leadingTint = 0xFFFF453A.toInt(),
        trailing = Trailing.Ring(state.level / 100f, 0xFFFF453A.toInt()),
        accent = 0xFFFF453A.toInt(),
        title = context.getString(R.string.low_battery),
        subtitle = context.getString(R.string.remaining, state.level),
        body = ExpandedBody.Charging(state.level, state.plugged, state.fast),
    )

    /** Plugging in or unplugging. */
    fun charging(state: BatteryState) = Presentation(
        kind = ActivityKind.CHARGING,
        leadingIcon = drawable(
            if (state.plugged) R.drawable.ic_battery_charging else R.drawable.ic_battery_full
        ),
        leadingTint = if (state.plugged) 0xFF34C759.toInt() else 0xFFFFFFFF.toInt(),
        trailing = Trailing.Ring(
            state.level / 100f,
            if (state.plugged) 0xFF34C759.toInt() else 0xFFFFFFFF.toInt(),
            "${state.level}"
        ),
        accent = if (state.plugged) 0xFF34C759.toInt() else 0xFF8E8E93.toInt(),
        title = when {
            state.full -> context.getString(R.string.fully_charged)
            state.plugged && state.fast -> context.getString(R.string.fast_charging)
            state.plugged -> context.getString(R.string.charging)
            else -> context.getString(R.string.unplugged)
        },
        subtitle = context.getString(R.string.battery, state.level),
        body = ExpandedBody.Charging(state.level, state.plugged, state.fast),
    )

    /** A volume change. */
    fun volume(stream: Int, level: Int, fraction: Float) = Presentation(
        kind = ActivityKind.VOLUME,
        leadingIcon = drawable(
            if (level == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        ),
        trailing = Trailing.Ring(fraction, accent(), "${(fraction * 100).toInt()}"),
        accent = accent(),
        title = if (stream == AudioManager.STREAM_MUSIC) context.getString(R.string.media_volume) else context.getString(R.string.ring_volume),
        subtitle = "${(fraction * 100).toInt()}%",
        body = ExpandedBody.QuickPanel,
    )

    /** A ringer-mode change. */
    fun ringer(iconRes: Int, title: String) = Presentation(
        kind = ActivityKind.RINGER,
        leadingIcon = drawable(iconRes),
        trailing = Trailing.Text(title),
        accent = accent(),
        title = title,
        subtitle = context.getString(R.string.ringer_mode),
        body = ExpandedBody.Message(title, context.getString(R.string.ringer_mode_changed)),
    )

    /** The unlock confirmation. */
    fun unlocked() = Presentation(
        kind = ActivityKind.UNLOCK,
        leadingIcon = drawable(R.drawable.ic_unlock),
        leadingTint = 0xFF34C759.toInt(),
        trailing = Trailing.Text(context.getString(R.string.unlocked), 0xFF34C759.toInt()),
        accent = 0xFF34C759.toInt(),
        title = context.getString(R.string.unlocked),
        subtitle = null,
        body = ExpandedBody.Message(context.getString(R.string.unlocked), null),
    )

    /** The microphone or camera going live. */
    fun privacy(iconRes: Int, label: String) = Presentation(
        kind = ActivityKind.PRIVACY,
        leadingIcon = drawable(iconRes),
        leadingTint = 0xFF34C759.toInt(),
        trailing = Trailing.Icon(drawable(iconRes), 0xFF34C759.toInt()),
        accent = 0xFF34C759.toInt(),
        title = label,
        subtitle = context.getString(R.string.privacy_indicator),
        body = ExpandedBody.Message(label, context.getString(R.string.app_using_sensor_right_now)),
    )

    /** A running countdown. */
    fun timer(remaining: Long, total: Long, running: Boolean) = Presentation(
        kind = ActivityKind.TIMER,
        leadingIcon = drawable(R.drawable.ic_timer),
        leadingTint = accent(),
        trailing = Trailing.Text(IslandView.formatDuration(remaining, true), accent()),
        accent = accent(),
        title = context.getString(R.string.timer),
        subtitle = IslandView.formatDuration(remaining, true),
        body = ExpandedBody.Timer(remaining, total, running),
    )

    /** A countdown reaching zero. */
    fun timerFinished() = Presentation(
        kind = ActivityKind.NOTIFICATION,
        leadingIcon = drawable(R.drawable.ic_timer),
        leadingTint = accent(),
        trailing = Trailing.Text(context.getString(R.string.done), accent()),
        accent = accent(),
        title = context.getString(R.string.timer_finished),
        subtitle = null,
        body = ExpandedBody.Message(context.getString(R.string.timer_finished), null),
    )

    /** Nothing happening: the resting island. */
    fun idle() = Presentation(
        kind = ActivityKind.IDLE,
        leadingIcon = drawable(R.drawable.ic_island),
        trailing = Trailing.None,
        accent = accent(),
        title = context.getString(R.string.app_name),
        subtitle = null,
        body = ExpandedBody.QuickPanel,
    )

    /** The stopwatch. */
    fun stopwatch(elapsed: Long, running: Boolean, laps: List<Long>) = Presentation(
        kind = ActivityKind.STOPWATCH,
        leadingIcon = drawable(R.drawable.ic_stopwatch),
        leadingTint = accent(),
        trailing = Trailing.Text(formatStopwatch(elapsed), accent()),
        accent = accent(),
        title = context.getString(R.string.stopwatch),
        subtitle = formatStopwatch(elapsed),
        body = ExpandedBody.Stopwatch(elapsed, running, laps),
    )

    /** The recent-notifications panel. */
    fun history(items: List<NotificationItem>) = Presentation(
        kind = ActivityKind.IDLE,
        leadingIcon = drawable(R.drawable.ic_bell),
        leadingTint = accent(),
        accent = accent(),
        title = context.getString(R.string.recent),
        subtitle = context.resources.getQuantityString(R.plurals.notification_count, items.size, items.size),
        body = ExpandedBody.History(items),
    )

    private fun drawable(res: Int): Drawable? = ContextCompat.getDrawable(context, res)
}
