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
import com.joyboard.notchisland.util.dp
import com.joyboard.notchisland.util.formatRelative
import com.joyboard.notchisland.util.formatStopwatch
import java.util.Date
import com.joyboard.notchisland.util.formatDuration

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
            is ExpandedBody.Charging -> buildChargingBody(body, accent, into, refs)
            is ExpandedBody.Timer -> buildTimerBody(body, accent, into, refs)
            is ExpandedBody.Message -> buildMessageBody(body, into, refs)
            is ExpandedBody.Ongoing -> buildOngoingBody(body.item, accent, into, refs)
            is ExpandedBody.Call -> buildCallBody(body.item, accent, into, refs)
            is ExpandedBody.Stopwatch -> buildStopwatchBody(body, accent, into, refs)
            is ExpandedBody.History -> buildHistoryBody(body.items, accent, into, refs)
            ExpandedBody.QuickPanel -> buildQuickPanelBody(accent, into, refs)
        }
        return refs
    }

    private fun buildMediaBody(media: MediaSnapshot, accent: Int, into: LinearLayout, refs: PanelRefs) {
        val seek = SeekBar(context).apply {
            max = media.durationMs.coerceAtLeast(1L).toInt()
            progress = media.positionMs.toInt()
            isEnabled = media.canSeek && media.durationMs > 0
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x4DFFFFFF)
            setPadding(0, 6.dp, 0, 6.dp)
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
        into.addView(
            seek,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val timeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val pos = widgets.smallLabel(formatDuration(media.positionMs))
        val dur = widgets.smallLabel(formatDuration(media.durationMs))
        refs.mediaPosition = pos
        refs.mediaDuration = dur
        timeRow.addView(pos)
        timeRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        timeRow.addView(dur)
        into.addView(
            timeRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.dp }
        }
        val prev = widgets.circleButton(R.drawable.ic_prev, 44.dp, "Previous track") {
            host.listener.onMediaCommand(MediaCommand.PREVIOUS)
        }
        prev.isEnabled = media.canSkipPrev
        val play = widgets.circleButton(
            if (media.playing) R.drawable.ic_pause else R.drawable.ic_play,
            54.dp,
            if (media.playing) "Pause" else "Play",
        ) { host.listener.onMediaCommand(MediaCommand.PLAY_PAUSE) }
        (play.background as? GradientDrawable)?.setColor(accent)
        play.setColorFilter(Color.BLACK)
        refs.playPause = play
        val next = widgets.circleButton(R.drawable.ic_next, 44.dp, "Next track") {
            host.listener.onMediaCommand(MediaCommand.NEXT)
        }
        next.isEnabled = media.canSkipNext
        controls.addView(prev)
        controls.addView(widgets.spacer(18.dp))
        controls.addView(play)
        controls.addView(widgets.spacer(18.dp))
        controls.addView(next)
        into.addView(controls)

        into.addView(buildVolumeRow(accent))
    }

    private fun buildNotificationBody(item: NotificationItem, accent: Int, into: LinearLayout, refs: PanelRefs) {
        if (item.text.isNotBlank()) {
            val text = TextView(context).apply {
                setTextColor(0xD9FFFFFF.toInt())
                textSize = 13f
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
                this.text = item.text
            }
            into.addView(text)
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
        }
        actions.addView(widgets.pillButton("Open", accent, filled = true) { host.listener.onOpenPresentationTarget() })
        item.actions.take(2).forEach { action ->
            actions.addView(widgets.spacer(8.dp))
            actions.addView(widgets.pillButton(action.title, accent) { host.listener.onNotificationAction(action) })
        }
        actions.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(widgets.pillButton("Dismiss", accent) { host.listener.onDismissCurrent() })
        into.addView(actions)
        addNotificationExtras(item, accent, into, refs)
    }

    private fun buildChargingBody(body: ExpandedBody.Charging, accent: Int, into: LinearLayout, refs: PanelRefs) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ring = RingProgressView(context).apply {
            layoutParams = LinearLayout.LayoutParams(62.dp, 62.dp)
            strokeWidth = 5f.dp
            ringColor = if (body.level <= 20) 0xFFFF453A.toInt() else 0xFF34C759.toInt()
            labelSizePx = 15f.dp
            label = "${body.level}%"
            progress = body.level / 100f
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 16.dp }
        }
        column.addView(TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            text = when {
                !body.plugged -> "Unplugged"
                body.level >= 100 -> "Fully charged"
                body.fast -> "Fast charging"
                else -> "Charging"
            }
        })
        column.addView(widgets.smallLabel(
            if (body.plugged) "Battery at ${body.level}%" else "Running on battery"
        ))
        row.addView(ring)
        row.addView(column)
        into.addView(row)
    }

    private fun buildTimerBody(body: ExpandedBody.Timer, accent: Int, into: LinearLayout, refs: PanelRefs) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ring = RingProgressView(context).apply {
            layoutParams = LinearLayout.LayoutParams(58.dp, 58.dp)
            strokeWidth = 5f.dp
            ringColor = accent
            progress = if (body.totalMs <= 0) 0f else body.remainingMs.toFloat() / body.totalMs
        }
        refs.timerRing = ring
        val time = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = android.graphics.Typeface.MONOSPACE
            text = formatDuration(body.remainingMs, forceMinutes = true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 16.dp }
        }
        refs.timerText = time
        row.addView(ring)
        row.addView(time)
        into.addView(row)

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
        }
        controls.addView(
            widgets.pillButton(if (body.running) "Pause" else "Resume", accent, filled = true) {
                host.listener.onTimerCommand(if (body.running) TimerCommand.PAUSE else TimerCommand.RESUME)
            }
        )
        controls.addView(widgets.spacer(8.dp))
        controls.addView(widgets.pillButton("+1 min", accent) { host.listener.onTimerCommand(TimerCommand.ADD_MINUTE) })
        controls.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(widgets.pillButton("Cancel", accent) { host.listener.onTimerCommand(TimerCommand.CANCEL) })
        into.addView(controls)
    }

    private fun buildOngoingBody(item: NotificationItem, accent: Int, into: LinearLayout, refs: PanelRefs) {
        if (item.text.isNotBlank()) {
            into.addView(TextView(context).apply {
                setTextColor(0xD9FFFFFF.toInt())
                textSize = 13f
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                text = item.text
            })
        }
        if (item.hasProgress || item.progressIndeterminate) {
            into.addView(
                ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = item.progressIndeterminate
                    if (!item.progressIndeterminate) {
                        max = item.progressMax.coerceAtLeast(1)
                        progress = item.progress.coerceIn(0, max)
                    }
                    progressTintList = ColorStateList.valueOf(accent)
                    indeterminateTintList = ColorStateList.valueOf(accent)
                    progressBackgroundTintList = ColorStateList.valueOf(0x4DFFFFFF)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp }
            )
            if (item.hasProgress) {
                val percent = item.progress * 100 / item.progressMax.coerceAtLeast(1)
                into.addView(widgets.smallLabel("$percent%"))
            }
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
        }
        actions.addView(widgets.pillButton("Open", accent, filled = true) { host.listener.onOpenPresentationTarget() })
        item.actions.take(2).forEach { action ->
            actions.addView(widgets.spacer(8.dp))
            actions.addView(widgets.pillButton(action.title, accent) { host.listener.onNotificationAction(action) })
        }
        into.addView(actions)
    }

    private fun buildCallBody(item: NotificationItem, accent: Int, into: LinearLayout, refs: PanelRefs) {
        if (item.text.isNotBlank()) {
            into.addView(widgets.smallLabel(item.text))
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
        }
        // A call notification carries its own answer and hang-up actions; we only relay them.
        if (item.actions.isEmpty()) {
            actions.addView(
                widgets.pillButton("Open call", accent, filled = true) { host.listener.onOpenPresentationTarget() }
            )
        } else {
            item.actions.take(3).forEachIndexed { index, action ->
                if (index > 0) actions.addView(widgets.spacer(8.dp))
                val declining = action.title.lowercase().let {
                    it.contains("decline") || it.contains("hang") || it.contains("end") ||
                        it.contains("reject")
                }
                actions.addView(
                    widgets.pillButton(
                        action.title,
                        if (declining) 0xFFFF453A.toInt() else 0xFF34C759.toInt(),
                        filled = true
                    ) { host.listener.onNotificationAction(action) }
                )
            }
        }
        into.addView(actions)
    }

    private fun buildStopwatchBody(body: ExpandedBody.Stopwatch, accent: Int, into: LinearLayout, refs: PanelRefs) {
        val time = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 34f
            typeface = android.graphics.Typeface.MONOSPACE
            text = formatStopwatch(body.elapsedMs)
        }
        refs.stopwatchText = time
        into.addView(time)

        if (body.laps.isNotEmpty()) {
            body.laps.takeLast(3).forEachIndexed { index, lap ->
                into.addView(
                    widgets.smallLabel("Lap ${body.laps.size - index}   ${formatStopwatch(lap)}")
                )
            }
        }

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
        }
        controls.addView(
            widgets.pillButton(if (body.running) "Pause" else "Start", accent, filled = true) {
                host.listener.onStopwatchCommand(StopwatchCommand.START_PAUSE)
            }
        )
        controls.addView(widgets.spacer(8.dp))
        controls.addView(widgets.pillButton("Lap", accent) { host.listener.onStopwatchCommand(StopwatchCommand.LAP) })
        controls.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(
            widgets.pillButton("Reset", accent) { host.listener.onStopwatchCommand(StopwatchCommand.RESET) }
        )
        into.addView(controls)
    }

    private fun buildHistoryBody(items: List<NotificationItem>, accent: Int, into: LinearLayout, refs: PanelRefs) {
        if (items.isEmpty()) {
            into.addView(widgets.smallLabel("Nothing has come through yet"))
            return
        }
        items.take(6).forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setPadding(0, 7.dp, 0, 7.dp)
                setOnClickListener { host.listener.onHistoryTap(item) }
            }
            row.addView(ImageView(context).apply {
                setImageDrawable(item.appIcon ?: item.smallIcon)
                layoutParams = LinearLayout.LayoutParams(22.dp, 22.dp)
            })
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = 10.dp }
            }
            column.addView(TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 12.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                text = item.title
            })
            column.addView(TextView(context).apply {
                setTextColor(0x99FFFFFF.toInt())
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                text = item.text.ifBlank { item.appLabel }
            })
            row.addView(column)
            row.addView(widgets.smallLabel(formatRelative(System.currentTimeMillis(), item.whenMs)))
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
                widgets.pillButton("Copy $code", accent, filled = true) { host.listener.onCopyCode(code) },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp }
            )
        }
        if (!host.settings.quickReplyEnabled) return
        val reply = item.reply ?: return
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
        }
        val field = EditText(context).apply {
            hint = reply.title
            setHintTextColor(0x80FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 3
            background = GradientDrawable().apply {
                cornerRadius = 18f.dp
                setColor(0x1FFFFFFF)
            }
            setPadding(14.dp, 10.dp, 14.dp, 10.dp)
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
            widgets.circleButton(R.drawable.ic_next, 40.dp, "Send reply") {
                host.sendReply(item, field.text)
            }.apply {
                (background as? GradientDrawable)?.setColor(accent)
                setColorFilter(Color.BLACK)
            }
        )
        into.addView(row)
    }

    private fun buildMessageBody(body: ExpandedBody.Message, into: LinearLayout, refs: PanelRefs) {
        into.addView(TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            text = body.title
        })
        body.subtitle?.let { into.addView(widgets.smallLabel(it)) }
    }

    private fun buildQuickPanelBody(accent: Int, into: LinearLayout, refs: PanelRefs) {
        val now = Date()
        val clock = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            text = DateFormat.format(
                if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", now
            )
        }
        val date = widgets.smallLabel(DateFormat.format("EEEE, d MMMM", now).toString())
        into.addView(clock)
        into.addView(date)
        into.addView(buildBrightnessRow(accent))
        into.addView(buildVolumeRow(accent))
    }

    private fun buildVolumeRow(accent: Int): View {
        val (current, max) = host.listener.currentVolume()
        return widgets.sliderRow(R.drawable.ic_volume_up, current, max, accent) { host.listener.onVolumeChange(it) }
    }

    private fun buildBrightnessRow(accent: Int): View =
        widgets.sliderRow(R.drawable.ic_settings, host.listener.currentBrightness(), 255, accent) {
            host.listener.onBrightnessChange(it)
        }
}
