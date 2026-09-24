package com.joyboard.notchisland.island

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.joyboard.notchisland.MainActivity
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.util.dp

/**
 * Owns the overlay window and decides what the island shows. Live activities compete by
 * [ActivityKind.priority]; the winner is rendered, everything else waits its turn.
 */
class IslandController(private val context: Context) : IslandView.Listener {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    private var settings = IslandSettings()
    private var root: LinearLayout? = null
    private var island: IslandView? = null
    private var touchStrip: FrameLayout? = null
    private var attached = false
    private var hiddenUntil = 0L
    private var screenOn = true

    private val activities = LinkedHashMap<ActivityKind, LiveActivity>()
    private var expandedByUser = false
    private var lastNotification: NotificationItem? = null

    private val haptics = Haptics(context)
    private val quickActions = QuickActions(context)
    private val mediaMonitor = MediaMonitor(context) { snapshot -> onMediaSnapshot(snapshot) }
    private val timer = TimerEngine(
        onTick = { remaining, total, running -> onTimerTick(remaining, total, running) },
        onFinished = { onTimerFinished() }
    )

    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var volumeMonitor: VolumeMonitor
    private lateinit var ringerMonitor: RingerMonitor
    private lateinit var screenMonitor: ScreenMonitor
    private lateinit var privacyMonitor: PrivacyMonitor

    private val expiryRunnable = Runnable { render() }
    private val collapseRunnable = Runnable {
        expandedByUser = false
        render()
    }
    private val mediaTicker = object : Runnable {
        override fun run() {
            val snapshot = mediaMonitor.snapshot()
            if (snapshot != null) {
                island?.refreshMediaProgress(mediaMonitor.position(), snapshot.durationMs)
            }
            handler.postDelayed(this, 500)
        }
    }

    // ------------------------------------------------------------------ lifecycle

    fun start(initial: IslandSettings) {
        settings = initial
        batteryMonitor = BatteryMonitor(context) { state, plugChanged -> onBattery(state, plugChanged) }
        volumeMonitor = VolumeMonitor(context) { stream, level, max -> onVolume(stream, level, max) }
        ringerMonitor = RingerMonitor(context) { mode -> onRinger(mode) }
        screenMonitor = ScreenMonitor(
            onUnlock = { onUnlock() },
            onScreenOff = { screenOn = false; updateVisibility() },
            onScreenOn = { screenOn = true; updateVisibility() },
            context = context,
        )
        privacyMonitor = PrivacyMonitor(context) { mic, camera -> onPrivacy(mic, camera) }

        attach()
        applySettings(initial)
        batteryMonitor.start()
        volumeMonitor.start()
        ringerMonitor.start()
        screenMonitor.start()
        if (settings.featurePrivacy) privacyMonitor.start()
        if (settings.featureMedia) mediaMonitor.start()
        render()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { batteryMonitor.stop() }
        runCatching { volumeMonitor.stop() }
        runCatching { ringerMonitor.stop() }
        runCatching { screenMonitor.stop() }
        runCatching { privacyMonitor.stop() }
        mediaMonitor.stop()
        quickActions.release()
        detach()
    }

    fun applySettings(next: IslandSettings) {
        val before = settings
        settings = next
        haptics.enabled = next.hapticsEnabled
        haptics.strength = next.hapticStrength
        island?.applySettings(next)
        refreshTouchStrip()
        updateWindowParams()
        if (before.featureMedia != next.featureMedia) {
            if (next.featureMedia) mediaMonitor.start() else {
                mediaMonitor.stop()
                activities.remove(ActivityKind.MEDIA)
            }
        }
        if (before.featurePrivacy != next.featurePrivacy) {
            if (next.featurePrivacy) privacyMonitor.start() else {
                privacyMonitor.stop()
                activities.remove(ActivityKind.PRIVACY)
            }
        }
        render()
    }

    fun onConfigurationChanged(configuration: Configuration) {
        updateVisibility()
        island?.let { view ->
            handler.post {
                // A wallpaper or theme change arrives as a configuration change, and that is
                // what moves the Material You palette, so re-read the colours here.
                view.applySettings(settings)
                refreshTouchStrip()
                view.snapToMode(view.mode)
            }
        }
    }

    private fun attach() {
        if (attached) return
        val view = IslandView(context, this)
        // The window wraps the island tightly, so anything landing in the container's padding or
        // in the touch strip was aimed at the island — forward it instead of dropping it.
        val container = object : LinearLayout(context) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (expandedByUser) {
                        expandedByUser = false
                        render()
                    }
                    return false
                }
                return view.onTouchEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            isClickable = true
        }

        val strip = FrameLayout(context).apply {
            addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = 2f.dp
                        setColor(0x4DFFFFFF)
                    }
                },
                FrameLayout.LayoutParams(26.dp, 3.dp, Gravity.CENTER)
            )
        }

        container.addView(
            view,
            LinearLayout.LayoutParams(settings.collapsedWidth.dp, settings.collapsedHeight.dp)
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        container.addView(
            strip,
            LinearLayout.LayoutParams(STRIP_WIDTH.dp, 0)
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        )

        runCatching {
            windowManager?.addView(container, buildParams())
            root = container
            island = view
            touchStrip = strip
            attached = true
            view.applySettings(settings)
            view.snapToMode(IslandMode.PILL)
            refreshTouchStrip()
        }
    }

    private fun detach() {
        val current = root ?: return
        runCatching { windowManager?.removeViewImmediate(current) }
        root = null
        island = null
        touchStrip = null
        attached = false
    }

    private fun buildParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = settings.offsetX.dp
            y = windowY()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

    /**
     * Overlay windows are always layered below the system status bar, and the status bar
     * consumes every touch inside its own band. Where the window starts therefore decides how
     * much of the island can be touched at all.
     */
    private fun windowY(): Int = when (settings.positionMode) {
        PositionMode.BELOW_STATUS_BAR -> settings.offsetY.dp + statusBarHeight()
        PositionMode.OVERLAP_STATUS_BAR -> settings.offsetY.dp
        PositionMode.CUSTOM -> settings.offsetY.dp
    }

    /** Height of the system status bar, which is the band where touches never reach us. */
    private fun statusBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = runCatching {
                windowManager?.currentWindowMetrics?.windowInsets
                    ?.getInsets(WindowInsets.Type.statusBars())?.top
            }.getOrNull()
            if (insets != null && insets > 0) return insets
        }
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val fromResources = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        return if (fromResources > 0) fromResources else 24.dp
    }

    /**
     * Sizes the transparent strip under the island. It is measured from the resting pill rather
     * than the live height, so expanding and collapsing never re-lays-out the window.
     */
    private fun refreshTouchStrip() {
        val container = root ?: return
        val strip = touchStrip ?: return
        val overlap = settings.positionMode == PositionMode.OVERLAP_STATUS_BAR
        val topPadding = if (overlap) 0 else TOUCH_PADDING.dp
        if (container.paddingTop != topPadding) {
            container.setPadding(TOUCH_PADDING.dp, topPadding, TOUCH_PADDING.dp, TOUCH_PADDING.dp)
        }
        val height = if (!overlap) 0 else {
            val islandBottom = windowY() + settings.collapsedHeight.dp
            (statusBarHeight() + settings.touchStripHeight.dp - islandBottom).coerceAtLeast(0)
        }
        val params = strip.layoutParams
        if (params.height != height) {
            params.height = height
            strip.layoutParams = params
        }
        strip.getChildAt(0)?.visibility =
            if (overlap && settings.showTouchHint && height > 6.dp) View.VISIBLE else View.INVISIBLE
    }

    private fun baseFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    private fun updateWindowParams() {
        val current = root ?: return
        val params = current.layoutParams as? WindowManager.LayoutParams ?: return
        val x = settings.offsetX.dp
        val y = windowY()
        var flags = baseFlags()
        val dim: Float
        if (settings.dimBackgroundWhenExpanded && island?.mode == IslandMode.EXPANDED) {
            flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            dim = 0.32f
        } else {
            dim = 0f
        }
        // updateViewLayout forces a relayout of the whole window, so only call it on a real change.
        if (params.x == x && params.y == y && params.flags == flags && params.dimAmount == dim) {
            return
        }
        params.x = x
        params.y = y
        params.flags = flags
        params.dimAmount = dim
        runCatching { windowManager?.updateViewLayout(current, params) }
    }

    private fun updateVisibility() {
        val view = root ?: return
        val landscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val lockedOut = !settings.showOnLockScreen && keyguard?.isKeyguardLocked == true
        val temporarilyHidden = SystemClock.elapsedRealtime() < hiddenUntil
        val hide = (settings.hideInLandscape && landscape) || lockedOut || !screenOn || temporarilyHidden
        view.visibility = if (hide) View.INVISIBLE else View.VISIBLE
    }

    // ------------------------------------------------------------------ activity feed

    fun push(activity: LiveActivity) {
        activities[activity.kind] = activity
        if (activity.autoExpand) expandedByUser = true
        render()
        haptics.tick()
    }

    fun drop(kind: ActivityKind) {
        if (activities.remove(kind) != null) render()
    }

    private fun onMediaSnapshot(snapshot: MediaSnapshot?) {
        if (!settings.featureMedia) return
        if (snapshot == null) {
            activities.remove(ActivityKind.MEDIA)
        } else {
            activities[ActivityKind.MEDIA] = LiveActivity(
                kind = ActivityKind.MEDIA,
                presentation = mediaPresentation(snapshot),
                expiresAt = Long.MAX_VALUE,
            )
        }
        render()
    }

    private fun mediaPresentation(snapshot: MediaSnapshot) = Presentation(
        kind = ActivityKind.MEDIA,
        leadingBitmap = snapshot.artwork,
        leadingIcon = snapshot.appIcon ?: drawable(R.drawable.ic_music),
        trailing = Trailing.Waveform(snapshot.playing, snapshot.accent),
        accent = snapshot.accent,
        title = snapshot.title,
        subtitle = "${snapshot.artist} · ${snapshot.appLabel}",
        body = ExpandedBody.Media(snapshot),
    )

    fun onNotification(item: NotificationItem) {
        if (!settings.featureNotifications) return
        if (item.packageName in settings.blockedPackages) return
        lastNotification = item
        val presentation = Presentation(
            kind = ActivityKind.NOTIFICATION,
            leadingIcon = item.appIcon ?: item.smallIcon,
            leadingBitmap = item.largeIcon,
            trailing = when (settings.notificationStyle) {
                com.joyboard.notchisland.data.NotificationStyle.PREVIEW ->
                    Trailing.Text(item.title.take(18), item.accent)
                com.joyboard.notchisland.data.NotificationStyle.MINIMAL ->
                    Trailing.Text(item.appLabel.take(14), item.accent)
                com.joyboard.notchisland.data.NotificationStyle.ICON_ONLY ->
                    Trailing.Icon(item.smallIcon, item.accent)
            },
            accent = item.accent,
            title = item.title.ifBlank { item.appLabel },
            subtitle = item.text.ifBlank { item.appLabel },
            body = ExpandedBody.Notification(item),
            tapIntent = item.contentIntent,
        )
        push(
            LiveActivity(
                kind = ActivityKind.NOTIFICATION,
                presentation = presentation,
                expiresAt = SystemClock.elapsedRealtime() + settings.notificationDurationMs,
            )
        )
    }

    private fun onBattery(state: BatteryState, plugChanged: Boolean) {
        if (state.level <= 15 && !state.plugged && settings.featureBatteryLow) {
            push(
                LiveActivity(
                    ActivityKind.BATTERY_LOW,
                    Presentation(
                        kind = ActivityKind.BATTERY_LOW,
                        leadingIcon = drawable(R.drawable.ic_battery_full),
                        leadingTint = 0xFFFF453A.toInt(),
                        trailing = Trailing.Ring(state.level / 100f, 0xFFFF453A.toInt()),
                        accent = 0xFFFF453A.toInt(),
                        title = "Low battery",
                        subtitle = "${state.level}% remaining",
                        body = ExpandedBody.Charging(state.level, state.plugged, state.fast),
                    ),
                    expiresAt = SystemClock.elapsedRealtime() + 5000,
                )
            )
            return
        }
        if (!plugChanged || !settings.featureCharging) {
            // keep the charging card fresh if it is already on screen
            activities[ActivityKind.CHARGING]?.let {
                activities[ActivityKind.CHARGING] = it.copy(
                    presentation = chargingPresentation(state)
                )
                render()
            }
            return
        }
        push(
            LiveActivity(
                ActivityKind.CHARGING,
                chargingPresentation(state),
                expiresAt = SystemClock.elapsedRealtime() + 4500,
            )
        )
    }

    private fun chargingPresentation(state: BatteryState) = Presentation(
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
            state.full -> "Fully charged"
            state.plugged && state.fast -> "Fast charging"
            state.plugged -> "Charging"
            else -> "Unplugged"
        },
        subtitle = "Battery ${state.level}%",
        body = ExpandedBody.Charging(state.level, state.plugged, state.fast),
    )

    private fun onVolume(stream: Int, level: Int, max: Int) {
        if (!settings.featureVolume) return
        val fraction = level.toFloat() / max.coerceAtLeast(1)
        push(
            LiveActivity(
                ActivityKind.VOLUME,
                Presentation(
                    kind = ActivityKind.VOLUME,
                    leadingIcon = drawable(
                        if (level == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up
                    ),
                    trailing = Trailing.Ring(fraction, settings.accentColor, "${(fraction * 100).toInt()}"),
                    accent = settings.accentColor,
                    title = if (stream == AudioManager.STREAM_MUSIC) "Media volume" else "Ring volume",
                    subtitle = "${(fraction * 100).toInt()}%",
                    body = ExpandedBody.QuickPanel,
                ),
                expiresAt = SystemClock.elapsedRealtime() + 1600,
            )
        )
    }

    private fun onRinger(mode: Int) {
        if (!settings.featureRinger) return
        val (iconRes, title) = when (mode) {
            AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_bell_off to "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_vibrate to "Vibrate"
            else -> R.drawable.ic_bell to "Ring"
        }
        push(
            LiveActivity(
                ActivityKind.RINGER,
                Presentation(
                    kind = ActivityKind.RINGER,
                    leadingIcon = drawable(iconRes),
                    trailing = Trailing.Text(title),
                    accent = settings.accentColor,
                    title = title,
                    subtitle = "Ringer mode",
                    body = ExpandedBody.Message(title, "Ringer mode changed"),
                ),
                expiresAt = SystemClock.elapsedRealtime() + 1500,
            )
        )
    }

    private fun onUnlock() {
        updateVisibility()
        if (!settings.featureUnlock) return
        push(
            LiveActivity(
                ActivityKind.UNLOCK,
                Presentation(
                    kind = ActivityKind.UNLOCK,
                    leadingIcon = drawable(R.drawable.ic_unlock),
                    leadingTint = 0xFF34C759.toInt(),
                    trailing = Trailing.Text("Unlocked", 0xFF34C759.toInt()),
                    accent = 0xFF34C759.toInt(),
                    title = "Unlocked",
                    subtitle = null,
                    body = ExpandedBody.Message("Unlocked", null),
                ),
                expiresAt = SystemClock.elapsedRealtime() + 1400,
            )
        )
    }

    private fun onPrivacy(mic: Boolean, camera: Boolean) {
        if (!settings.featurePrivacy) return
        if (!mic && !camera) {
            drop(ActivityKind.PRIVACY)
            return
        }
        val iconRes = if (camera) R.drawable.ic_camera else R.drawable.ic_mic
        val label = when {
            mic && camera -> "Camera and microphone in use"
            camera -> "Camera in use"
            else -> "Microphone in use"
        }
        push(
            LiveActivity(
                ActivityKind.PRIVACY,
                Presentation(
                    kind = ActivityKind.PRIVACY,
                    leadingIcon = drawable(iconRes),
                    leadingTint = 0xFF34C759.toInt(),
                    trailing = Trailing.Icon(drawable(iconRes), 0xFF34C759.toInt()),
                    accent = 0xFF34C759.toInt(),
                    title = label,
                    subtitle = "Privacy indicator",
                    body = ExpandedBody.Message(label, "An app is using a sensor right now"),
                ),
                expiresAt = SystemClock.elapsedRealtime() + 3000,
            )
        )
    }

    // ------------------------------------------------------------------ timer

    fun startTimer(durationMs: Long) {
        if (!settings.featureTimer) return
        timer.start(durationMs)
        expandedByUser = true
        render()
    }

    private fun onTimerTick(remaining: Long, total: Long, running: Boolean) {
        if (!timer.active) {
            activities.remove(ActivityKind.TIMER)
            render()
            return
        }
        activities[ActivityKind.TIMER] = LiveActivity(
            ActivityKind.TIMER,
            Presentation(
                kind = ActivityKind.TIMER,
                leadingIcon = drawable(R.drawable.ic_timer),
                leadingTint = settings.accentColor,
                trailing = Trailing.Text(IslandView.formatDuration(remaining, true), settings.accentColor),
                accent = settings.accentColor,
                title = "Timer",
                subtitle = IslandView.formatDuration(remaining, true),
                body = ExpandedBody.Timer(remaining, total, running),
            ),
            expiresAt = Long.MAX_VALUE,
        )
        val view = island
        if (view != null && view.mode == IslandMode.EXPANDED) {
            view.refreshTimer(remaining, total)
            updateCompactOnly()
        } else {
            render()
        }
    }

    private fun onTimerFinished() {
        haptics.pop()
        push(
            LiveActivity(
                ActivityKind.NOTIFICATION,
                Presentation(
                    kind = ActivityKind.NOTIFICATION,
                    leadingIcon = drawable(R.drawable.ic_timer),
                    leadingTint = settings.accentColor,
                    trailing = Trailing.Text("Done", settings.accentColor),
                    accent = settings.accentColor,
                    title = "Timer finished",
                    subtitle = null,
                    body = ExpandedBody.Message("Timer finished", null),
                ),
                expiresAt = SystemClock.elapsedRealtime() + 4000,
            )
        )
        activities.remove(ActivityKind.TIMER)
        render()
    }

    // ------------------------------------------------------------------ rendering

    private fun currentTop(): LiveActivity? {
        val now = SystemClock.elapsedRealtime()
        val expired = activities.filterValues { it.expiresAt <= now }.keys
        expired.forEach { activities.remove(it) }
        return activities.values.maxByOrNull { it.kind.priority }
    }

    private fun idlePresentation(): Presentation = Presentation(
        kind = ActivityKind.IDLE,
        leadingIcon = drawable(R.drawable.ic_island),
        trailing = Trailing.None,
        accent = settings.accentColor,
        title = "Notch Island",
        subtitle = null,
        body = ExpandedBody.QuickPanel,
    )

    private fun render() {
        val view = island ?: return
        handler.removeCallbacks(expiryRunnable)

        val top = currentTop()
        val presentation = top?.presentation ?: idlePresentation()
        view.setPresentation(presentation)

        val target = when {
            expandedByUser -> IslandMode.EXPANDED
            top != null -> IslandMode.COMPACT
            settings.alwaysShowPill -> IslandMode.PILL
            else -> IslandMode.HIDDEN
        }
        if (view.mode != target) {
            view.animateToMode(target)
            haptics.tick()
        }
        updateWindowParams()
        updateVisibility()

        // keep the media clock ticking only while it is visible
        handler.removeCallbacks(mediaTicker)
        if (target == IslandMode.EXPANDED && presentation.body is ExpandedBody.Media) {
            handler.post(mediaTicker)
        }

        // schedule the next expiry pass
        val now = SystemClock.elapsedRealtime()
        val nextExpiry = activities.values.map { it.expiresAt }.filter { it != Long.MAX_VALUE }.minOrNull()
        if (nextExpiry != null) {
            handler.postDelayed(expiryRunnable, (nextExpiry - now).coerceAtLeast(60L))
        }

        handler.removeCallbacks(collapseRunnable)
        if (expandedByUser && settings.autoCollapseSeconds > 0) {
            handler.postDelayed(collapseRunnable, settings.autoCollapseSeconds * 1000L)
        }
    }

    /** Refreshes compact content without restarting the size animation. */
    private fun updateCompactOnly() {
        val view = island ?: return
        val top = currentTop() ?: return
        view.setPresentation(top.presentation)
    }

    private fun drawable(res: Int): Drawable? = ContextCompat.getDrawable(context, res)

    private companion object {
        /** Slop around the island so a near miss still counts as a tap. */
        const val TOUCH_PADDING = 14

        /** Width of the transparent strip that catches taps in overlap mode. */
        const val STRIP_WIDTH = 96
    }

    // ------------------------------------------------------------------ IslandView.Listener

    override fun onRequestMode(mode: IslandMode) {
        expandedByUser = mode == IslandMode.EXPANDED
        render()
    }

    override fun onGesture(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.EXPAND -> {
                expandedByUser = true
                haptics.pop()
                render()
            }
            GestureAction.COLLAPSE -> {
                expandedByUser = false
                haptics.tick()
                render()
            }
            GestureAction.MEDIA_PLAY_PAUSE -> mediaMonitor.command(MediaCommand.PLAY_PAUSE)
            GestureAction.MEDIA_NEXT -> mediaMonitor.command(MediaCommand.NEXT)
            GestureAction.MEDIA_PREVIOUS -> mediaMonitor.command(MediaCommand.PREVIOUS)
            GestureAction.TOGGLE_TORCH -> {
                quickActions.perform(QuickToggle.TORCH)
                island?.refreshToggleStates()
            }
            GestureAction.TOGGLE_RINGER -> ringerMonitor.cycle()
            GestureAction.OPEN_SETTINGS -> openApp()
            GestureAction.OPEN_LAST_NOTIFICATION -> openPresentationTarget()
            GestureAction.SHOW_QUICK_PANEL -> {
                activities.remove(ActivityKind.NOTIFICATION)
                expandedByUser = true
                render()
            }
            GestureAction.HIDE_TEMPORARILY -> {
                hiddenUntil = SystemClock.elapsedRealtime() + 30_000
                updateVisibility()
                handler.postDelayed({ updateVisibility() }, 30_000)
            }
        }
    }

    override fun onMediaCommand(command: MediaCommand) {
        mediaMonitor.command(command)
        haptics.tick()
    }

    override fun onMediaSeek(positionMs: Long) = mediaMonitor.seekTo(positionMs)

    override fun onTimerCommand(command: TimerCommand) {
        when (command) {
            TimerCommand.PAUSE -> timer.pause()
            TimerCommand.RESUME -> timer.resume()
            TimerCommand.ADD_MINUTE -> timer.addMinute()
            TimerCommand.CANCEL -> {
                timer.cancel()
                activities.remove(ActivityKind.TIMER)
            }
        }
        render()
    }

    override fun onQuickToggle(toggle: QuickToggle) {
        val handled = quickActions.perform(toggle)
        haptics.tick()
        if (handled) {
            island?.refreshToggleStates()
        } else {
            expandedByUser = false
            render()
        }
    }

    override fun onVolumeChange(progress: Int) = volumeMonitor.setMusicVolume(progress)

    override fun onBrightnessChange(progress: Int) {
        if (!quickActions.setBrightness(progress)) {
            quickActions.perform(QuickToggle.SETTINGS)
        }
    }

    override fun onOpenPresentationTarget() = openPresentationTarget()

    override fun onNotificationAction(action: NotificationAction) {
        runCatching { action.intent?.send() }
        expandedByUser = false
        activities.remove(ActivityKind.NOTIFICATION)
        render()
    }

    override fun onDismissCurrent() {
        currentTop()?.let { activities.remove(it.kind) }
        expandedByUser = false
        render()
    }

    override fun quickToggleState(toggle: QuickToggle): Boolean = quickActions.state(toggle)

    override fun currentVolume(): Pair<Int, Int> = volumeMonitor.musicVolume()

    override fun currentBrightness(): Int = quickActions.brightness()

    private fun openPresentationTarget() {
        val top = currentTop()?.presentation
        val intent = top?.tapIntent ?: lastNotification?.contentIntent
        if (intent != null) {
            runCatching { intent.send() }
        } else {
            openApp()
        }
        expandedByUser = false
        activities.remove(ActivityKind.NOTIFICATION)
        render()
    }

    private fun openApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        expandedByUser = false
        render()
    }
}
