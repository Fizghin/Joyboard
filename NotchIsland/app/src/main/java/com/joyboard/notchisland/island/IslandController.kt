package com.joyboard.notchisland.island

import android.app.KeyguardManager
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.joyboard.notchisland.MainActivity
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.ColorSource
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.TapExpansion
import com.joyboard.notchisland.service.IslandBus
import com.joyboard.notchisland.service.NotchNotificationListener
import com.joyboard.notchisland.data.isQuietAt
import com.joyboard.notchisland.util.DynamicColors
import com.joyboard.notchisland.util.PendingIntents
import com.joyboard.notchisland.util.readableAccent

/**
 * Owns the overlay window and decides what the island shows. Live activities compete by
 * [ActivityKind.priority]; the winner is rendered, everything else waits its turn.
 */
class IslandController(private val context: Context) : IslandView.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    private var settings = IslandSettings()
    private val window = OverlayWindow(
        context,
        this,
        onOutsideTouch = {
            if (userStage != null) {
                userStage = null
                showingHistory = false
                render()
            }
        },
        onStatusBarVisibility = { visible ->
            if (fullscreen == visible) {
                fullscreen = !visible
                updateVisibility()
            }
        },
    )
    /** Some app is showing without the status bar: a video, a game, a reader. */
    private var fullscreen = false
    private val island: IslandView? get() = window.island
    private val presentations = Presentations(context, accent = { islandAccent() }, settings = { settings })
    private var hiddenUntil = 0L
    private var screenOn = true

    private val activities = ActivityQueue()
    private val history = ArrayDeque<NotificationItem>()
    /** The size the user has asked for, or null when the island is deciding for itself. */
    private var userStage: IslandMode? = null
    /** What the collapse timer was last armed for, so a busy activity cannot keep pushing it back. */
    private var collapseArmedFor: IslandMode? = null
    /** When an activity last genuinely changed, as opposed to refreshing its own contents. */
    private var lastActivityChangeAt = 0L
    private var showingHistory = false
    private var lastNotification: NotificationItem? = null

    private val haptics = Haptics(context)
    private val quickActions = QuickActions(context)
    private val mediaMonitor = MediaMonitor(context) { snapshot -> onMediaSnapshot(snapshot) }
    private val calendarMonitor = CalendarMonitor(context) { event -> onCalendar(event) }
    /** The event on the island, and whether its start has been announced, so each surfaces once. */
    private var calendarShownId: Long? = null
    private var calendarStartAnnounced = false
    private val stopwatch = StopwatchEngine { elapsed, running -> onStopwatchTick(elapsed, running) }
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
    private val settleRunnable = Runnable { render() }
    private val collapseRunnable = Runnable {
        if (island?.isReplying() == true) return@Runnable
        userStage = null
        showingHistory = false
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

    /** Whether the island is on screen at all. Read-only, for tests. */
    @VisibleForTesting
    internal val islandShowing: Boolean get() = window.isShowing

    /** The size the island is currently at. Read-only, for tests. */
    @VisibleForTesting
    internal val currentMode: IslandMode
        get() = island?.mode ?: IslandMode.HIDDEN

    // ------------------------------------------------------------------ lifecycle

    fun start(initial: IslandSettings) {
        settings = initial
        batteryMonitor = BatteryMonitor(context) { state, plugChanged -> onBattery(state, plugChanged) }
        volumeMonitor = VolumeMonitor(context) { stream, level, max -> onVolume(stream, level, max) }
        ringerMonitor = RingerMonitor(context) { mode -> onRinger(mode) }
        screenMonitor = ScreenMonitor(
            onUnlock = { onUnlock() },
            onScreenOff = { screenOn = false; onScreenPower(false) },
            onScreenOn = { screenOn = true; onScreenPower(true) },
            context = context,
        )
        privacyMonitor = PrivacyMonitor(context) { mic, camera -> onPrivacy(mic, camera) }

        window.attach(initial)
        applySettings(initial)
        batteryMonitor.start()
        volumeMonitor.start()
        ringerMonitor.start()
        screenMonitor.start()
        if (settings.featurePrivacy) privacyMonitor.start()
        if (settings.featureMedia) mediaMonitor.start()
        if (settings.featureCalendar) calendarMonitor.start(settings.calendarLeadMinutes)
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
        calendarMonitor.release()
        quickActions.release()
        window.detach()
    }

    fun applySettings(next: IslandSettings) {
        val before = settings
        settings = next
        haptics.enabled = next.hapticsEnabled
        haptics.strength = next.hapticStrength
        // Switching history off should forget what was kept, not just stop adding to it.
        if (!next.featureHistory) history.clear()
        window.applySettings(next)
        if (before.featureMedia != next.featureMedia) {
            if (next.featureMedia) mediaMonitor.start() else {
                mediaMonitor.stop()
                activities.remove(ActivityKind.MEDIA)
            }
        }
        if (next.featureCalendar) {
            // Also picks up a new lead time, and calendar access granted since the last start.
            calendarMonitor.start(next.calendarLeadMinutes)
        } else if (before.featureCalendar) {
            calendarMonitor.stop()
            activities.remove(ActivityKind.CALENDAR)
            calendarShownId = null
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
        window.invalidateInsets()
        updateVisibility()
        island?.let { view ->
            handler.post {
                // A wallpaper or theme change arrives as a configuration change, and that is
                // what moves the Material You palette, so re-read the colours here.
                window.applySettings(settings)
                view.snapToMode(view.mode)
            }
        }
    }

    /** With the screen off there is nothing to draw, so the pollers go quiet too. */
    private fun onScreenPower(on: Boolean) {
        updateVisibility()
        if (!settings.suspendWhenScreenOff) return
        if (on) {
            if (settings.featureMedia) mediaMonitor.start()
            if (settings.featurePrivacy) privacyMonitor.start()
        } else {
            handler.removeCallbacks(mediaTicker)
            mediaMonitor.stop()
            privacyMonitor.stop()
        }
    }

    /** The helper service saw the app in front, or the keyboard, change. */
    fun onContextChanged() = updateVisibility()

    private fun updateVisibility() {
        val now = java.util.Calendar.getInstance()
        val hide = VisibilityPolicy.shouldHide(
            VisibilityPolicy.Inputs(
                screenOn = screenOn,
                lockedOut = !settings.showOnLockScreen && keyguard?.isKeyguardLocked == true,
                temporarilyHidden = SystemClock.elapsedRealtime() < hiddenUntil,
                quiet = settings.isQuietAt(
                    now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                ),
                landscape = context.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE,
                hideInLandscape = settings.hideInLandscape,
                fullscreen = fullscreen,
                hideInFullscreen = settings.hideInFullscreen,
                keyboardVisible = IslandBus.keyboardVisible,
                hideWhileTyping = settings.hideWhileTyping,
                replying = island?.isReplying() == true,
                foregroundPackage = IslandBus.foregroundPackage,
                hiddenInPackages = settings.hiddenInPackages,
                urgent = currentTop()?.kind == ActivityKind.CALL,
            )
        )
        window.setVisible(!hide)
    }

    // ------------------------------------------------------------------ activity feed

    fun push(activity: LiveActivity) {
        markActivityChanged()
        activities.put(activity)
        if (activity.autoExpand) userStage = IslandMode.EXPANDED
        render()
        haptics.tick()
    }

    fun drop(kind: ActivityKind) {
        if (activities.remove(kind)) render()
    }

    private fun onMediaSnapshot(snapshot: MediaSnapshot?) {
        if (!settings.featureMedia) return
        if (snapshot == null) {
            activities.remove(ActivityKind.MEDIA)
        } else {
            val previous = activities.peek(ActivityKind.MEDIA)?.presentation
            // A new track is worth surfacing; a moving playhead is not.
            if (previous?.title != snapshot.title) markActivityChanged()
            activities.put(LiveActivity(
                kind = ActivityKind.MEDIA,
                presentation = presentations.media(snapshot),
                expiresAt = Long.MAX_VALUE,
            ))
        }
        render()
    }

    private fun onCalendar(event: CalendarEvent?) {
        if (!settings.featureCalendar || event == null) {
            calendarShownId = null
            if (activities.remove(ActivityKind.CALENDAR)) render()
            return
        }
        val now = System.currentTimeMillis()
        val started = CalendarPicker.minutesUntil(event, now) == 0
        // A new event surfaces once, and again when it begins; the minutes between just tick.
        if (event.id != calendarShownId) {
            calendarShownId = event.id
            calendarStartAnnounced = started
            markActivityChanged()
            haptics.tick()
        } else if (started && !calendarStartAnnounced) {
            calendarStartAnnounced = true
            markActivityChanged()
            haptics.pop()
        }
        activities.put(
            LiveActivity(
                kind = ActivityKind.CALENDAR,
                presentation = presentations.calendar(event, now),
                expiresAt = Long.MAX_VALUE,
            )
        )
        render()
    }

    fun onNotification(item: NotificationItem) {
        val kind = when {
            item.isCall && settings.featureCalls -> NotificationKind.CALL
            item.ongoing -> NotificationKind.ONGOING
            else -> NotificationKind.PREVIEW
        }
        val allowed = NotificationFilter.shouldShow(
            kind = kind,
            packageBlocked = item.packageName in settings.blockedPackages,
            importance = item.importance,
            passesDoNotDisturb = item.passesDoNotDisturb,
            ambient = item.ambient,
            showSilent = settings.silentNotifications,
            respectDoNotDisturb = settings.respectDoNotDisturb,
        )
        if (!allowed) return
        when (kind) {
            NotificationKind.CALL -> pushCall(item)
            NotificationKind.ONGOING -> pushOngoing(item)
            NotificationKind.PREVIEW -> if (settings.featureNotifications) pushNotification(item)
        }
    }

    /** An ongoing or call notification going away takes its activity with it. */
    fun onNotificationRemoved(key: String) {
        var changed = false
        if (activities.peek(ActivityKind.CALL)?.presentation?.notificationKey == key) {
            changed = activities.remove(ActivityKind.CALL)
        }
        if (activities.peek(ActivityKind.ONGOING)?.presentation?.notificationKey == key) {
            changed = activities.remove(ActivityKind.ONGOING) || changed
        }
        if (changed) render()
    }

    private fun pushCall(item: NotificationItem) {
        lastNotification = item
        if (activities.peek(ActivityKind.CALL)?.presentation?.notificationKey != item.key) {
            markActivityChanged()
        }
        activities.put(
            LiveActivity(
                kind = ActivityKind.CALL,
                presentation = presentations.call(item),
                // A call stays until its notification goes away.
                expiresAt = Long.MAX_VALUE,
                autoExpand = settings.autoExpandCalls,
            )
        )
        render()
        haptics.pop()
    }

    private fun pushOngoing(item: NotificationItem) {
        if (!settings.featureOngoing) return
        // A progress tick on the same notification is not news, so it must not reset the timer.
        if (activities.peek(ActivityKind.ONGOING)?.presentation?.notificationKey != item.key) {
            markActivityChanged()
        }
        activities.put(
            LiveActivity(
                kind = ActivityKind.ONGOING,
                presentation = presentations.ongoing(item),
                expiresAt = Long.MAX_VALUE,
            )
        )
        render()
    }

    private fun pushNotification(item: NotificationItem) {
        lastNotification = item
        if (settings.featureHistory) remember(item)
        val presentation = presentations.notification(item)
        // A passcode or a reply box is worth opening for; a plain alert is not.
        val worthExpanding = (item.otp != null && settings.autoExpandOtp) ||
            item.packageName in settings.autoExpandPackages
        push(
            LiveActivity(
                kind = ActivityKind.NOTIFICATION,
                presentation = presentation,
                expiresAt = SystemClock.elapsedRealtime() + settings.notificationDurationMs,
                autoExpand = worthExpanding,
            )
        )
    }

    private fun remember(item: NotificationItem) {
        history.removeAll { it.key == item.key }
        history.addFirst(item)
        while (history.size > HISTORY_LIMIT) history.removeLast()
    }

    private fun onBattery(state: BatteryState, plugChanged: Boolean) {
        if (state.level <= 15 && !state.plugged && settings.featureBatteryLow) {
            push(
                LiveActivity(
                    ActivityKind.BATTERY_LOW,
                    presentations.batteryLow(state),
                    expiresAt = SystemClock.elapsedRealtime() + 5000,
                )
            )
            return
        }
        if (!plugChanged || !settings.featureCharging) {
            // keep the charging card fresh if it is already on screen
            activities.peek(ActivityKind.CHARGING)?.let {
                activities.put(it.copy(presentation = presentations.charging(state)))
                render()
            }
            return
        }
        push(
            LiveActivity(
                ActivityKind.CHARGING,
                presentations.charging(state),
                expiresAt = SystemClock.elapsedRealtime() + 4500,
            )
        )
    }

    private fun onVolume(stream: Int, level: Int, max: Int) {
        if (!settings.featureVolume) return
        val fraction = level.toFloat() / max.coerceAtLeast(1)
        push(
            LiveActivity(
                ActivityKind.VOLUME,
                presentations.volume(stream, level, fraction),
                expiresAt = SystemClock.elapsedRealtime() + 1600,
            )
        )
    }

    private fun onRinger(mode: Int) {
        if (!settings.featureRinger) return
        val (iconRes, title) = when (mode) {
            AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_bell_off to context.getString(R.string.silent)
            AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_vibrate to context.getString(R.string.vibrate)
            else -> R.drawable.ic_bell to context.getString(R.string.ring)
        }
        push(
            LiveActivity(
                ActivityKind.RINGER,
                presentations.ringer(iconRes, title),
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
                presentations.unlocked(),
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
            mic && camera -> context.getString(R.string.camera_microphone_use)
            camera -> context.getString(R.string.camera_use)
            else -> context.getString(R.string.microphone_use)
        }
        push(
            LiveActivity(
                ActivityKind.PRIVACY,
                presentations.privacy(iconRes, label),
                expiresAt = SystemClock.elapsedRealtime() + 3000,
            )
        )
    }

    // ------------------------------------------------------------------ timer

    fun startTimer(durationMs: Long) {
        if (!settings.featureTimer) return
        markActivityChanged()
        timer.start(durationMs)
        userStage = IslandMode.EXPANDED
        render()
    }

    private fun onTimerTick(remaining: Long, total: Long, running: Boolean) {
        if (!timer.active) {
            activities.remove(ActivityKind.TIMER)
            render()
            return
        }
        activities.put(LiveActivity(
            ActivityKind.TIMER,
            presentations.timer(remaining, total, running),
            expiresAt = Long.MAX_VALUE,
        ))
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
                presentations.timerFinished(),
                expiresAt = SystemClock.elapsedRealtime() + 4000,
            )
        )
        activities.remove(ActivityKind.TIMER)
        render()
    }

    // ------------------------------------------------------------------ rendering

    private fun currentTop(): LiveActivity? = activities.top()

    /**
     * The island's own accent, resolved the way the view resolves it, for activities that have no
     * colour of their own — timers, volume, the ringer. Using the raw manual colour here made them
     * ignore a wallpaper-matched accent that the rest of the island was following.
     */
    private fun islandAccent(): Int = readableAccent(
        when (settings.accentSource) {
            ColorSource.MATERIAL_YOU -> DynamicColors.accent(context, dark = true)
            // Album art only exists for music; everything else falls back to the chosen colour.
            ColorSource.ARTWORK, ColorSource.MANUAL -> settings.accentColor
        }
    )

    private fun markActivityChanged() {
        lastActivityChangeAt = SystemClock.elapsedRealtime()
    }

    private fun render() = runCatching { renderInternal() }.getOrElse {
        // A render must never take the service down with it.
        android.util.Log.w("NotchIsland", "render failed", it)
    }

    private fun renderInternal() {
        val view = island ?: return
        handler.removeCallbacks(expiryRunnable)

        val top = currentTop()
        val presentation = when {
            showingHistory && userStage != null -> presentations.history(history.toList())
            else -> top?.presentation ?: presentations.idle()
        }
        view.setPresentation(presentation)

        val sinceChange = SystemClock.elapsedRealtime() - lastActivityChangeAt
        val restMs = settings.compactRestSeconds * 1000L
        val target = RestPolicy.target(
            userStage = userStage,
            hasActivity = top != null,
            msSinceActivityChange = sinceChange,
            stayCompact = settings.stayCompactForActivities,
            compactRestMs = restMs,
            alwaysShowPill = settings.alwaysShowPill,
        )
        if (view.mode != target) {
            view.animateToMode(target)
            haptics.tick()
        } else {
            // Guards against a cancelled animation leaving the island stuck at the wrong size.
            view.ensureSized(target)
        }
        window.updateParams()
        updateVisibility()

        // keep the media clock ticking only while it is visible
        handler.removeCallbacks(mediaTicker)
        if (target == IslandMode.EXPANDED && presentation.body is ExpandedBody.Media) {
            handler.post(mediaTicker)
        }

        // schedule the next expiry pass
        activities.millisUntilNextExpiry()?.let { delay ->
            handler.postDelayed(expiryRunnable, delay.coerceAtLeast(60L))
        }

        // and the moment the compact readout is due to settle back to the pill
        handler.removeCallbacks(settleRunnable)
        RestPolicy.millisUntilSettle(
            userStage = userStage,
            hasActivity = top != null,
            msSinceActivityChange = sinceChange,
            stayCompact = settings.stayCompactForActivities,
            compactRestMs = restMs,
        )?.let { handler.postDelayed(settleRunnable, it.coerceAtLeast(60L)) }

        // Arm the collapse once per stage. Re-arming on every render let a chatty activity —
        // a download ticking its progress, a track changing position — hold the island open.
        if (userStage != collapseArmedFor) {
            handler.removeCallbacks(collapseRunnable)
            collapseArmedFor = userStage
            if (userStage != null && settings.autoCollapseSeconds > 0) {
                handler.postDelayed(collapseRunnable, settings.autoCollapseSeconds * 1000L)
            }
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
        /** How many notifications the island remembers for its history panel. */
        const val HISTORY_LIMIT = 12
    }

    // ------------------------------------------------------------------ IslandView.Listener

    override fun onRequestMode(mode: IslandMode) {
        userStage = if (mode == IslandMode.PILL || mode == IslandMode.HIDDEN) null else mode
        render()
    }

    override fun onGesture(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> Unit
            // A tap either walks up the sizes or goes straight to the panel, per the setting.
            GestureAction.EXPAND -> {
                if (settings.tapExpansion == TapExpansion.STEP) stepUp() else openEverything()
                haptics.pop()
                render()
            }
            GestureAction.EXPAND_FULL -> {
                openEverything()
                haptics.pop()
                render()
            }
            GestureAction.COLLAPSE -> {
                userStage = null
                showingHistory = false
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
                openEverything()
                render()
            }
            GestureAction.SHOW_HISTORY -> {
                if (settings.featureHistory) {
                    showingHistory = true
                    userStage = IslandMode.EXPANDED
                    haptics.pop()
                    render()
                }
            }
            GestureAction.START_STOPWATCH -> startStopwatch()
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

    override fun onMediaOutput() {
        haptics.tick()
        // The picker opens over everything; the island folds away rather than sit on top of it.
        if (MediaOutput.show(context)) {
            userStage = null
            render()
        }
    }

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
            userStage = null
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
        action.intent?.let { PendingIntents.send(context, it) }
        userStage = null
        activities.remove(ActivityKind.NOTIFICATION)
        render()
    }

    override fun onDismissCurrent() {
        currentTop()?.let { activity ->
            activities.remove(activity.kind)
            // A dismissed notification should also stop nagging from the shade.
            activity.presentation.notificationKey?.let {
                NotchNotificationListener.instance?.dismiss(it)
            }
        }
        userStage = null
        showingHistory = false
        render()
    }

    override fun quickToggleState(toggle: QuickToggle): Boolean = quickActions.state(toggle)

    override fun currentVolume(): Pair<Int, Int> = volumeMonitor.musicVolume()

    override fun currentBrightness(): Int = quickActions.brightness()

    override fun onStopwatchCommand(command: StopwatchCommand) {
        when (command) {
            StopwatchCommand.START_PAUSE -> stopwatch.toggle()
            StopwatchCommand.LAP -> stopwatch.lap()
            StopwatchCommand.RESET -> {
                stopwatch.reset()
                activities.remove(ActivityKind.STOPWATCH)
            }
        }
        haptics.tick()
        render()
    }

    override fun onSendReply(item: NotificationItem, text: CharSequence) {
        val reply = item.reply ?: return
        val intent = reply.intent ?: return
        val results = Bundle().apply { putCharSequence(reply.resultKey, text) }
        val fill = Intent()
        RemoteInput.addResultsToIntent(reply.remoteInputs, fill, results)
        if (!PendingIntents.send(context, intent, fill)) toast(context.getString(R.string.could_not_send_reply))
        window.setFocusable(false)
        userStage = null
        activities.remove(ActivityKind.NOTIFICATION)
        haptics.pop()
        render()
    }

    override fun onReplyFocusChanged(active: Boolean) {
        window.setFocusable(active)
        if (active) {
            // Do not collapse the island out from under someone who is typing.
            handler.removeCallbacks(collapseRunnable)
        } else if (userStage != null && settings.autoCollapseSeconds > 0) {
            handler.postDelayed(collapseRunnable, settings.autoCollapseSeconds * 1000L)
        }
    }

    override fun onCopyCode(code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        runCatching {
            clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.passcode), code))
            toast(context.getString(R.string.copied, code))
        }
        haptics.pop()
        userStage = null
        activities.remove(ActivityKind.NOTIFICATION)
        render()
    }

    override fun onHistoryTap(item: NotificationItem) {
        item.contentIntent?.let { PendingIntents.send(context, it) }
        userStage = null
        showingHistory = false
        render()
    }

    private fun onStopwatchTick(elapsed: Long, running: Boolean) {
        if (!stopwatch.active) {
            activities.remove(ActivityKind.STOPWATCH)
            render()
            return
        }
        activities.put(
            LiveActivity(
                ActivityKind.STOPWATCH,
                presentations.stopwatch(elapsed, running, stopwatch.laps.toList()),
                expiresAt = Long.MAX_VALUE,
            )
        )
        val view = island
        if (view != null && view.mode == IslandMode.EXPANDED) {
            view.refreshStopwatch(elapsed)
            updateCompactOnly()
        } else {
            render()
        }
    }

    fun startStopwatch() {
        if (!settings.featureStopwatch) return
        markActivityChanged()
        stopwatch.start()
        userStage = IslandMode.EXPANDED
        render()
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    /**
     * One tap, one step: the resting pill opens to the compact preview, then to the small card,
     * then to everything, then back to rest. Where it starts depends on what the island is
     * already showing, so a tap during a notification opens that notification.
     */
    private fun stepUp() {
        val current = userStage ?: island?.mode ?: IslandMode.PILL
        userStage = ExpansionStepper.next(current)
        if (userStage == null) showingHistory = false
    }

    private fun openEverything() {
        userStage = IslandMode.EXPANDED
    }

    private fun openPresentationTarget() {
        val top = currentTop()?.presentation
        val intent = top?.tapIntent ?: lastNotification?.contentIntent
        if (intent == null || !PendingIntents.send(context, intent)) openApp()
        userStage = null
        activities.remove(ActivityKind.NOTIFICATION)
        render()
    }

    private fun openApp() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        userStage = null
        render()
    }
}
