package com.joyboard.notchisland.island

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.IslandWidgets.Emphasis
import com.joyboard.notchisland.util.dp
import com.joyboard.notchisland.util.formatRelative
import com.joyboard.notchisland.util.formatStopwatch
import com.joyboard.notchisland.util.withAlpha
import java.util.Date
import com.joyboard.notchisland.util.formatDuration
import java.util.Locale
import kotlin.math.roundToInt
import androidx.core.view.isNotEmpty

/** The live views a built panel keeps, so they can be refreshed in place rather than rebuilt. */
internal class PanelRefs {
    var mediaSeek: SeekBar? = null
    var mediaPosition: TextView? = null
    var mediaDuration: TextView? = null
    var playPause: ImageView? = null
    var timerText: TextView? = null
    var timerRing: RingProgressView? = null
    var stopwatchText: TextView? = null
    var replyField: EditText? = null
    var lyrics: List<LyricLine>? = null
    var lyricNow: TextView? = null
    var lyricNext: TextView? = null

    /** Moves the lyrics to the line being sung, touching the views only when the line changes. */
    fun showLyricAt(positionMs: Long) {
        val lines = lyrics ?: return
        val index = Lrc.indexAt(lines, positionMs)
        // Before the first line and in the gaps between verses, a note stands in for words.
        val now = lines.getOrNull(index)?.text?.takeIf { it.isNotBlank() } ?: "♪"
        val next = lines.getOrNull(index + 1)?.text.orEmpty()
        if (lyricNow?.text?.toString() != now) lyricNow?.text = now
        if (lyricNext?.text?.toString() != next) lyricNext?.text = next
    }
}

/** What a panel needs from the island it is drawn into. */
internal interface PanelHost {
    val settings: IslandSettings
    val listener: IslandView.Listener
    var userSeeking: Boolean
    fun onReplyFocus(hasFocus: Boolean)
    fun sendReply(item: NotificationItem, text: CharSequence)
}

/**
 * Builds the open island's panel for each kind of activity: music, a notification, a call, a
 * timer and the rest. Split out of IslandView, which is left with the island itself — its size,
 * motion, gestures and the camera — while this file owns what goes inside it.
 *
 * Some panels — the timer, the stopwatch, the battery, a call and the resting island — stand on
 * their own, with no header above them, the way the iPhone lays out its own live activities;
 * IslandView leaves the header off for those. The rest sit under the header and add only what
 * it does not already say.
 */
internal class IslandPanels(
    private val context: Context,
    private val widgets: IslandWidgets,
    private val host: PanelHost,
) {

    fun build(body: ExpandedBody, accent: Int, into: LinearLayout): PanelRefs {
        val refs = PanelRefs()
        when (body) {
            is ExpandedBody.Media -> buildMediaBody(body.media, accent, into, refs)
            is ExpandedBody.Notification -> buildNotificationBody(body.item, accent, into, refs)
            is ExpandedBody.Charging -> buildChargingBody(body, accent, into)
            is ExpandedBody.Timer -> buildTimerBody(body, accent, into, refs)
            is ExpandedBody.Message -> buildMessageBody(body, accent, into)
            is ExpandedBody.Ongoing -> buildOngoingBody(body.item, accent, into)
            is ExpandedBody.Call -> buildCallBody(body.item, into)
            is ExpandedBody.Stopwatch -> buildStopwatchBody(body, accent, into, refs)
            is ExpandedBody.History -> buildHistoryBody(body.items, into)
            is ExpandedBody.Navigation -> buildNavigationBody(body, accent, into)
            ExpandedBody.QuickPanel -> buildQuickPanelBody(accent, into)
        }
        return refs
    }

    // ------------------------------------------------------------------ music

    private fun buildMediaBody(media: MediaSnapshot, accent: Int, into: LinearLayout, refs: PanelRefs) {
        // Elapsed and total sit either side of the bar, as on the iPhone, rather than under it.
        val scrubber = row(topMargin = 2.dp)
        val pos = timeLabel(formatDuration(media.positionMs))
        val dur = timeLabel(formatDuration(media.durationMs))
        val seek = SeekBar(context).apply {
            max = media.durationMs.coerceAtLeast(1L).toInt()
            progress = media.positionMs.toInt()
            isEnabled = media.canSeek && media.durationMs > 0
            widgets.styleSlider(this, accent)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 10.dp; marginEnd = 10.dp }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) refs.mediaPosition?.text = formatDuration(value.toLong())
                }
                override fun onStartTrackingTouch(sb: SeekBar) { host.userSeeking = true }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    host.userSeeking = false
                    host.listener.onMediaSeek(sb.progress.toLong())
                }
            })
        }
        refs.mediaSeek = seek
        refs.mediaPosition = pos
        refs.mediaDuration = dur
        scrubber.addView(pos)
        scrubber.addView(seek)
        scrubber.addView(dur)
        into.addView(scrubber)

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
        }
        // Skips are bare glyphs; only play and pause gets a button shape, in the accent.
        val prev = widgets.circleButton(
            R.drawable.ic_prev, 50.dp, context.getString(R.string.previous_track), Color.WHITE, Color.TRANSPARENT,
        ) { host.listener.onMediaCommand(MediaCommand.PREVIOUS) }
        prev.isEnabled = media.canSkipPrev
        prev.alpha = if (media.canSkipPrev) 1f else 0.35f
        val play = widgets.circleButton(
            if (media.playing) R.drawable.ic_pause else R.drawable.ic_play,
            56.dp,
            if (media.playing) context.getString(R.string.pause) else context.getString(R.string.play),
            Color.BLACK,
            accent,
        ) { host.listener.onMediaCommand(MediaCommand.PLAY_PAUSE) }
        refs.playPause = play
        val next = widgets.circleButton(
            R.drawable.ic_next, 50.dp, context.getString(R.string.next_track), Color.WHITE, Color.TRANSPARENT,
        ) { host.listener.onMediaCommand(MediaCommand.NEXT) }
        next.isEnabled = media.canSkipNext
        next.alpha = if (media.canSkipNext) 1f else 0.35f
        controls.addView(prev)
        controls.addView(widgets.spacer(22.dp))
        controls.addView(play)
        controls.addView(widgets.spacer(22.dp))
        controls.addView(next)
        into.addView(controls)

        media.lyrics?.takeIf { it.isNotEmpty() }?.let { lines ->
            val now = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_HORIZONTAL
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            val next = TextView(context).apply {
                setTextColor(0x80FFFFFF.toInt())
                textSize = 13f
                gravity = Gravity.CENTER_HORIZONTAL
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            refs.lyrics = lines
            refs.lyricNow = now
            refs.lyricNext = next
            refs.showLyricAt(media.positionMs)
            into.addView(now, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp })
            into.addView(next, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2.dp })
        }

        media.upNext?.let { next ->
            into.addView(widgets.detail(context.getString(R.string.up_next, next), size = 12f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dp }
            })
        }

        into.addView(buildVolumeRow(accent, withOutput = true))
    }

    // ------------------------------------------------------------------ notifications

    private fun buildNotificationBody(item: NotificationItem, accent: Int, into: LinearLayout, refs: PanelRefs) {
        // The header names the app when open, so the message itself is said once, here, in full.
        if (item.text.isNotBlank()) into.addView(bodyText(item.text, maxLines = 6))
        val actions = row(topMargin = 12.dp)
        actions.addView(widgets.pillButton(context.getString(R.string.open), accent, Emphasis.FILLED) {
            host.listener.onOpenPresentationTarget()
        })
        item.actions.take(2).forEach { action ->
            actions.addView(widgets.spacer(8.dp))
            actions.addView(actionPill(action.title, accent) { host.listener.onNotificationAction(action) })
        }
        actions.addView(widgets.flex())
        actions.addView(
            widgets.circleButton(
                R.drawable.ic_close, 34.dp, context.getString(R.string.dismiss),
                IslandColors.SECONDARY, IslandColors.FILL,
            ) { host.listener.onDismissCurrent() }
        )
        into.addView(actions)
        addNotificationExtras(item, accent, into, refs)
    }

    private fun buildOngoingBody(item: NotificationItem, accent: Int, into: LinearLayout) {
        if (item.text.isNotBlank()) into.addView(bodyText(item.text, maxLines = 2))
        if (item.hasProgress) {
            val percent = item.progress * 100 / item.progressMax.coerceAtLeast(1)
            val bar = row(topMargin = 10.dp)
            bar.addView(BarProgressView(context).apply {
                color = accent
                progress = item.progress.toFloat() / item.progressMax.coerceAtLeast(1)
                layoutParams = LinearLayout.LayoutParams(0, 6.dp, 1f)
            })
            // Many apps already put the percentage in their text; it is not said twice.
            if (!item.text.contains("$percent%")) {
                bar.addView(timeLabel(context.getString(R.string.percent, percent)).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = 10.dp }
                })
            }
            into.addView(bar)
        } else if (item.progressIndeterminate) {
            into.addView(
                ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(accent)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dp }
            )
        }
        val actions = row(topMargin = 12.dp)
        actions.addView(widgets.pillButton(context.getString(R.string.open), accent, Emphasis.FILLED) {
            host.listener.onOpenPresentationTarget()
        })
        item.actions.take(2).forEach { action ->
            actions.addView(widgets.spacer(8.dp))
            actions.addView(actionPill(action.title, accent) { host.listener.onNotificationAction(action) })
        }
        into.addView(actions)
    }

    /** A call on its own row: who, and the buttons to answer or end it. */
    private fun buildCallBody(item: NotificationItem, into: LinearLayout) {
        val row = row()
        row.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(46.dp, 46.dp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = widgets.roundOutline(23f.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(IslandColors.FILL)
            }
            if (item.largeIcon != null) setImageBitmap(item.largeIcon)
            else setImageDrawable(item.appIcon ?: item.smallIcon)
        })
        row.addView(titleColumn(
            item.title.ifBlank { item.appLabel },
            item.text.ifBlank { context.getString(R.string.call) },
        ))
        // A call notification carries its own answer and hang-up actions; we only relay them.
        if (item.actions.isEmpty()) {
            row.addView(widgets.circleButton(
                R.drawable.ic_call, 48.dp, context.getString(R.string.open_call_2),
                Color.WHITE, IslandColors.GREEN,
            ) { host.listener.onOpenPresentationTarget() })
        } else {
            item.actions.take(3).forEachIndexed { index, action ->
                if (index > 0) row.addView(widgets.spacer(10.dp))
                val onClick = { host.listener.onNotificationAction(action) }
                row.addView(
                    when (callActionStyle(action.title)) {
                        CallAction.END -> widgets.circleButton(
                            R.drawable.ic_call_end, 48.dp, action.title, Color.WHITE, IslandColors.RED, onClick,
                        )
                        CallAction.ANSWER -> widgets.circleButton(
                            R.drawable.ic_call, 48.dp, action.title, Color.WHITE, IslandColors.GREEN, onClick,
                        )
                        CallAction.OTHER -> actionPill(action.title, Color.WHITE, onClick)
                    }
                )
            }
        }
        into.addView(row)
    }

    private fun buildHistoryBody(items: List<NotificationItem>, into: LinearLayout) {
        if (items.isEmpty()) {
            into.addView(widgets.detail(context.getString(R.string.nothing_has_come_through_yet)))
            return
        }
        items.take(6).forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setPadding(0, 6.dp, 0, 6.dp)
                setOnClickListener { host.listener.onHistoryTap(item) }
            }
            row.addView(ImageView(context).apply {
                setImageDrawable(item.appIcon ?: item.smallIcon)
                layoutParams = LinearLayout.LayoutParams(30.dp, 30.dp)
                clipToOutline = true
                outlineProvider = widgets.roundOutline(8f.dp)
            })
            row.addView(titleColumn(item.title.ifBlank { item.appLabel }, item.text.ifBlank { item.appLabel }, small = true))
            row.addView(timeLabel(formatRelative(System.currentTimeMillis(), item.whenMs)))
            into.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    /** The one-tap passcode copy and the inline reply field, when a notification offers them. */
    private fun addNotificationExtras(item: NotificationItem, accent: Int, into: LinearLayout, refs: PanelRefs) {
        if (host.settings.otpDetection) item.otp?.let { code ->
            into.addView(
                widgets.pillButton(context.getString(R.string.copy, code), accent, Emphasis.TINTED) {
                    host.listener.onCopyCode(code)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp }
            )
        }
        if (!host.settings.quickReplyEnabled) return
        val reply = item.reply ?: return
        val row = row(topMargin = 10.dp)
        val field = EditText(context).apply {
            hint = reply.title
            setHintTextColor(0x80FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 3
            background = GradientDrawable().apply {
                cornerRadius = 19f.dp
                setColor(IslandColors.FILL)
            }
            setPadding(16.dp, 10.dp, 16.dp, 10.dp)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            // The overlay window is normally unfocusable; it has to be told to accept the IME.
            setOnFocusChangeListener { _, hasFocus ->
                host.onReplyFocus(hasFocus)
            }
            setOnEditorActionListener { view, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    host.sendReply(item, view.text)
                    true
                } else {
                    false
                }
            }
        }
        refs.replyField = field
        row.addView(field)
        row.addView(widgets.spacer(8.dp))
        row.addView(
            widgets.circleButton(
                R.drawable.ic_arrow_up, 38.dp, context.getString(R.string.send_reply), Color.BLACK, accent,
            ) { host.sendReply(item, field.text) }
        )
        into.addView(row)
    }

    // ------------------------------------------------------------------ battery

    /** The battery on its own row: a drawn cell, what it is doing, and the level in large type. */
    private fun buildChargingBody(body: ExpandedBody.Charging, accent: Int, into: LinearLayout) {
        val row = row()
        row.addView(BatteryView(context).apply {
            level = body.level
            color = accent
            charging = body.plugged
            layoutParams = LinearLayout.LayoutParams(58.dp, 28.dp)
        })
        val title = when {
            body.warning && !body.plugged -> context.getString(R.string.low_battery)
            !body.plugged -> context.getString(R.string.unplugged)
            body.level >= 100 -> context.getString(R.string.fully_charged)
            body.fast -> context.getString(R.string.fast_charging)
            else -> context.getString(R.string.charging)
        }
        val status = when {
            !body.plugged -> context.getString(R.string.running_battery)
            body.level < 100 && body.fullInMs != null -> context.getString(R.string.full_in, span(body.fullInMs))
            else -> context.getString(R.string.plugged_in)
        }
        val temperature = body.temperatureC?.takeIf { host.settings.featureBatteryHeat }
            ?.let { context.getString(R.string.celsius, it.roundToInt()) }
        val detail = listOfNotNull(status, temperature).joinToString(" · ")
        row.addView(titleColumn(title, detail).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = 14.dp
        })
        row.addView(TextView(context).apply {
            text = context.getString(R.string.percent, body.level)
            setTextColor(accent)
            textSize = 30f
            typeface = IslandWidgets.LIGHT
            fontFeatureSettings = IslandWidgets.TABULAR
            includeFontPadding = false
        })
        into.addView(row)
        // Running low with nothing plugged in: the one thing worth doing about it.
        if (body.warning && !body.plugged) {
            into.addView(
                widgets.pillButton(context.getString(R.string.battery_saver), accent, Emphasis.TINTED) {
                    host.listener.onOpenPresentationTarget()
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 12.dp }
            )
        }
    }

    // ------------------------------------------------------------------ clocks

    /** The countdown: its buttons on the left and the time, large, on the right. */
    private fun buildTimerBody(body: ExpandedBody.Timer, accent: Int, into: LinearLayout, refs: PanelRefs) {
        val row = row()
        row.addView(
            widgets.circleButton(
                if (body.running) R.drawable.ic_pause else R.drawable.ic_play,
                50.dp,
                context.getString(if (body.running) R.string.pause else R.string.resume),
                accent,
                accent.withAlpha(0.24f),
            ) { host.listener.onTimerCommand(if (body.running) TimerCommand.PAUSE else TimerCommand.RESUME) }
        )
        row.addView(widgets.spacer(10.dp))
        row.addView(
            widgets.circleButton(
                R.drawable.ic_close, 50.dp, context.getString(R.string.cancel), Color.WHITE, IslandColors.FILL,
            ) { host.listener.onTimerCommand(TimerCommand.CANCEL) }
        )
        row.addView(widgets.spacer(10.dp))
        row.addView(
            widgets.textCircleButton(
                context.getString(R.string.plus_one), 50.dp, context.getString(R.string.n_1_min),
                Color.WHITE, IslandColors.FILL,
            ) { host.listener.onTimerCommand(TimerCommand.ADD_MINUTE) }
        )
        row.addView(widgets.flex())
        val time = bigClock(formatDuration(body.remainingMs, forceMinutes = true), accent)
        refs.timerText = time
        row.addView(clockColumn(context.getString(R.string.timer), accent, time))
        into.addView(row)
    }

    /** The stopwatch: lap or reset and start or stop on the left, the time on the right. */
    private fun buildStopwatchBody(body: ExpandedBody.Stopwatch, accent: Int, into: LinearLayout, refs: PanelRefs) {
        val row = row()
        // As on the iPhone: the left button takes a lap while running and resets once stopped.
        row.addView(
            if (body.running) {
                widgets.circleButton(
                    R.drawable.ic_flag, 50.dp, context.getString(R.string.lap_2), Color.WHITE, IslandColors.FILL,
                ) { host.listener.onStopwatchCommand(StopwatchCommand.LAP) }
            } else {
                widgets.circleButton(
                    R.drawable.ic_reset, 50.dp, context.getString(R.string.reset), Color.WHITE, IslandColors.FILL,
                ) { host.listener.onStopwatchCommand(StopwatchCommand.RESET) }
            }
        )
        row.addView(widgets.spacer(10.dp))
        row.addView(
            widgets.circleButton(
                if (body.running) R.drawable.ic_pause else R.drawable.ic_play,
                50.dp,
                context.getString(if (body.running) R.string.pause else R.string.start),
                accent,
                accent.withAlpha(0.24f),
            ) { host.listener.onStopwatchCommand(StopwatchCommand.START_PAUSE) }
        )
        row.addView(widgets.flex())
        val time = bigClock(formatStopwatch(body.elapsedMs), Color.WHITE)
        refs.stopwatchText = time
        row.addView(clockColumn(context.getString(R.string.stopwatch), accent, time))
        into.addView(row)

        // Newest first, each lap as its own length rather than the running total.
        val laps = body.laps
        for (i in laps.indices.reversed().take(3)) {
            val lap = row(topMargin = if (i == laps.lastIndex) 12.dp else 6.dp)
            lap.addView(widgets.detail(context.getString(R.string.lap_n, i + 1)))
            lap.addView(widgets.flex())
            lap.addView(widgets.detail(formatStopwatch(laps[i] - (laps.getOrNull(i - 1) ?: 0L)), Color.WHITE).apply {
                fontFeatureSettings = IslandWidgets.TABULAR
            })
            into.addView(lap)
        }
    }

    // ------------------------------------------------------------------ everything else

    private fun buildMessageBody(body: ExpandedBody.Message, accent: Int, into: LinearLayout) {
        // Only what the header does not already say: most activities have a title and nothing
        // more, and their panel is empty.
        if (body.title.isNotBlank()) {
            into.addView(TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = IslandWidgets.MEDIUM
                text = body.title
            })
        }
        body.subtitle?.let { into.addView(bodyText(it, maxLines = 4)) }
        body.actionLabel?.let { label ->
            into.addView(
                widgets.pillButton(label, accent, Emphasis.FILLED) { host.listener.onOpenPresentationTarget() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp).apply {
                    if (into.isNotEmpty()) topMargin = 10.dp
                }
            )
        }
    }

    /** The resting island: the time and date, what is coming, and the controls people reach for. */
    private fun buildQuickPanelBody(accent: Int, into: LinearLayout) {
        val now = Date()
        val top = row()
        val left = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        left.addView(TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 36f
            typeface = IslandWidgets.LIGHT
            fontFeatureSettings = IslandWidgets.TABULAR
            includeFontPadding = false
            // Skeletons, so each locale gets its own order and separators.
            val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
            text = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton), now)
        })
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")
        left.addView(widgets.detail(DateFormat.format(datePattern, now).toString()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
        })
        top.addView(left)

        val right = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        host.listener.currentWeather()?.let { weather ->
            val sky = context.getString(weather.sky.labelRes)
            val temp = row().apply {
                contentDescription = context.getString(R.string.weather_now, weather.degrees, sky)
            }
            temp.addView(ImageView(context).apply {
                setImageDrawable(widgets.icon(weather.sky.iconRes))
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(22.dp, 22.dp).apply { marginEnd = 6.dp }
            })
            temp.addView(TextView(context).apply {
                text = weather.degrees
                setTextColor(Color.WHITE)
                textSize = 24f
                typeface = IslandWidgets.LIGHT
                includeFontPadding = false
            })
            right.addView(temp)
            right.addView(widgets.detail(sky, size = 12f).apply { gravity = Gravity.END })
        }
        host.listener.nextAlarm()?.let { at ->
            val time = DateFormat.getTimeFormat(context).format(Date(at))
            val alarm = row(topMargin = 4.dp).apply {
                contentDescription = context.getString(R.string.alarm_at, time)
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
            alarm.addView(ImageView(context).apply {
                setImageDrawable(widgets.icon(R.drawable.ic_alarm))
                setColorFilter(IslandColors.SECONDARY)
                layoutParams = LinearLayout.LayoutParams(14.dp, 14.dp).apply { marginEnd = 4.dp }
            })
            alarm.addView(widgets.detail(time, size = 12f))
            right.addView(alarm)
        }
        if (right.isNotEmpty()) top.addView(right)
        into.addView(top)

        if (host.settings.quickTimers && host.settings.featureTimer) into.addView(buildQuickTimers(accent))
        into.addView(buildBrightnessRow(accent).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = 10.dp
        })
        into.addView(buildVolumeRow(accent))
    }

    /** One tap to a countdown, the lengths people reach for most, and the stopwatch beside them. */
    private fun buildQuickTimers(accent: Int): View {
        val row = row(topMargin = 12.dp)
        QUICK_TIMER_MINUTES.forEachIndexed { index, minutes ->
            if (index > 0) row.addView(widgets.spacer(6.dp))
            row.addView(
                widgets.pillButton(
                    context.getString(R.string.duration_minutes, minutes), IslandColors.ORANGE, Emphasis.TINTED,
                ) { host.listener.onStartTimer(minutes) }.apply {
                    contentDescription = context.getString(R.string.start_timer_minutes, minutes)
                    // Four share the row, so the label gets the width rather than the padding.
                    setPadding(0, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
        }
        if (host.settings.featureStopwatch) {
            row.addView(widgets.spacer(6.dp))
            row.addView(
                widgets.circleButton(
                    R.drawable.ic_stopwatch, 34.dp, context.getString(R.string.start_stopwatch),
                    IslandColors.ORANGE, IslandColors.ORANGE.withAlpha(0.2f),
                ) { host.listener.onStartStopwatch() }
            )
        }
        return row
    }

    /** The next turn, large: the arrow, how far, what to do, and when you get there. */
    private fun buildNavigationBody(body: ExpandedBody.Navigation, accent: Int, into: LinearLayout) {
        val item = body.item
        val info = body.info
        val row = row()
        row.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(52.dp, 52.dp)
            if (item.largeIcon != null) {
                setImageBitmap(item.largeIcon)
            } else {
                setImageDrawable(widgets.icon(R.drawable.ic_navigation))
                setColorFilter(accent)
            }
        })
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 14.dp }
        }
        info.distance?.let {
            column.addView(TextView(context).apply {
                text = it
                setTextColor(Color.WHITE)
                textSize = 30f
                typeface = IslandWidgets.LIGHT
                includeFontPadding = false
            })
        }
        column.addView(TextView(context).apply {
            text = info.instruction.ifBlank { item.appLabel }
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = IslandWidgets.MEDIUM
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(column)
        into.addView(row)
        info.eta?.let {
            into.addView(widgets.detail(it).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp }
            })
        }
        val actions = row(topMargin = 12.dp)
        actions.addView(widgets.pillButton(context.getString(R.string.open), accent, Emphasis.FILLED) {
            host.listener.onOpenPresentationTarget()
        })
        item.actions.take(2).forEach { action ->
            actions.addView(widgets.spacer(8.dp))
            actions.addView(actionPill(action.title, accent) { host.listener.onNotificationAction(action) })
        }
        into.addView(actions)
    }

    private fun buildVolumeRow(accent: Int, withOutput: Boolean = false): View {
        val (current, max) = host.listener.currentVolume()
        // Next to the volume, the way iOS puts AirPlay: where the sound goes, beside how loud.
        val output = if (!withOutput) null else {
            widgets.circleButton(R.drawable.ic_output, 32.dp, context.getString(R.string.choose_audio_output)) {
                host.listener.onMediaOutput()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(32.dp, 32.dp).apply { marginStart = 10.dp }
            }
        }
        return widgets.sliderRow(
            R.drawable.ic_volume_up, current, max, accent, output, context.getString(R.string.volume),
        ) { host.listener.onVolumeChange(it) }
    }

    private fun buildBrightnessRow(accent: Int): View =
        widgets.sliderRow(
            R.drawable.ic_brightness, host.listener.currentBrightness(), 255, accent,
            label = context.getString(R.string.brightness),
        ) { host.listener.onBrightnessChange(it) }

    // ------------------------------------------------------------------ pieces

    private fun row(topMargin: Int = 0) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = topMargin }
    }

    private fun bodyText(text: String, maxLines: Int) = TextView(context).apply {
        setTextColor(0xE6FFFFFF.toInt())
        textSize = 14f
        setLineSpacing(2f.dp, 1f)
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
        this.text = text
    }

    /** A name over a line of detail, taking whatever width its row leaves. */
    private fun titleColumn(title: String, detail: String?, small: Boolean = false) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = if (small) 10.dp else 12.dp; marginEnd = 8.dp }
        addView(TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = if (small) 13.5f else 16f
            typeface = IslandWidgets.MEDIUM
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = title
        })
        detail?.let {
            addView(widgets.detail(it, size = if (small) 12.5f else 13f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }

    /** Short figures — times, percentages — in digits that keep their width. */
    private fun timeLabel(text: String) = widgets.detail(text, size = 12f).apply {
        fontFeatureSettings = IslandWidgets.TABULAR
    }

    private fun bigClock(text: String, color: Int) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        textSize = 38f
        typeface = IslandWidgets.LIGHT
        fontFeatureSettings = IslandWidgets.TABULAR
        includeFontPadding = false
        maxLines = 1
    }

    private fun clockColumn(label: String, accent: Int, clock: TextView) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END
        addView(TextView(context).apply {
            text = label
            setTextColor(accent)
            textSize = 13f
            typeface = IslandWidgets.MEDIUM
        })
        addView(clock)
    }

    /** A notification's own action, kept to a width that leaves room for its neighbours. */
    private fun actionPill(title: String, accent: Int, onClick: () -> Unit) =
        widgets.pillButton(title, accent, Emphasis.TINTED, onClick).apply {
            maxWidth = 128.dp
            ellipsize = TextUtils.TruncateAt.END
        }

    private fun span(ms: Long): String {
        val minutes = ((ms + 59_999) / 60_000).toInt()
        return if (minutes >= 60) {
            context.getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
        } else {
            context.getString(R.string.duration_minutes, minutes)
        }
    }

    internal enum class CallAction { ANSWER, END, OTHER }

    companion object {
        /** The countdowns offered in the quick panel, in minutes. */
        internal val QUICK_TIMER_MINUTES = listOf(1, 5, 10, 25)

        /** Which of a call notification's buttons answers and which ends, by what they say. */
        internal fun callActionStyle(title: String): CallAction {
            val t = title.lowercase(Locale.ROOT)
            return when {
                listOf("decline", "hang", "end", "reject").any { it in t } -> CallAction.END
                listOf("answer", "accept", "pick up").any { it in t } -> CallAction.ANSWER
                else -> CallAction.OTHER
            }
        }
    }
}
