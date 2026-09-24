package com.joyboard.notchisland.island

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.ColorSource
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.util.DynamicColors
import com.joyboard.notchisland.util.dp
import com.joyboard.notchisland.util.formatRelative
import com.joyboard.notchisland.util.formatStopwatch
import com.joyboard.notchisland.util.readableAccent
import com.joyboard.notchisland.util.visible
import java.util.Date
import kotlin.math.abs

/**
 * The island itself: a rounded, animated container that morphs between a bare pill, a compact
 * two-sided readout and a full expanded panel.
 */
@SuppressLint("ViewConstructor")
class IslandView(context: Context, private val listener: Listener) : FrameLayout(context) {

    interface Listener {
        fun onRequestMode(mode: IslandMode)
        fun onGesture(action: GestureAction)
        fun onMediaCommand(command: MediaCommand)
        fun onMediaSeek(positionMs: Long)
        fun onTimerCommand(command: TimerCommand)
        fun onStopwatchCommand(command: StopwatchCommand)
        fun onSendReply(item: NotificationItem, text: CharSequence)
        fun onReplyFocusChanged(active: Boolean)
        fun onCopyCode(code: String)
        fun onHistoryTap(item: NotificationItem)
        fun onQuickToggle(toggle: QuickToggle)
        fun onVolumeChange(progress: Int)
        fun onBrightnessChange(progress: Int)
        fun onOpenPresentationTarget()
        fun onNotificationAction(action: NotificationAction)
        fun onDismissCurrent()
        fun quickToggleState(toggle: QuickToggle): Boolean
        fun currentVolume(): Pair<Int, Int>
        fun currentBrightness(): Int
    }

    // ------------------------------------------------------------------ state

    private var settings: IslandSettings = IslandSettings()
    private var systemAccent: Int = 0xFF3B82F6.toInt()
    private var systemSurface: Int = Color.BLACK
    private var systemAnimationScale: Float = 1f
    private var presentation: Presentation = Presentation(ActivityKind.IDLE)
    var mode: IslandMode = IslandMode.PILL
        private set

    private val bgDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.BLACK)
    }
    private var sizeAnimator: ValueAnimator? = null
    private var bodySignature: String? = null
    private var cachedExpandedKey: String? = null
    private var cachedExpandedHeight = 0
    private val spring = PathInterpolator(0.22f, 1.12f, 0.36f, 1f)
    private val ease = PathInterpolator(0.33f, 0f, 0.1f, 1f)

    // ------------------------------------------------------------------ views

    private val compactRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(12.dp, 0, 12.dp, 0)
        alpha = 0f
    }
    private val leadingIcon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LinearLayout.LayoutParams(18.dp, 18.dp)
        clipToOutline = true
        outlineProvider = roundOutline(5f.dp)
    }
    private val leadingSpacer = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }
    private val trailingText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 11f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        maxLines = 1
    }
    private val trailingIcon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(16.dp, 16.dp)
    }
    private val trailingRing = RingProgressView(context).apply {
        layoutParams = LinearLayout.LayoutParams(18.dp, 18.dp)
        strokeWidth = 2.4f.dp
    }
    private val trailingWave = WaveformView(context)

    private val expandedRoot = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dp, 14.dp, 16.dp, 14.dp)
        alpha = 0f
        visibility = View.GONE
    }

    private val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val headerArt = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = roundOutline(10f.dp)
        background = GradientDrawable().apply {
            cornerRadius = 10f.dp
            setColor(0x22FFFFFF)
        }
    }
    private val headerTitle = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val headerSubtitle = TextView(context).apply {
        setTextColor(0xB3FFFFFF.toInt())
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val headerTrailing = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(40.dp, 40.dp)
    }
    private val headerWave = WaveformView(context).apply {
        layoutParams = FrameLayout.LayoutParams(22.dp, 18.dp, Gravity.CENTER)
    }

    private val bodyContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12.dp }
    }

    private val togglesRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12.dp }
    }

    private val toggleButtons = mutableMapOf<QuickToggle, ImageView>()

    // media body views
    private var mediaSeek: SeekBar? = null
    private var mediaPosition: TextView? = null
    private var mediaDuration: TextView? = null
    private var playPause: ImageView? = null
    private var userSeeking = false

    // timer body views
    private var timerText: TextView? = null
    private var timerRing: RingProgressView? = null
    private var stopwatchText: TextView? = null
    private var replyField: EditText? = null

    init {
        isClickable = true
        background = bgDrawable
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, bgDrawable.cornerRadius)
            }
        }
        clipToOutline = true

        compactRow.addView(leadingIcon)
        compactRow.addView(leadingSpacer)
        compactRow.addView(trailingText)
        compactRow.addView(trailingIcon)
        compactRow.addView(trailingRing)
        compactRow.addView(trailingWave)
        addView(
            compactRow,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 12.dp; marginEnd = 8.dp }
            addView(headerTitle)
            addView(headerSubtitle)
        }
        headerTrailing.addView(headerWave)
        headerRow.addView(headerArt)
        headerRow.addView(titleColumn)
        headerRow.addView(headerTrailing)

        expandedRoot.addView(
            headerRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        expandedRoot.addView(bodyContainer)
        expandedRoot.addView(togglesRow)
        addView(
            expandedRoot,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )

        buildToggles()
        applySettings(settings)
    }

    // ------------------------------------------------------------------ public API

    fun applySettings(s: IslandSettings) {
        settings = s
        cachedExpandedKey = null
        // Honour the accessibility "remove animations" setting rather than fighting it.
        systemAnimationScale = if (s.respectSystemAnimationScale) {
            runCatching {
                android.provider.Settings.Global.getFloat(
                    context.contentResolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                )
            }.getOrDefault(1f)
        } else {
            1f
        }
        systemAccent = DynamicColors.accent(context, dark = true)
        systemSurface = DynamicColors.surface(context, dark = true)
        bgDrawable.setColor(resolvedBackground())
        if (s.borderEnabled) {
            bgDrawable.setStroke(s.borderWidth.dp, s.borderColor)
        } else {
            bgDrawable.setStroke(0, Color.TRANSPARENT)
        }
        elevation = if (s.shadowEnabled) 10f.dp else 0f
        bgDrawable.cornerRadius = radiusFor(mode)
        invalidateOutline()
        requestLayout()
    }

    fun setPresentation(p: Presentation) {
        presentation = p
        val accent = resolveAccent(p)

        // ---- compact side ----
        if (p.leadingBitmap != null) {
            leadingIcon.setImageBitmap(p.leadingBitmap)
            leadingIcon.clearColorFilter()
        } else {
            leadingIcon.setImageDrawable(p.leadingIcon)
            p.leadingTint?.let { leadingIcon.setColorFilter(it) } ?: leadingIcon.clearColorFilter()
        }
        leadingIcon.visible(p.leadingBitmap != null || p.leadingIcon != null)

        trailingText.visible(false)
        trailingIcon.visible(false)
        trailingRing.visible(false)
        trailingWave.visible(false)
        when (val t = p.trailing) {
            Trailing.None -> Unit
            is Trailing.Text -> {
                trailingText.text = t.text
                trailingText.setTextColor(t.color ?: Color.WHITE)
                trailingText.visible(true)
            }
            is Trailing.Icon -> {
                trailingIcon.setImageDrawable(t.drawable)
                t.tint?.let { trailingIcon.setColorFilter(it) } ?: trailingIcon.clearColorFilter()
                trailingIcon.visible(true)
            }
            is Trailing.Ring -> {
                trailingRing.ringColor = t.color
                trailingRing.label = t.label
                trailingRing.progress = t.progress
                trailingRing.visible(true)
            }
            is Trailing.Waveform -> {
                trailingWave.barColor = t.color
                trailingWave.playing = t.playing
                trailingWave.visible(true)
            }
        }

        // ---- expanded side ----
        headerTitle.text = p.title ?: ""
        headerSubtitle.text = p.subtitle ?: ""
        headerSubtitle.visible(!p.subtitle.isNullOrBlank())
        headerTitle.visible(!p.title.isNullOrBlank())

        when (val body = p.body) {
            is ExpandedBody.Media -> bindMediaHeader(body.media, accent)
            is ExpandedBody.Notification -> bindNotificationHeader(body.item)
            else -> {
                if (p.leadingBitmap != null) {
                    headerArt.setImageBitmap(p.leadingBitmap)
                    headerArt.clearColorFilter()
                } else {
                    headerArt.setImageDrawable(p.leadingIcon)
                    headerArt.setColorFilter(accent)
                }
                headerArt.visible(p.leadingIcon != null || p.leadingBitmap != null)
                headerWave.playing = false
                headerWave.visible(false)
            }
        }

        if (mode == IslandMode.EXPANDED) {
            togglesRow.visible(showsToggles(p))
            // Only re-measure and resize when the panel's contents actually changed.
            if (buildBody(p, accent)) resizeToMeasuredHeight()
            refreshToggleStates()
        }
    }

    /** Grows or shrinks the expanded panel to fit new content, without a full mode animation. */
    private fun resizeToMeasuredHeight() {
        val targetHeight = heightFor(IslandMode.EXPANDED, widthFor(IslandMode.EXPANDED))
        val startHeight = height.takeIf { it > 0 } ?: targetHeight
        if (startHeight == targetHeight) return
        sizeAnimator?.cancel()
        sizeAnimator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = dur(240)
            interpolator = ease
            addUpdateListener { a ->
                val lp = layoutParams ?: return@addUpdateListener
                lp.height = a.animatedValue as Int
                layoutParams = lp
            }
            start()
        }
    }

    fun animateToMode(target: IslandMode, force: Boolean = false) {
        if (target == mode && !force) return
        val previous = mode
        mode = target
        val accent = resolveAccent(presentation)
        if (target == IslandMode.EXPANDED) buildBody(presentation, accent)
        togglesRow.visible(target == IslandMode.EXPANDED && showsToggles(presentation))
        if (target == IslandMode.EXPANDED) refreshToggleStates()

        val targetWidth = widthFor(target)
        val targetHeight = heightFor(target, targetWidth)
        val startWidth = if (width == 0) targetWidth else width
        val startHeight = if (height == 0) targetHeight else height
        val startRadius = bgDrawable.cornerRadius
        val targetRadius = radiusFor(target)

        expandedRoot.visible(target == IslandMode.EXPANDED)
        compactRow.visible(target == IslandMode.COMPACT || target == IslandMode.PILL)
        // Bars only cost frames while they can actually be seen.
        trailingWave.visible(trailingWave.visibility == View.VISIBLE && target == IslandMode.COMPACT)
        headerWave.playing = headerWave.playing && target == IslandMode.EXPANDED

        compactRow.animate().alpha(if (target == IslandMode.COMPACT) 1f else 0f)
            .setDuration(dur(160)).start()
        expandedRoot.animate().alpha(if (target == IslandMode.EXPANDED) 1f else 0f)
            .setDuration(dur(if (target == IslandMode.EXPANDED) 220 else 120))
            .setStartDelay(if (target == IslandMode.EXPANDED) dur(70) else 0)
            .start()

        sizeAnimator?.cancel()
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur(if (target == IslandMode.EXPANDED || previous == IslandMode.EXPANDED) 420 else 300)
            interpolator = if (target == IslandMode.EXPANDED) spring else ease
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                val lp = layoutParams ?: return@addUpdateListener
                lp.width = (startWidth + (targetWidth - startWidth) * f).toInt()
                lp.height = (startHeight + (targetHeight - startHeight) * f).toInt()
                bgDrawable.cornerRadius = startRadius + (targetRadius - startRadius) * f
                layoutParams = lp
                invalidateOutline()
            }
            start()
        }

        alpha = if (target == IslandMode.HIDDEN) 0f else 1f
    }

    /** Applies the target size immediately, used when the island is first attached. */
    fun snapToMode(target: IslandMode) {
        mode = target
        val w = widthFor(target)
        val h = heightFor(target, w)
        // The island lives inside whatever container the controller built, so stay generic.
        val lp = layoutParams ?: ViewGroup.LayoutParams(w, h)
        lp.width = w
        lp.height = h
        layoutParams = lp
        bgDrawable.cornerRadius = radiusFor(target)
        compactRow.alpha = if (target == IslandMode.COMPACT) 1f else 0f
        compactRow.visible(target != IslandMode.EXPANDED)
        expandedRoot.alpha = if (target == IslandMode.EXPANDED) 1f else 0f
        expandedRoot.visible(target == IslandMode.EXPANDED)
        invalidateOutline()
    }

    fun refreshMediaProgress(positionMs: Long, durationMs: Long) {
        if (userSeeking) return
        val seek = mediaSeek ?: return
        seek.max = durationMs.coerceAtLeast(1L).toInt()
        seek.progress = positionMs.coerceIn(0, durationMs.coerceAtLeast(1L)).toInt()
        mediaPosition?.text = formatDuration(positionMs)
        mediaDuration?.text = formatDuration(durationMs)
    }

    fun refreshTimer(remainingMs: Long, totalMs: Long) {
        timerText?.text = formatDuration(remainingMs, forceMinutes = true)
        timerRing?.progress = if (totalMs <= 0) 0f else remainingMs.toFloat() / totalMs
    }

    fun refreshStopwatch(elapsedMs: Long) {
        stopwatchText?.text = formatStopwatch(elapsedMs)
    }

    /** True while the reply field holds focus, so the controller keeps the window focusable. */
    fun isReplying(): Boolean = replyField?.hasFocus() == true

    fun clearReplyFocus() {
        replyField?.clearFocus()
    }

    fun refreshToggleStates() {
        toggleButtons.forEach { (toggle, view) ->
            val on = listener.quickToggleState(toggle)
            val accent = resolveAccent(presentation)
            (view.background as? GradientDrawable)?.setColor(
                if (on) accent else 0x1FFFFFFF
            )
            view.setColorFilter(if (on) Color.BLACK else Color.WHITE)
        }
    }

    // ------------------------------------------------------------------ sizing

    private fun widthFor(m: IslandMode): Int = when (m) {
        IslandMode.HIDDEN -> settings.collapsedWidth.dp
        IslandMode.PILL -> settings.collapsedWidth.dp
        IslandMode.COMPACT -> settings.compactWidth.dp
        IslandMode.EXPANDED -> settings.expandedWidth.dp
    }

    private fun heightFor(m: IslandMode, width: Int): Int = when (m) {
        IslandMode.HIDDEN, IslandMode.PILL -> settings.collapsedHeight.dp
        IslandMode.COMPACT -> (settings.collapsedHeight + 4).dp
        IslandMode.EXPANDED -> measureExpandedHeight(width)
    }

    private fun measureExpandedHeight(width: Int): Int {
        val key = "$width|$bodySignature|${togglesRow.visibility}"
        if (key == cachedExpandedKey && cachedExpandedHeight > 0) return cachedExpandedHeight
        expandedRoot.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        cachedExpandedKey = key
        cachedExpandedHeight = expandedRoot.measuredHeight.coerceAtLeast(96.dp)
        return cachedExpandedHeight
    }

    private fun radiusFor(m: IslandMode): Float = when (m) {
        IslandMode.EXPANDED -> 28f.dp
        IslandMode.COMPACT -> ((settings.collapsedHeight + 4) / 2f).dp
            .coerceAtMost(settings.cornerRadius.dp.toFloat() + 4f.dp)
        else -> settings.cornerRadius.dp.toFloat()
            .coerceAtMost(settings.collapsedHeight.dp / 2f)
    }

    /** The accent in force right now, given the chosen source. */
    private fun resolveAccent(p: Presentation): Int = readableAccent(
        when (settings.accentSource) {
            ColorSource.ARTWORK -> p.accent
            ColorSource.MATERIAL_YOU -> systemAccent
            ColorSource.MANUAL -> settings.accentColor
        }
    )

    private fun resolvedBackground(): Int {
        val base = when (settings.backgroundSource) {
            ColorSource.MATERIAL_YOU -> systemSurface
            else -> settings.backgroundColor
        }
        return Color.argb(
            (Color.alpha(base) * settings.opacity.coerceIn(0f, 1f)).toInt(),
            Color.red(base), Color.green(base), Color.blue(base)
        )
    }

    private fun dur(base: Long): Long {
        if (systemAnimationScale <= 0f) return 0L
        val scaled = base / settings.animationSpeed.coerceIn(0.4f, 2.5f) * systemAnimationScale
        return scaled.toLong().coerceAtLeast(0L)
    }

    // ------------------------------------------------------------------ body builders

    private fun showsToggles(p: Presentation): Boolean = when (p.body) {
        is ExpandedBody.QuickPanel, is ExpandedBody.Media, is ExpandedBody.Charging -> true
        else -> false
    }

    /** Returns true when the panel was actually rebuilt. */
    private fun buildBody(p: Presentation, accent: Int): Boolean {
        val signature = bodyKey(p, accent)
        if (signature == bodySignature && bodyContainer.childCount > 0) return false
        bodySignature = signature
        bodyContainer.removeAllViews()
        mediaSeek = null; mediaPosition = null; mediaDuration = null; playPause = null
        timerText = null; timerRing = null
        when (val body = p.body) {
            is ExpandedBody.Media -> buildMediaBody(body.media, accent)
            is ExpandedBody.Notification -> buildNotificationBody(body.item, accent)
            is ExpandedBody.Charging -> buildChargingBody(body, accent)
            is ExpandedBody.Timer -> buildTimerBody(body, accent)
            is ExpandedBody.Message -> buildMessageBody(body)
            is ExpandedBody.Ongoing -> buildOngoingBody(body.item, accent)
            is ExpandedBody.Call -> buildCallBody(body.item, accent)
            is ExpandedBody.Stopwatch -> buildStopwatchBody(body, accent)
            is ExpandedBody.History -> buildHistoryBody(body.items, accent)
            ExpandedBody.QuickPanel -> buildQuickPanelBody(accent)
        }
        return true
    }

    /**
     * Identity of the panel's contents. Anything not in here is either animated in place
     * (progress, countdowns) or does not change what the panel looks like.
     */
    private fun bodyKey(p: Presentation, accent: Int): String = when (val body = p.body) {
        is ExpandedBody.Media -> with(body.media) {
            "media|$packageName|$title|$artist|$playing|$durationMs|$canSeek|$accent"
        }
        is ExpandedBody.Notification -> with(body.item) {
            "notification|$key|${actions.size}|${settings.quickReplyEnabled && reply != null}|" +
                "${settings.otpDetection && otp != null}|$accent"
        }
        is ExpandedBody.Charging -> "charging|${body.level}|${body.plugged}|${body.fast}|$accent"
        is ExpandedBody.Timer -> "timer|${body.running}|$accent"
        is ExpandedBody.Message -> "message|${body.title}|${body.subtitle}"
        is ExpandedBody.Ongoing -> with(body.item) {
            "ongoing|$key|$title|$text|$progress|$progressMax|$progressIndeterminate|$accent"
        }
        is ExpandedBody.Call -> "call|${body.item.key}|${body.item.actions.size}|$accent"
        is ExpandedBody.Stopwatch -> "stopwatch|${body.running}|${body.laps.size}|$accent"
        is ExpandedBody.History ->
            "history|${body.items.joinToString(",") { it.key }}|$accent"
        // The quick panel shows a clock, so it is allowed to go stale for at most a minute.
        ExpandedBody.QuickPanel ->
            "quick|$accent|${System.currentTimeMillis() / 60_000L}"
    }

    private fun bindMediaHeader(media: MediaSnapshot, accent: Int) {
        if (media.artwork != null) {
            headerArt.setImageBitmap(media.artwork)
            headerArt.clearColorFilter()
        } else {
            headerArt.setImageDrawable(media.appIcon ?: icon(R.drawable.ic_music))
            headerArt.clearColorFilter()
        }
        headerArt.visible(true)
        headerWave.barColor = accent
        headerWave.playing = media.playing
        headerWave.visible(true)
    }

    private fun bindNotificationHeader(item: NotificationItem) {
        if (item.largeIcon != null) {
            headerArt.setImageBitmap(item.largeIcon)
            headerArt.clearColorFilter()
        } else {
            headerArt.setImageDrawable(item.appIcon ?: item.smallIcon)
            headerArt.clearColorFilter()
        }
        headerArt.visible(true)
        headerWave.playing = false
        headerWave.visible(false)
    }

    private fun buildMediaBody(media: MediaSnapshot, accent: Int) {
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
                    if (fromUser) mediaPosition?.text = formatDuration(value.toLong())
                }
                override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true }
                override fun onStopTrackingTouch(sb: SeekBar) {
                    userSeeking = false
                    listener.onMediaSeek(sb.progress.toLong())
                }
            })
        }
        mediaSeek = seek
        bodyContainer.addView(
            seek,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val timeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val pos = smallLabel(formatDuration(media.positionMs))
        val dur = smallLabel(formatDuration(media.durationMs))
        mediaPosition = pos
        mediaDuration = dur
        timeRow.addView(pos)
        timeRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        timeRow.addView(dur)
        bodyContainer.addView(
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
        val prev = circleButton(R.drawable.ic_prev, 44.dp) { listener.onMediaCommand(MediaCommand.PREVIOUS) }
        prev.isEnabled = media.canSkipPrev
        val play = circleButton(
            if (media.playing) R.drawable.ic_pause else R.drawable.ic_play, 54.dp
        ) { listener.onMediaCommand(MediaCommand.PLAY_PAUSE) }
        (play.background as? GradientDrawable)?.setColor(accent)
        play.setColorFilter(Color.BLACK)
        playPause = play
        val next = circleButton(R.drawable.ic_next, 44.dp) { listener.onMediaCommand(MediaCommand.NEXT) }
        next.isEnabled = media.canSkipNext
        controls.addView(prev)
        controls.addView(spacer(18.dp))
        controls.addView(play)
        controls.addView(spacer(18.dp))
        controls.addView(next)
        bodyContainer.addView(controls)

        bodyContainer.addView(buildVolumeRow(accent))
    }

    private fun buildNotificationBody(item: NotificationItem, accent: Int) {
        if (item.text.isNotBlank()) {
            val text = TextView(context).apply {
                setTextColor(0xD9FFFFFF.toInt())
                textSize = 13f
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
                this.text = item.text
            }
            bodyContainer.addView(text)
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
        }
        actions.addView(pillButton("Open", accent, filled = true) { listener.onOpenPresentationTarget() })
        item.actions.take(2).forEach { action ->
            actions.addView(spacer(8.dp))
            actions.addView(pillButton(action.title, accent) { listener.onNotificationAction(action) })
        }
        actions.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(pillButton("Dismiss", accent) { listener.onDismissCurrent() })
        bodyContainer.addView(actions)
        addNotificationExtras(item, accent)
    }

    private fun buildChargingBody(body: ExpandedBody.Charging, accent: Int) {
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
        column.addView(smallLabel(
            if (body.plugged) "Battery at ${body.level}%" else "Running on battery"
        ))
        row.addView(ring)
        row.addView(column)
        bodyContainer.addView(row)
    }

    private fun buildTimerBody(body: ExpandedBody.Timer, accent: Int) {
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
        timerRing = ring
        val time = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = android.graphics.Typeface.MONOSPACE
            text = formatDuration(body.remainingMs, forceMinutes = true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 16.dp }
        }
        timerText = time
        row.addView(ring)
        row.addView(time)
        bodyContainer.addView(row)

        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
        }
        controls.addView(
            pillButton(if (body.running) "Pause" else "Resume", accent, filled = true) {
                listener.onTimerCommand(if (body.running) TimerCommand.PAUSE else TimerCommand.RESUME)
            }
        )
        controls.addView(spacer(8.dp))
        controls.addView(pillButton("+1 min", accent) { listener.onTimerCommand(TimerCommand.ADD_MINUTE) })
        controls.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(pillButton("Cancel", accent) { listener.onTimerCommand(TimerCommand.CANCEL) })
        bodyContainer.addView(controls)
    }

    private fun buildOngoingBody(item: NotificationItem, accent: Int) {
        if (item.text.isNotBlank()) {
            bodyContainer.addView(TextView(context).apply {
                setTextColor(0xD9FFFFFF.toInt())
                textSize = 13f
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                text = item.text
            })
        }
        if (item.hasProgress || item.progressIndeterminate) {
            bodyContainer.addView(
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
                bodyContainer.addView(smallLabel("$percent%"))
            }
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
        }
        actions.addView(pillButton("Open", accent, filled = true) { listener.onOpenPresentationTarget() })
        item.actions.take(2).forEach { action ->
            actions.addView(spacer(8.dp))
            actions.addView(pillButton(action.title, accent) { listener.onNotificationAction(action) })
        }
        bodyContainer.addView(actions)
    }

    private fun buildCallBody(item: NotificationItem, accent: Int) {
        if (item.text.isNotBlank()) {
            bodyContainer.addView(smallLabel(item.text))
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
                pillButton("Open call", accent, filled = true) { listener.onOpenPresentationTarget() }
            )
        } else {
            item.actions.take(3).forEachIndexed { index, action ->
                if (index > 0) actions.addView(spacer(8.dp))
                val declining = action.title.lowercase().let {
                    it.contains("decline") || it.contains("hang") || it.contains("end") ||
                        it.contains("reject")
                }
                actions.addView(
                    pillButton(
                        action.title,
                        if (declining) 0xFFFF453A.toInt() else 0xFF34C759.toInt(),
                        filled = true
                    ) { listener.onNotificationAction(action) }
                )
            }
        }
        bodyContainer.addView(actions)
    }

    private fun buildStopwatchBody(body: ExpandedBody.Stopwatch, accent: Int) {
        val time = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 34f
            typeface = android.graphics.Typeface.MONOSPACE
            text = formatStopwatch(body.elapsedMs)
        }
        stopwatchText = time
        bodyContainer.addView(time)

        if (body.laps.isNotEmpty()) {
            body.laps.takeLast(3).forEachIndexed { index, lap ->
                bodyContainer.addView(
                    smallLabel("Lap ${body.laps.size - index}   ${formatStopwatch(lap)}")
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
            pillButton(if (body.running) "Pause" else "Start", accent, filled = true) {
                listener.onStopwatchCommand(StopwatchCommand.START_PAUSE)
            }
        )
        controls.addView(spacer(8.dp))
        controls.addView(pillButton("Lap", accent) { listener.onStopwatchCommand(StopwatchCommand.LAP) })
        controls.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(
            pillButton("Reset", accent) { listener.onStopwatchCommand(StopwatchCommand.RESET) }
        )
        bodyContainer.addView(controls)
    }

    private fun buildHistoryBody(items: List<NotificationItem>, accent: Int) {
        if (items.isEmpty()) {
            bodyContainer.addView(smallLabel("Nothing has come through yet"))
            return
        }
        items.take(6).forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setPadding(0, 7.dp, 0, 7.dp)
                setOnClickListener { listener.onHistoryTap(item) }
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
            row.addView(smallLabel(formatRelative(System.currentTimeMillis(), item.whenMs)))
            bodyContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    /** The one-tap passcode copy and the inline reply field, when a notification offers them. */
    private fun addNotificationExtras(item: NotificationItem, accent: Int) {
        if (settings.otpDetection) item.otp?.let { code ->
            bodyContainer.addView(
                pillButton("Copy $code", accent, filled = true) { listener.onCopyCode(code) },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.dp }
            )
        }
        if (!settings.quickReplyEnabled) return
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
            setOnFocusChangeListener { _, hasFocus -> listener.onReplyFocusChanged(hasFocus) }
            setOnEditorActionListener { view, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendReply(item, view.text)
                    true
                } else {
                    false
                }
            }
        }
        replyField = field
        row.addView(field)
        row.addView(spacer(8.dp))
        row.addView(
            circleButton(R.drawable.ic_next, 40.dp) { sendReply(item, field.text) }.apply {
                (background as? GradientDrawable)?.setColor(accent)
                setColorFilter(Color.BLACK)
            }
        )
        bodyContainer.addView(row)
    }

    private fun sendReply(item: NotificationItem, text: CharSequence) {
        if (text.isBlank()) return
        listener.onSendReply(item, text.toString())
        replyField?.setText("")
        replyField?.clearFocus()
        listener.onReplyFocusChanged(false)
    }

    private fun buildMessageBody(body: ExpandedBody.Message) {
        bodyContainer.addView(TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            text = body.title
        })
        body.subtitle?.let { bodyContainer.addView(smallLabel(it)) }
    }

    private fun buildQuickPanelBody(accent: Int) {
        val now = Date()
        val clock = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            text = DateFormat.format(
                if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", now
            )
        }
        val date = smallLabel(DateFormat.format("EEEE, d MMMM", now).toString())
        bodyContainer.addView(clock)
        bodyContainer.addView(date)
        bodyContainer.addView(buildBrightnessRow(accent))
        bodyContainer.addView(buildVolumeRow(accent))
    }

    private fun buildVolumeRow(accent: Int): View {
        val (current, max) = listener.currentVolume()
        return sliderRow(R.drawable.ic_volume_up, current, max, accent) { listener.onVolumeChange(it) }
    }

    private fun buildBrightnessRow(accent: Int): View =
        sliderRow(R.drawable.ic_settings, listener.currentBrightness(), 255, accent) {
            listener.onBrightnessChange(it)
        }

    private fun sliderRow(
        iconRes: Int,
        value: Int,
        max: Int,
        accent: Int,
        onChange: (Int) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dp }
        }
        row.addView(ImageView(context).apply {
            setImageDrawable(icon(iconRes))
            setColorFilter(0xCCFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(18.dp, 18.dp)
        })
        row.addView(SeekBar(context).apply {
            this.max = max.coerceAtLeast(1)
            progress = value.coerceIn(0, max)
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x4DFFFFFF)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = 10.dp }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) onChange(p)
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        })
        return row
    }

    private fun buildToggles() {
        val entries = listOf(
            QuickToggle.TORCH to R.drawable.ic_torch,
            QuickToggle.WIFI to R.drawable.ic_wifi,
            QuickToggle.BLUETOOTH to R.drawable.ic_bluetooth,
            QuickToggle.DND to R.drawable.ic_dnd,
            QuickToggle.RINGER to R.drawable.ic_bell,
            QuickToggle.ROTATION to R.drawable.ic_rotate,
            QuickToggle.APP_SETTINGS to R.drawable.ic_settings,
        )
        entries.forEachIndexed { index, (toggle, iconRes) ->
            if (index > 0) togglesRow.addView(spacer(8.dp))
            val button = circleButton(iconRes, 38.dp) { listener.onQuickToggle(toggle) }
            toggleButtons[toggle] = button
            togglesRow.addView(button)
        }
    }

    // ------------------------------------------------------------------ small view helpers

    private fun icon(res: Int) = ContextCompat.getDrawable(context, res)

    private fun smallLabel(text: String) = TextView(context).apply {
        setTextColor(0x99FFFFFF.toInt())
        textSize = 11f
        this.text = text
    }

    private fun spacer(size: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(size, 1)
    }

    private fun circleButton(iconRes: Int, size: Int, onClick: () -> Unit): ImageView =
        ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageDrawable(icon(iconRes))
            setColorFilter(Color.WHITE)
            val pad = size / 4
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x1FFFFFFF)
            }
            isClickable = true
            setOnClickListener {
                animate().scaleX(0.86f).scaleY(0.86f).setDuration(90).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }.start()
                onClick()
            }
        }

    private fun pillButton(
        label: String,
        accent: Int,
        filled: Boolean = false,
        onClick: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        textSize = 12f
        setTextColor(if (filled) Color.BLACK else Color.WHITE)
        setPadding(14.dp, 8.dp, 14.dp, 8.dp)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = 16f.dp
            setColor(if (filled) accent else 0x1FFFFFFF)
        }
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun roundOutline(radius: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    // ------------------------------------------------------------------ gestures

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastTapTime = 0L
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        longPressFired = true
        listener.onGesture(settings.longPressAction)
    }

    /** While the reply field has focus this window owns the back key, so give it a job. */
    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && replyField?.hasFocus() == true) {
            if (event.action == KeyEvent.ACTION_UP) {
                replyField?.clearFocus()
                listener.onReplyFocusChanged(false)
            }
            return true
        }
        return super.dispatchKeyEventPreIme(event)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        mode != IslandMode.EXPANDED

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                downTime = System.currentTimeMillis()
                longPressFired = false
                postDelayed(longPressRunnable, 420)
                animate().scaleX(0.97f).scaleY(0.94f).setDuration(dur(120)).start()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > 16.dp || abs(event.rawY - downY) > 16.dp) {
                    removeCallbacks(longPressRunnable)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(dur(140)).start()
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(dur(160)).start()
                if (longPressFired) return true
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val threshold = 40.dp
                when {
                    abs(dy) > threshold && abs(dy) > abs(dx) ->
                        listener.onGesture(if (dy > 0) settings.swipeDownAction else settings.swipeUpAction)
                    abs(dx) > threshold ->
                        listener.onGesture(if (dx > 0) settings.swipeRightAction else settings.swipeLeftAction)
                    settings.doubleTapAction == GestureAction.NONE -> {
                        // Nothing to wait for, so the tap lands straight away.
                        listener.onGesture(settings.tapAction)
                    }
                    else -> {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < DOUBLE_TAP_WINDOW) {
                            lastTapTime = 0
                            removeCallbacks(singleTapRunnable)
                            listener.onGesture(settings.doubleTapAction)
                        } else {
                            lastTapTime = now
                            postDelayed(singleTapRunnable, DOUBLE_TAP_WINDOW)
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private val singleTapRunnable = Runnable { listener.onGesture(settings.tapAction) }

    companion object {
        private const val DOUBLE_TAP_WINDOW = 230L

        fun formatDuration(ms: Long, forceMinutes: Boolean = false): String =
            com.joyboard.notchisland.util.formatDuration(ms, forceMinutes)
    }
}
