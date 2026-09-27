package com.joyboard.notchisland.island

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.os.Build
import android.view.KeyEvent
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.ColorSource
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.util.DynamicColors
import com.joyboard.notchisland.util.dp
import kotlin.math.roundToInt
import com.joyboard.notchisland.util.formatStopwatch
import com.joyboard.notchisland.util.readableAccent
import com.joyboard.notchisland.util.visible
import kotlin.math.abs
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible

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
        /** The audio output picker, from the media panel. */
        fun onMediaOutput() = Unit
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
    private val androidSpring = PathInterpolator(0.22f, 1.12f, 0.36f, 1f)
    private val iosSpring = SpringInterpolator.island()
    private val iosSubtleSpring = SpringInterpolator.subtle()
    private val ease = PathInterpolator(0.33f, 0f, 0.1f, 1f)

    /** The real Dynamic Island's own motion, used verbatim when iOS mode is on. */
    private fun growInterpolator(): android.view.animation.Interpolator =
        if (settings.iosMode) iosSpring else androidSpring

    private fun shrinkInterpolator(): android.view.animation.Interpolator =
        if (settings.iosMode) iosSubtleSpring else ease

    // ------------------------------------------------------------------ views

    private val widgets = IslandWidgets(context)
    private val panels = IslandPanels(context, widgets, object : PanelHost {
        override val settings: IslandSettings get() = this@IslandView.settings
        override val listener: Listener get() = this@IslandView.listener
        override var userSeeking: Boolean
            get() = this@IslandView.userSeeking
            set(value) { this@IslandView.userSeeking = value }
        override fun onReplyFocus(hasFocus: Boolean) {
            listener.onReplyFocusChanged(hasFocus)
            setBackHandled(hasFocus)
        }
        override fun sendReply(item: NotificationItem, text: CharSequence) =
            this@IslandView.sendReply(item, text)
    })

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
        outlineProvider = widgets.roundOutline(5f.dp)
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

    /** A stand-in front camera, so the pill reads as hardware rather than a floating widget. */
    private val fauxCamera = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF0A0A0C.toInt())
            setStroke(1, 0xFF17171B.toInt())
        }
        visibility = View.GONE
    }

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
        outlineProvider = widgets.roundOutline(10f.dp)
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

    // live views inside the current panel, handed back by IslandPanels
    private var refs = PanelRefs()
    private var userSeeking = false


    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // A new activity arriving is worth hearing about, but never worth interrupting for.
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
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
        // Last child draws on top: the lens sits over the hole whatever else is showing.
        addView(
            fauxCamera,
            LayoutParams(11.dp, 11.dp, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
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
        applyHoleLayout(mode)
        if (s.iosMode) {
            // The real thing is pure black with no outline and no shadow behind it.
            bgDrawable.setColor(Color.BLACK)
            bgDrawable.setStroke(0, Color.TRANSPARENT)
        } else if (s.borderEnabled) {
            bgDrawable.setStroke(s.borderWidth.dp, s.borderColor)
        } else {
            bgDrawable.setStroke(0, Color.TRANSPARENT)
        }
        elevation = if (s.shadowEnabled && !s.iosMode) 10f.dp else 0f
        bgDrawable.cornerRadius = radiusFor(mode)
        invalidateOutline()
        requestLayout()
    }

    fun setPresentation(p: Presentation) {
        presentation = p
        refreshAccessibility()
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
                // The same resolved accent the open panel uses, so the bars do not change colour
                // when the island grows. (Charging green and low-battery red stay as they are.)
                trailingWave.barColor = resolveAccent(p)
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

        if (mode == IslandMode.PILL || mode == IslandMode.COMPACT) applyHoleLayout(mode)
        if (mode == IslandMode.EXPANDED || mode == IslandMode.MEDIUM) {
            togglesRow.visible(mode == IslandMode.EXPANDED && showsToggles(p))
            // Only re-measure and resize when the panel's contents actually changed.
            if (buildBody(p, accent)) resizeToMeasuredHeight()
            if (mode == IslandMode.EXPANDED) refreshToggleStates()
        }
    }

    /** Grows or shrinks the expanded panel to fit new content, without a full mode animation. */
    private fun resizeToMeasuredHeight() {
        val targetHeight = heightFor(mode, widthFor(mode))
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
        refreshAccessibility()
        val accent = resolveAccent(presentation)
        val opened = target == IslandMode.EXPANDED || target == IslandMode.MEDIUM
        if (opened) buildBody(presentation, accent)
        bodyContainer.visible(target == IslandMode.EXPANDED)
        togglesRow.visible(target == IslandMode.EXPANDED && showsToggles(presentation))
        if (target == IslandMode.EXPANDED) refreshToggleStates()

        applyHoleLayout(target)
        val targetWidth = widthFor(target)
        val targetHeight = heightFor(target, targetWidth)
        val startWidth = if (width == 0) targetWidth else width
        val startHeight = if (height == 0) targetHeight else height
        val startRadius = bgDrawable.cornerRadius
        val targetRadius = radiusFor(target)

        expandedRoot.visible(opened)
        compactRow.visible(target == IslandMode.COMPACT || target == IslandMode.PILL)
        // Bars only cost frames while they can actually be seen.
        trailingWave.visible(trailingWave.isVisible && target == IslandMode.COMPACT)
        headerWave.playing = headerWave.playing && target == IslandMode.EXPANDED

        compactRow.animate().alpha(if (target == IslandMode.COMPACT) 1f else 0f)
            .setDuration(dur(160)).start()
        expandedRoot.animate().alpha(if (opened) 1f else 0f)
            .setDuration(dur(if (opened) 220 else 120))
            .setStartDelay(if (opened) dur(70) else 0)
            .start()

        val growing = target.ordinal > previous.ordinal
        val interpolator = if (growing) growInterpolator() else shrinkInterpolator()
        sizeAnimator?.cancel()
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when {
                settings.iosMode && interpolator is SpringInterpolator ->
                    dur(interpolator.settleDurationMs)
                target == IslandMode.EXPANDED || previous == IslandMode.EXPANDED -> dur(420)
                else -> dur(300)
            }
            this.interpolator = interpolator
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

    // ------------------------------------------------------------------ the camera hole

    private var hole: Hole? = null
    private var holeDx = 0f
    private var holeDy = 0f

    /**
     * Tells the island where the phone's camera is, as an offset from the island's top centre —
     * the point it is anchored by, so the offset holds whatever size the island grows to.
     */
    fun setHole(hole: Hole?, dxFromIslandCenter: Float, dyFromIslandTop: Float) {
        this.hole = hole
        holeDx = dxFromIslandCenter
        holeDy = dyFromIslandTop
        cachedExpandedKey = null
        applyHoleLayout(mode)
    }

    /**
     * Treats the camera as hardware: content in every size is laid out around it, and the
     * optional lens is drawn exactly over it. Called before any size is measured, because the
     * clearance changes how tall the open panel needs to be.
     */
    private fun applyHoleLayout(target: IslandMode) {
        val density = resources.displayMetrics.density
        val widthDp = widthFor(target) / density
        val current = hole
        val local = current?.let { HoleGeometry.locate(holeDx, holeDy, it, widthDp) }

        when (target) {
            IslandMode.HIDDEN, IslandMode.PILL, IslandMode.COMPACT -> {
                val rowHeight = (if (target == IslandMode.COMPACT) settings.collapsedHeight + 4 else settings.collapsedHeight).toFloat()
                val clearance = if (settings.avoidHole && local != null) {
                    HoleGeometry.rowClearance(
                        local, widthDp, rowHeight,
                        basePadding = ROW_PADDING,
                        leadingWidth = LEADING_WIDTH,
                        trailingWidth = trailingWidthDp(),
                        margin = HOLE_MARGIN,
                    )
                } else {
                    Clearance.NONE
                }
                compactRow.setPadding(
                    dpPx(ROW_PADDING + clearance.start), 0, dpPx(ROW_PADDING + clearance.end), 0
                )
            }
            IslandMode.MEDIUM, IslandMode.EXPANDED -> {
                val clearance = if (settings.avoidHole && local != null) {
                    HoleGeometry.panelClearance(local, widthDp, PANEL_TALL, PANEL_PADDING_TOP, HOLE_MARGIN)
                } else {
                    Clearance.NONE
                }
                expandedRoot.setPadding(
                    16.dp, dpPx(PANEL_PADDING_TOP + clearance.top), 16.dp, 14.dp
                )
            }
        }

        // The lens only makes sense for a round hole that the island actually covers.
        val islandHeightDp = when (target) {
            IslandMode.MEDIUM, IslandMode.EXPANDED -> PANEL_TALL
            IslandMode.COMPACT -> settings.collapsedHeight + 4f
            else -> settings.collapsedHeight.toFloat()
        }
        val showLens = settings.showFauxCamera && current != null && current.isRound &&
            local != null && HoleGeometry.intersects(local, widthDp, islandHeightDp)
        fauxCamera.visible(showLens)
        if (showLens) {
            (fauxCamera.layoutParams as? LayoutParams)?.let { lp ->
                lp.width = dpPx(current.width)
                lp.height = dpPx(current.height)
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                // A horizontally centred FrameLayout child moves by exactly its left margin.
                lp.leftMargin = dpPx(holeDx)
                lp.rightMargin = 0
                lp.topMargin = dpPx(holeDy - current.height / 2f)
                fauxCamera.layoutParams = lp
            }
        }
    }

    private fun trailingWidthDp(): Float {
        val density = resources.displayMetrics.density
        val visible = listOf(trailingText, trailingIcon, trailingRing, trailingWave)
            .firstOrNull { it.isVisible } ?: return 0f
        visible.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        return visible.measuredWidth / density
    }

    private fun dpPx(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    /**
     * Re-asserts the size for the current mode if the view has drifted from it — a cancelled
     * animation or an interrupted layout can otherwise leave the island stuck open.
     */
    fun ensureSized(target: IslandMode) {
        if (sizeAnimator?.isRunning == true) return
        val lp = layoutParams ?: return
        val wantWidth = widthFor(target)
        val wantHeight = heightFor(target, wantWidth)
        if (lp.width == wantWidth && lp.height == wantHeight) return
        lp.width = wantWidth
        lp.height = wantHeight
        layoutParams = lp
        bgDrawable.cornerRadius = radiusFor(target)
        invalidateOutline()
    }

    /** Applies the target size immediately, used when the island is first attached. */
    fun snapToMode(target: IslandMode) {
        mode = target
        refreshAccessibility()
        val opened = target == IslandMode.EXPANDED || target == IslandMode.MEDIUM
        if (opened) {
            // Only the animated path used to build the panel, so snapping straight open — as a
            // rotation or theme change does — could leave an empty shell.
            buildBody(presentation, resolveAccent(presentation))
            togglesRow.visible(target == IslandMode.EXPANDED && showsToggles(presentation))
            if (target == IslandMode.EXPANDED) refreshToggleStates()
        }
        bodyContainer.visible(target == IslandMode.EXPANDED)
        applyHoleLayout(target)
        val w = widthFor(target)
        val h = heightFor(target, w)
        // The island lives inside whatever container the controller built, so stay generic.
        val lp = layoutParams ?: ViewGroup.LayoutParams(w, h)
        lp.width = w
        lp.height = h
        layoutParams = lp
        bgDrawable.cornerRadius = radiusFor(target)
        compactRow.alpha = if (target == IslandMode.COMPACT) 1f else 0f
        compactRow.visible(!opened)
        expandedRoot.alpha = if (opened) 1f else 0f
        expandedRoot.visible(opened)
        invalidateOutline()
    }

    fun refreshMediaProgress(positionMs: Long, durationMs: Long) {
        if (userSeeking) return
        val seek = refs.mediaSeek ?: return
        seek.max = durationMs.coerceAtLeast(1L).toInt()
        seek.progress = positionMs.coerceIn(0, durationMs.coerceAtLeast(1L)).toInt()
        refs.mediaPosition?.text = formatDuration(positionMs)
        refs.mediaDuration?.text = formatDuration(durationMs)
        refs.showLyricAt(positionMs)
    }

    fun refreshTimer(remainingMs: Long, totalMs: Long) {
        refs.timerText?.text = formatDuration(remainingMs, forceMinutes = true)
        refs.timerRing?.progress = if (totalMs <= 0) 0f else remainingMs.toFloat() / totalMs
    }

    fun refreshStopwatch(elapsedMs: Long) {
        refs.stopwatchText?.text = formatStopwatch(elapsedMs)
    }

    /** True while the reply field holds focus, so the controller keeps the window focusable. */
    fun isReplying(): Boolean = refs.replyField?.hasFocus() == true

    fun clearReplyFocus() {
        refs.replyField?.clearFocus()
        setBackHandled(false)
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
        IslandMode.MEDIUM -> settings.mediumWidth.dp
        IslandMode.EXPANDED -> settings.expandedWidth.dp
    }

    private fun heightFor(m: IslandMode, width: Int): Int = when (m) {
        IslandMode.HIDDEN, IslandMode.PILL -> settings.collapsedHeight.dp
        IslandMode.COMPACT -> (settings.collapsedHeight + 4).dp
        IslandMode.MEDIUM, IslandMode.EXPANDED -> measureExpandedHeight(width, m)
    }

    private fun measureExpandedHeight(width: Int, target: IslandMode): Int {
        val key = "$width|$target|$bodySignature|${togglesRow.visibility}|${expandedRoot.paddingTop}"
        if (key == cachedExpandedKey && cachedExpandedHeight > 0) return cachedExpandedHeight
        // The medium card is the header alone, so measure it with the body folded away.
        val bodyWas = bodyContainer.visibility
        val togglesWas = togglesRow.visibility
        if (target == IslandMode.MEDIUM) {
            bodyContainer.visibility = View.GONE
            togglesRow.visibility = View.GONE
        }
        expandedRoot.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val measured = expandedRoot.measuredHeight
        if (target == IslandMode.MEDIUM) {
            bodyContainer.visibility = bodyWas
            togglesRow.visibility = togglesWas
        }
        cachedExpandedKey = key
        cachedExpandedHeight = measured.coerceAtLeast(
            if (target == IslandMode.MEDIUM) 64.dp else 96.dp
        )
        return cachedExpandedHeight
    }

    private fun radiusFor(m: IslandMode): Float = when (m) {
        // iOS keeps a 44pt corner on the expanded island; ours follows when the preset does.
        IslandMode.EXPANDED -> if (settings.iosMode) 44f.dp else 28f.dp
        IslandMode.MEDIUM -> if (settings.iosMode) 32f.dp else 24f.dp
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
        if (settings.iosMode) return Color.BLACK
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
        if (signature == bodySignature && bodyContainer.isNotEmpty()) return false
        bodySignature = signature
        bodyContainer.removeAllViews()
        refs = panels.build(p.body, accent, bodyContainer)
        return true
    }


    /**
     * Identity of the panel's contents. Anything not in here is either animated in place
     * (progress, countdowns) or does not change what the panel looks like.
     */
    private fun bodyKey(p: Presentation, accent: Int): String = when (val body = p.body) {
        is ExpandedBody.Media -> with(body.media) {
            "media|$packageName|$title|$artist|$playing|$durationMs|$canSeek|$upNext|${lyrics?.size}|$accent"
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
            headerArt.setImageDrawable(media.appIcon ?: widgets.icon(R.drawable.ic_music))
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
            if (index > 0) togglesRow.addView(widgets.spacer(8.dp))
            val button = widgets.circleButton(iconRes, 38.dp, context.getString(toggle.label)) {
                listener.onQuickToggle(toggle)
            }
            toggleButtons[toggle] = button
            togglesRow.addView(button)
        }
    }

    private fun sendReply(item: NotificationItem, text: CharSequence) {
        if (text.isBlank()) return
        listener.onSendReply(item, text.toString())
        refs.replyField?.setText("")
        refs.replyField?.clearFocus()
        listener.onReplyFocusChanged(false)
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

    private var backCallback: Any? = null

    /**
     * Apps targeting Android 16 no longer receive KEYCODE_BACK — back arrives through
     * OnBackInvokedDispatcher instead, window by window. The overlay registers for it only while
     * the reply field is in use, so at every other moment back goes to whatever is underneath.
     */
    private fun setBackHandled(handled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = findOnBackInvokedDispatcher() ?: return
        val existing = backCallback as? OnBackInvokedCallback
        if (handled && existing == null) {
            val callback = OnBackInvokedCallback { closeReply() }
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
            )
            backCallback = callback
        } else if (!handled && existing != null) {
            dispatcher.unregisterOnBackInvokedCallback(existing)
            backCallback = null
        }
    }

    private fun closeReply() {
        refs.replyField?.clearFocus()
        listener.onReplyFocusChanged(false)
        setBackHandled(false)
    }

    /** Back on releases before the dispatcher existed, while the reply field has focus. */
    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && refs.replyField?.hasFocus() == true) {
            if (event.action == KeyEvent.ACTION_UP) closeReply()
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
                        performClick()
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

    private val singleTapRunnable = Runnable { performClick() }

    /**
     * A tap. Routed through here rather than straight to the listener so a screen reader's
     * "activate" does exactly what a finger does, and the click is reported to accessibility.
     */
    override fun performClick(): Boolean {
        super.performClick()
        listener.onGesture(settings.tapAction)
        return true
    }

    // ------------------------------------------------------------------ accessibility

    /**
     * Everything the island does is a gesture, which a screen reader cannot perform. So it also
     * says what it is showing, and offers the gestures as named actions.
     */
    private fun refreshAccessibility() {
        val p = presentation
        contentDescription = listOfNotNull(
            p.title?.takeIf { it.isNotBlank() } ?: context.getString(com.joyboard.notchisland.R.string.app_name),
            p.subtitle?.takeIf { it.isNotBlank() },
        ).joinToString(", ")
        ViewCompat.setStateDescription(
            this,
            when (mode) {
                IslandMode.EXPANDED -> context.getString(R.string.open)
                IslandMode.MEDIUM -> context.getString(R.string.partly_open)
                IslandMode.COMPACT -> context.getString(R.string.showing_activity)
                else -> context.getString(R.string.resting)
            }
        )

        val actions = buildList {
            if (mode != IslandMode.EXPANDED) add(context.getString(R.string.open) to GestureAction.EXPAND_FULL)
            if (mode == IslandMode.EXPANDED || mode == IslandMode.MEDIUM) add(context.getString(R.string.close) to GestureAction.COLLAPSE)
            if (p.body is ExpandedBody.Media) {
                add(context.getString(R.string.play_or_pause) to GestureAction.MEDIA_PLAY_PAUSE)
                add(context.getString(R.string.next_track) to GestureAction.MEDIA_NEXT)
                add(context.getString(R.string.previous_track) to GestureAction.MEDIA_PREVIOUS)
            }
        }
        accessibilityActionIds.forEach { ViewCompat.removeAccessibilityAction(this, it) }
        accessibilityActionIds = actions.map { (label, action) ->
            ViewCompat.addAccessibilityAction(this, label) { _, _ ->
                listener.onGesture(action)
                true
            }
        }

        // Resting and compact, the island is one thing to a screen reader; opened, its
        // buttons are reachable one by one.
        val opened = mode == IslandMode.EXPANDED || mode == IslandMode.MEDIUM
        compactRow.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        expandedRoot.importantForAccessibility =
            if (opened) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private var accessibilityActionIds: List<Int> = emptyList()

    companion object {
        private const val DOUBLE_TAP_WINDOW = 230L
        private const val ROW_PADDING = 12f
        private const val LEADING_WIDTH = 18f
        private const val PANEL_PADDING_TOP = 14f
        private const val HOLE_MARGIN = 6f
        /** The open panel is taller than any camera is deep, so treat it as unbounded. */
        private const val PANEL_TALL = 10_000f

        fun formatDuration(ms: Long, forceMinutes: Boolean = false): String =
            com.joyboard.notchisland.util.formatDuration(ms, forceMinutes)
    }
}
