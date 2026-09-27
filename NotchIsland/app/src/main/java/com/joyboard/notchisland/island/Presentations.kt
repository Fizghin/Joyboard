package com.joyboard.notchisland.island

import android.content.Context
import android.graphics.drawable.Drawable
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.NotificationStyle
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.util.formatStopwatch
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import java.util.Date
import android.provider.Settings
import com.joyboard.notchisland.util.readableAccent
import kotlin.math.roundToInt

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
        trailing = Trailing.Waveform(true, IslandColors.GREEN),
        accent = IslandColors.GREEN,
        fixedAccent = IslandColors.GREEN,
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

    /** The low-battery warning. Tapping it goes to Battery Saver. */
    fun batteryLow(state: BatteryState) = Presentation(
        kind = ActivityKind.BATTERY_LOW,
        leadingIcon = drawable(R.drawable.ic_battery_full),
        leadingTint = IslandColors.RED,
        trailing = Trailing.Text(percent(state.level), IslandColors.RED),
        accent = IslandColors.RED,
        fixedAccent = IslandColors.RED,
        title = context.getString(R.string.low_battery),
        subtitle = context.getString(R.string.remaining, state.level),
        body = ExpandedBody.Charging(
            state.level, state.plugged, state.fast, state.fullInMs, warning = true, temperatureC = state.temperatureC,
        ),
        tapIntent = PendingIntent.getActivity(
            context, BATTERY_SAVER_REQUEST,
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )

    /** Plugging in or unplugging. */
    fun charging(state: BatteryState): Presentation {
        val color = if (state.plugged) IslandColors.GREEN else IslandColors.WHITE
        return Presentation(
            kind = ActivityKind.CHARGING,
            leadingIcon = drawable(
                if (state.plugged) R.drawable.ic_battery_charging else R.drawable.ic_battery_full
            ),
            leadingTint = color,
            trailing = Trailing.Text(percent(state.level), color),
            accent = color,
            fixedAccent = color,
            title = when {
                state.full -> context.getString(R.string.fully_charged)
                state.plugged && state.fast -> context.getString(R.string.fast_charging)
                state.plugged -> context.getString(R.string.charging)
                else -> context.getString(R.string.unplugged)
            },
            subtitle = context.getString(R.string.battery, state.level),
            body = ExpandedBody.Charging(
                state.level, state.plugged, state.fast, state.fullInMs, temperatureC = state.temperatureC,
            ),
        )
    }

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

    /** A ringer-mode change. Silent is red, as the iPhone's switch is. */
    fun ringer(iconRes: Int, title: String): Presentation {
        val tint = if (iconRes == R.drawable.ic_bell_off) IslandColors.RED else null
        return Presentation(
            kind = ActivityKind.RINGER,
            leadingIcon = drawable(iconRes),
            leadingTint = tint,
            trailing = Trailing.Text(title, tint),
            accent = accent(),
            fixedAccent = tint,
            title = title,
            subtitle = context.getString(R.string.ringer_mode),
            body = NOTHING_MORE,
        )
    }

    /** The unlock confirmation. */
    fun unlocked() = Presentation(
        kind = ActivityKind.UNLOCK,
        leadingIcon = drawable(R.drawable.ic_unlock),
        leadingTint = IslandColors.GREEN,
        trailing = Trailing.Text(context.getString(R.string.unlocked), IslandColors.GREEN),
        accent = IslandColors.GREEN,
        fixedAccent = IslandColors.GREEN,
        title = context.getString(R.string.unlocked),
        subtitle = null,
        body = NOTHING_MORE,
    )

    /** The microphone or camera going live: a green dot for the camera, orange for the microphone. */
    fun privacy(iconRes: Int, label: String): Presentation {
        val tint = if (iconRes == R.drawable.ic_mic) IslandColors.ORANGE else IslandColors.GREEN
        return Presentation(
            kind = ActivityKind.PRIVACY,
            leadingIcon = drawable(iconRes),
            leadingTint = tint,
            trailing = Trailing.Icon(drawable(R.drawable.ic_dot), tint),
            accent = tint,
            fixedAccent = tint,
            title = label,
            subtitle = context.getString(R.string.app_using_sensor_right_now),
            body = NOTHING_MORE,
        )
    }

    /** A running countdown. */
    fun timer(remaining: Long, total: Long, running: Boolean) = Presentation(
        kind = ActivityKind.TIMER,
        leadingIcon = drawable(R.drawable.ic_timer),
        leadingTint = IslandColors.ORANGE,
        trailing = Trailing.Text(IslandView.formatDuration(remaining, true), IslandColors.ORANGE),
        accent = IslandColors.ORANGE,
        fixedAccent = IslandColors.ORANGE,
        title = context.getString(R.string.timer),
        subtitle = IslandView.formatDuration(remaining, true),
        body = ExpandedBody.Timer(remaining, total, running),
    )

    /** A countdown reaching zero. */
    fun timerFinished() = Presentation(
        kind = ActivityKind.NOTIFICATION,
        leadingIcon = drawable(R.drawable.ic_timer),
        leadingTint = IslandColors.ORANGE,
        trailing = Trailing.Text(context.getString(R.string.done), IslandColors.ORANGE),
        accent = IslandColors.ORANGE,
        fixedAccent = IslandColors.ORANGE,
        title = context.getString(R.string.timer_finished),
        subtitle = null,
        body = NOTHING_MORE,
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
        leadingTint = IslandColors.ORANGE,
        trailing = Trailing.Text(formatStopwatch(elapsed), IslandColors.ORANGE),
        accent = IslandColors.ORANGE,
        fixedAccent = IslandColors.ORANGE,
        title = context.getString(R.string.stopwatch),
        subtitle = formatStopwatch(elapsed),
        body = ExpandedBody.Stopwatch(elapsed, running, laps),
    )

    /** The recent-notifications panel. */
    fun history(items: List<NotificationItem>) = Presentation(
        kind = ActivityKind.IDLE,
        leadingIcon = drawable(R.drawable.ic_bell),
        accent = accent(),
        title = context.getString(R.string.recent),
        subtitle = context.resources.getQuantityString(R.plurals.notification_count, items.size, items.size),
        body = ExpandedBody.History(items),
    )

    /** Headphones arriving or leaving, by name. */
    fun headphones(name: String, connected: Boolean): Presentation {
        val tint = if (connected) IslandColors.GREEN else IslandColors.GRAY
        val status = context.getString(
            if (connected) R.string.headphones_connected else R.string.headphones_disconnected
        )
        val title = name.ifBlank { context.getString(R.string.headphones) }
        return Presentation(
            kind = ActivityKind.HEADPHONES,
            leadingIcon = drawable(R.drawable.ic_headphones),
            trailing = Trailing.Text(status, tint),
            accent = tint,
            fixedAccent = tint,
            title = title,
            subtitle = status,
            body = NOTHING_MORE,
        )
    }

    /** The torch, for as long as it is on. */
    fun flashlight(): Presentation {
        val amber = IslandColors.AMBER
        return Presentation(
            kind = ActivityKind.FLASHLIGHT,
            leadingIcon = drawable(R.drawable.ic_torch),
            leadingTint = amber,
            trailing = Trailing.Text(context.getString(R.string.torch_state_on), amber),
            accent = amber,
            fixedAccent = amber,
            title = context.getString(R.string.flashlight_on),
            subtitle = context.getString(R.string.flashlight_tap_off),
            // The header already names it; the panel below only needs the switch.
            body = ExpandedBody.Message("", null, actionLabel = context.getString(R.string.turn_off_flashlight)),
        )
    }

    /** Do Not Disturb switching on or off. */
    fun focus(on: Boolean): Presentation {
        val tint = if (on) IslandColors.PURPLE else IslandColors.GRAY
        val state = context.getString(if (on) R.string.dnd_on else R.string.dnd_off)
        val title = context.getString(R.string.do_not_disturb)
        return Presentation(
            kind = ActivityKind.FOCUS,
            leadingIcon = drawable(R.drawable.ic_dnd),
            leadingTint = tint,
            trailing = Trailing.Text(state, tint),
            accent = tint,
            fixedAccent = tint,
            title = title,
            subtitle = state,
            body = NOTHING_MORE,
        )
    }

    /** Rain due soon. */
    fun rainSoon(startMs: Long, nowMs: Long): Presentation {
        val blue = IslandColors.CYAN
        val minutes = ((startMs - nowMs + 59_999) / 60_000).toInt()
        val detail = if (minutes <= 0) context.getString(R.string.rain_starting_now)
        else context.getString(R.string.rain_around, DateFormat.getTimeFormat(context).format(Date(startMs)))
        return Presentation(
            kind = ActivityKind.WEATHER,
            leadingIcon = drawable(R.drawable.ic_weather_rain),
            leadingTint = blue,
            trailing = Trailing.Text(
                if (minutes <= 0) context.getString(R.string.calendar_now)
                else context.getString(R.string.calendar_short_minutes, minutes),
                blue,
            ),
            accent = blue,
            fixedAccent = blue,
            title = context.getString(R.string.rain_soon),
            subtitle = detail,
            body = NOTHING_MORE,
        )
    }

    /** A message from another app. It has no tap target: automation may speak, not launch. */
    fun external(request: AutomationRequest): Presentation {
        val tint = request.color ?: accent()
        return Presentation(
            kind = ActivityKind.EXTERNAL,
            leadingIcon = drawable(request.iconRes),
            leadingTint = request.color,
            // The compact island says what it is about; the icon is already on the other side.
            trailing = Trailing.Text(request.title.take(16), tint),
            accent = tint,
            fixedAccent = request.color,
            title = request.title,
            subtitle = request.text,
            body = NOTHING_MORE,
        )
    }

    /** The next calendar event, counting down to its start. Tapping opens it. */
    fun calendar(event: CalendarEvent, nowMs: Long): Presentation {
        val minutes = CalendarPicker.minutesUntil(event, nowMs)
        val countdown = if (minutes == 0) context.getString(R.string.calendar_now)
        else context.resources.getQuantityString(R.plurals.calendar_in_minutes, minutes, minutes)
        val short = if (minutes == 0) context.getString(R.string.calendar_now)
        else context.getString(R.string.calendar_short_minutes, minutes)
        val time = DateFormat.getTimeFormat(context)
        val span = context.getString(
            R.string.calendar_span, time.format(Date(event.beginMs)), time.format(Date(event.endMs))
        )
        val where = event.location?.let { context.getString(R.string.calendar_span_location, span, it) } ?: span
        val title = event.title.ifBlank { context.getString(R.string.calendar_untitled) }
        val open = Intent(
            Intent.ACTION_VIEW,
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return Presentation(
            kind = ActivityKind.CALENDAR,
            leadingIcon = drawable(R.drawable.ic_calendar),
            leadingTint = IslandColors.RED,
            trailing = Trailing.Text(short, IslandColors.RED),
            accent = IslandColors.RED,
            fixedAccent = IslandColors.RED,
            title = title,
            subtitle = countdown,
            // The header has the title and how long until; the panel adds when and where.
            body = ExpandedBody.Message("", where),
            tapIntent = PendingIntent.getActivity(
                context, event.id.toInt(), open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    /** Directions, for as long as the maps app is navigating. Tapping goes back to the map. */
    fun navigation(item: NotificationItem): Presentation {
        val info = NavigationText.parse(item.title, item.text, item.subText)
        val title = info.instruction.ifBlank { item.appLabel }
        return Presentation(
            kind = ActivityKind.NAVIGATION,
            // Maps apps draw the next turn as the notification's picture.
            leadingBitmap = item.largeIcon,
            leadingIcon = drawable(R.drawable.ic_navigation),
            leadingTint = if (item.largeIcon == null) readableAccent(item.accent) else null,
            trailing = info.distance?.let { Trailing.Text(it, IslandColors.WHITE) }
                ?: Trailing.Icon(item.appIcon ?: item.smallIcon),
            accent = item.accent,
            title = title,
            subtitle = listOfNotNull(info.distance, info.eta).joinToString(" · ").ifBlank { item.appLabel },
            body = ExpandedBody.Navigation(item, info),
            tapIntent = item.contentIntent,
            notificationKey = item.key,
        )
    }

    /** A fast transfer under way, with both directions. */
    fun network(reading: SpeedReading): Presentation {
        val down = SpeedMeter.format(reading.downBps)
        val up = SpeedMeter.format(reading.upBps)
        // Whichever way the data is mostly going is the one worth the compact island's room.
        val lead = if (reading.downBps >= reading.upBps) "↓ $down" else "↑ $up"
        return Presentation(
            kind = ActivityKind.NETWORK,
            leadingIcon = drawable(R.drawable.ic_speed),
            leadingTint = IslandColors.CYAN,
            trailing = Trailing.Text(lead, IslandColors.CYAN),
            accent = IslandColors.CYAN,
            fixedAccent = IslandColors.CYAN,
            title = context.getString(R.string.network_speed),
            subtitle = context.getString(R.string.network_rates, down, up),
            body = NOTHING_MORE,
        )
    }

    /** The battery running hot, with what to do about it. */
    fun batteryHot(state: BatteryState): Presentation {
        val degrees = context.getString(R.string.celsius, (state.temperatureC ?: 0f).roundToInt())
        return Presentation(
            kind = ActivityKind.BATTERY_HOT,
            leadingIcon = drawable(R.drawable.ic_thermometer),
            leadingTint = IslandColors.RED,
            trailing = Trailing.Text(degrees, IslandColors.RED),
            accent = IslandColors.RED,
            fixedAccent = IslandColors.RED,
            title = context.getString(R.string.battery_hot),
            subtitle = context.getString(
                if (state.plugged) R.string.battery_hot_plugged else R.string.battery_hot_unplugged
            ),
            body = NOTHING_MORE,
        )
    }

    /** The pinned note. Its button ticks it off. */
    fun note(text: String) = Presentation(
        kind = ActivityKind.NOTE,
        leadingIcon = drawable(R.drawable.ic_note),
        leadingTint = IslandColors.AMBER,
        trailing = Trailing.Text(text.lineSequence().first().take(24), IslandColors.AMBER),
        accent = IslandColors.AMBER,
        fixedAccent = IslandColors.AMBER,
        title = context.getString(R.string.note),
        subtitle = text,
        body = ExpandedBody.Message("", null, actionLabel = context.getString(R.string.done)),
    )

    private fun drawable(res: Int): Drawable? = ContextCompat.getDrawable(context, res)

    private fun percent(level: Int) = context.getString(R.string.percent, level)

    private companion object {
        /** A panel with nothing to add to its header. */
        val NOTHING_MORE = ExpandedBody.Message("", null)
        const val BATTERY_SAVER_REQUEST = 0x5AFE
    }
}
