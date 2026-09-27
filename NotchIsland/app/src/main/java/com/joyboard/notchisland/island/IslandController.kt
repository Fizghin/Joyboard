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
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.WindowManager
import com.joyboard.notchisland.BuildConfig
import com.joyboard.notchisland.data.SettingsRepository
import android.accessibilityservice.AccessibilityService
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private val lyrics = LyricsProvider()
    private val weatherMonitor = WeatherMonitor(onReport = { report -> onWeather(report) })
    private var weather: WeatherReport? = null
    /** The rain start last announced, so one shower is announced once. */
    private var rainAnnouncedFor = 0L
    private val automationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onAutomation(intent)
    }
    private var automationRegistered = false
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
    private val headphoneMonitor = HeadphoneMonitor(context) { name, connected -> onHeadphones(name, connected) }
    private val focusMonitor = FocusMonitor(context) { on -> onFocus(on) }
    private val networkMonitor = NetworkSpeedMonitor { reading -> onNetworkSpeed(reading) }
    private val heatWatch = HeatWatch()
    private val lowBattery = LowBatteryWatch()
    private val seenNotifications = SeenNotifications()
    private val alarm = TimerAlarm(context)
    private val connectivity = ConnectivityMonitor(
        context,
        onNetwork = { online, wifi -> onConnectivity(online, wifi) },
        onAirplane = { on -> onAirplane(on) },
    )
    /** Whether the phone was online at the last report; null until the first one. */
    private var online: Boolean? = null
    /** "No internet" was said, so "back online" is owed when it returns. */
    private var offlineAnnounced = false
    private val offlineCheck = Runnable {
        // Still offline after the grace period, and not because airplane mode was switched on.
        if (online == false && !connectivity.airplaneOn()) {
            offlineAnnounced = true
            push(LiveActivity(ActivityKind.CONNECTIVITY, presentations.offline(), SystemClock.elapsedRealtime() + 4_000))
        }
    }
    /** When the sleep timer stops the music, in epoch ms; null when it is off. */
    private var sleepAt: Long? = null
    private val sleepRunnable = Runnable { onSleepTimerDone() }
    /** When the island's own slider last moved the volume, so that is not announced back to it. */
    private var ownVolumeChangeAt: Long? = null
    /** For writing back a note ticked off on the island; cancelled when the island stops. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** A pinned note ticked off on the island, kept out until the setting catches up. */
    private var dismissedNote: String? = null
    /** The long-lived activity the person brought to the front from the bubble. */
    private var promoted: ActivityKind? = null
    /** What the bubble beside the island is showing right now. */
    private var secondaryKind: ActivityKind? = null
    private val alarms = context.getSystemService(android.app.AlarmManager::class.java)

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

        quickActions.onTorchChanged = { on -> onTorch(on) }
        window.attach(initial, accessibilityHost())
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
        headphoneMonitor.stop()
        focusMonitor.stop()
        networkMonitor.stop()
        alarm.stop()
        connectivity.stop()
        scope.cancel()
        quickActions.onTorchChanged = null
        lyrics.release()
        weatherMonitor.release()
        setAutomationListening(false)
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
        rehostIfNeeded()
        if (before.featureMedia != next.featureMedia) {
            if (next.featureMedia) mediaMonitor.start() else {
                mediaMonitor.stop()
                activities.remove(ActivityKind.MEDIA)
            }
        }
        setAutomationListening(next.externalApiEnabled)
        if (!next.externalApiEnabled) activities.remove(ActivityKind.EXTERNAL)
        if (next.featureCalendar) {
            // Also picks up a new lead time, and calendar access granted since the last start.
            calendarMonitor.start(next.calendarLeadMinutes)
        } else if (before.featureCalendar) {
            calendarMonitor.stop()
            activities.remove(ActivityKind.CALENDAR)
            calendarShownId = null
        }
        updateWeatherWatch()
        if (next.featureHeadphones) headphoneMonitor.start() else headphoneMonitor.stop()
        if (next.featureFocus) focusMonitor.start() else focusMonitor.stop()
        if (!next.featureNavigation) activities.remove(ActivityKind.NAVIGATION)
        updateNetworkWatch()
        applyNote()
        if (next.featureConnectivity) {
            connectivity.start()
        } else {
            connectivity.stop()
            online = null
            offlineAnnounced = false
            handler.removeCallbacks(offlineCheck)
            activities.remove(ActivityKind.CONNECTIVITY)
        }
        if (!next.featureFlashlight) activities.remove(ActivityKind.FLASHLIGHT)
        else if (quickActions.torchOn && activities.peek(ActivityKind.FLASHLIGHT) == null) onTorch(true)
        if (before.featureLyrics != next.featureLyrics && next.featureMedia) {
            onMediaSnapshot(mediaMonitor.snapshot())
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
            updateWeatherWatch()
            updateNetworkWatch()
        } else {
            handler.removeCallbacks(mediaTicker)
            mediaMonitor.stop()
            privacyMonitor.stop()
            weatherMonitor.stop()
            updateNetworkWatch()
        }
    }

    /** The helper service saw the app in front, the keyboard or the shade change. */
    fun onContextChanged() = updateVisibility()

    /** The helper connected or went away, so the island may move above or below the status bar. */
    fun onHelperChanged() = rehostIfNeeded()

    /** The helper's window manager, when the island should be drawn above the status bar. */
    private fun accessibilityHost(): WindowManager? =
        if (BuildConfig.HELPER_AVAILABLE && settings.drawAboveStatusBar) IslandBus.helperWindowManager else null

    /** Moves the island into the right kind of window, keeping what it is showing. */
    private fun rehostIfNeeded() {
        val wanted = accessibilityHost()
        if (window.isSettledFor(wanted)) return
        // The new window reports the status bar afresh; the old one's reading may not apply.
        fullscreen = false
        window.detach()
        window.attach(settings, wanted)
        window.applySettings(settings)
        render()
    }

    /** Whether the island is an accessibility overlay right now. Read-only, for tests. */
    @VisibleForTesting
    internal val windowType: Int? get() = window.windowType

    @VisibleForTesting
    internal val touchStripHeight: Int get() = window.touchStripHeight

    /** Stand-ins for the torch and audio callbacks, which tests cannot fire. */
    @VisibleForTesting
    internal fun torchChanged(on: Boolean) = onTorch(on)

    @VisibleForTesting
    internal fun headphonesChanged(name: String, connected: Boolean) = onHeadphones(name, connected)

    @VisibleForTesting
    internal val topKind: ActivityKind? get() = currentTop()?.kind

    @VisibleForTesting
    internal val secondaryShown: ActivityKind? get() = secondaryKind.takeIf { window.secondaryShowing }

    @VisibleForTesting
    internal fun tapSecondary() = window.secondaryView?.performClick()

    @VisibleForTesting
    internal fun networkSpeed(reading: SpeedReading) = onNetworkSpeed(reading)

    @VisibleForTesting
    internal fun connectivityChanged(online: Boolean, wifi: Boolean) = onConnectivity(online, wifi)

    @VisibleForTesting
    internal fun airplaneChanged(on: Boolean) = onAirplane(on)

    @VisibleForTesting
    internal val alarmRinging: Boolean get() = alarm.ringing

    @VisibleForTesting
    internal fun volumeChanged(stream: Int, level: Int, max: Int) = onVolume(stream, level, max)

    @VisibleForTesting
    internal fun batteryChanged(state: BatteryState, plugChanged: Boolean) = onBattery(state, plugChanged)

    @VisibleForTesting
    internal val statusBarProbe: android.view.View? get() = window.probe

    @VisibleForTesting
    internal val islandRootView: android.view.View? get() = window.rootView

    private fun updateVisibility() {
        val now = java.util.Calendar.getInstance()
        val reason = VisibilityPolicy.reason(
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
                // A call, or a timer ringing: both need seeing wherever you are.
                urgent = currentTop()?.kind.let { it == ActivityKind.CALL || it == ActivityKind.ALARM },
                shadeOpen = IslandBus.shadeOpen,
                aboveStatusBar = window.aboveStatusBar,
            )
        )
        IslandBus.hideReason = reason
        IslandBus.islandAboveStatusBar = window.aboveStatusBar
        window.setVisible(reason == null)
    }

    // ------------------------------------------------------------------ activity feed

    fun push(activity: LiveActivity) {
        arrivals++
        markActivityChanged()
        activities.put(activity)
        if (activity.autoExpand) userStage = IslandMode.EXPANDED
        render()
        haptics.tick()
        // Arrivals worth a flourish; a volume step or an unlock would make it tiresome.
        if (activity.kind in ANNOUNCED) island?.announce(activity.presentation.accent)
    }

    private fun onHeadphones(name: String, connected: Boolean) {
        if (!settings.featureHeadphones) return
        push(
            LiveActivity(
                ActivityKind.HEADPHONES,
                presentations.headphones(name, connected),
                expiresAt = SystemClock.elapsedRealtime() + 3_000,
            )
        )
    }

    private fun onFocus(on: Boolean) {
        if (!settings.featureFocus) return
        push(
            LiveActivity(
                ActivityKind.FOCUS,
                presentations.focus(on),
                expiresAt = SystemClock.elapsedRealtime() + 2_000,
            )
        )
    }

    /** The torch stays in the island while it is on, and leaves when it goes off. */
    private fun onTorch(on: Boolean) {
        if (!on || !settings.featureFlashlight) {
            if (activities.remove(ActivityKind.FLASHLIGHT)) render()
            return
        }
        if (activities.peek(ActivityKind.FLASHLIGHT) != null) return
        push(LiveActivity(ActivityKind.FLASHLIGHT, presentations.flashlight(), expiresAt = Long.MAX_VALUE))
    }

    override fun nextAlarm(): Long? {
        if (!settings.showNextAlarm) return null
        val at = runCatching { alarms?.nextAlarmClock?.triggerTime }.getOrNull() ?: return null
        val until = at - System.currentTimeMillis()
        return at.takeIf { until in 0..DAY_MS }
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
                presentation = presentations.media(withLyrics(snapshot)),
                expiresAt = Long.MAX_VALUE,
            ))
        }
        render()
    }

    /**
     * Listens for automation intents only while the setting is on and the island is running:
     * nothing is registered — and nothing can reach the island this way — the rest of the time.
     */
    private fun setAutomationListening(listen: Boolean) {
        if (listen == automationRegistered) return
        if (listen) {
            val filter = IntentFilter().apply {
                addAction(AutomationRequest.ACTION_SHOW)
                addAction(AutomationRequest.ACTION_DISMISS)
            }
            runCatching {
                ContextCompat.registerReceiver(
                    context, automationReceiver, filter, ContextCompat.RECEIVER_EXPORTED
                )
                automationRegistered = true
            }
        } else {
            runCatching { context.unregisterReceiver(automationReceiver) }
            automationRegistered = false
        }
    }

    @VisibleForTesting
    internal fun onAutomation(intent: Intent) {
        if (!settings.externalApiEnabled) return
        when (intent.action) {
            AutomationRequest.ACTION_DISMISS -> drop(ActivityKind.EXTERNAL)
            AutomationRequest.ACTION_SHOW -> {
                val extras = intent.extras ?: return
                @Suppress("DEPRECATION")
                val request = AutomationRequest.parse { key -> extras.get(key) } ?: return
                push(
                    LiveActivity(
                        kind = ActivityKind.EXTERNAL,
                        presentation = presentations.external(request),
                        expiresAt = SystemClock.elapsedRealtime() + request.durationMs,
                        autoExpand = request.expand,
                    )
                )
            }
        }
    }

    /** Fetches weather only while it is on, an area is known, and the screen is on. */
    private fun updateWeatherWatch() {
        val area = settings.weatherArea
        if (settings.featureWeather && Weather.parseArea(area) != null && screenOn) {
            weatherMonitor.start(area)
        } else {
            weatherMonitor.stop()
            if (!settings.featureWeather) {
                weather = null
                activities.remove(ActivityKind.WEATHER)
            }
        }
    }

    private fun onWeather(report: WeatherReport) {
        if (!settings.featureWeather) return
        weather = report
        val now = System.currentTimeMillis()
        val start = Weather.rainStart(report, now)
        // A forecast refreshed mid-shower nudges the start time; that is still the same rain.
        if (settings.rainAlerts && start != null && kotlin.math.abs(start - rainAnnouncedFor) > RAIN_SAME_SHOWER_MS) {
            rainAnnouncedFor = start
            push(
                LiveActivity(
                    kind = ActivityKind.WEATHER,
                    presentation = presentations.rainSoon(start, now),
                    expiresAt = SystemClock.elapsedRealtime() + 6_000,
                )
            )
        } else {
            // The quick panel shows the conditions, so an open one should pick them up.
            render()
        }
    }

    override fun currentWeather(): WeatherReport? = weather.takeIf { settings.featureWeather }

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
            handler.post { island?.announce(islandAccent()) }
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

    /**
     * Adds lyrics to the snapshot when they are on and already known; otherwise asks for them,
     * and the answer comes back as a fresh snapshot for the same song.
     */
    private fun withLyrics(snapshot: MediaSnapshot): MediaSnapshot {
        if (!settings.featureLyrics) return snapshot
        val song = LyricsProvider.Song.of(snapshot) ?: return snapshot
        val lines = lyrics.cached(song)
        if (lines == null) {
            lyrics.request(song) { found ->
                val now = mediaMonitor.snapshot()
                if (now != null && LyricsProvider.Song.of(now)?.key == found.key) onMediaSnapshot(now)
            }
        }
        return if (lines.isNullOrEmpty()) snapshot else snapshot.copy(lyrics = lines)
    }

    fun onNotification(item: NotificationItem) {
        // A player's own notification: its media session already puts it on the island, with
        // controls. Letting it through as well made it outrank now playing as a plain card.
        if (item.media) return
        val kind = when {
            item.isCall && settings.featureCalls -> NotificationKind.CALL
            (item.ongoing || item.isNavigation) && item.isLiveOngoing -> NotificationKind.ONGOING
            // An app saying it is running — a VPN, a tracker, a sync — is not news, and would
            // otherwise hold the island for as long as the app runs.
            item.ongoing -> {
                dropOngoing(item.key)
                return
            }
            else -> NotificationKind.PREVIEW
        }
        // An ongoing notification updated into something else — a download that finished —
        // takes its live activity with it.
        if (kind != NotificationKind.ONGOING) dropOngoing(item.key)
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
        seenNotifications.forget(key)
        var changed = false
        if (activities.peek(ActivityKind.CALL)?.presentation?.notificationKey == key) {
            changed = activities.remove(ActivityKind.CALL)
        }
        if (activities.peek(ActivityKind.ONGOING)?.presentation?.notificationKey == key) {
            changed = activities.remove(ActivityKind.ONGOING) || changed
        }
        if (activities.peek(ActivityKind.NAVIGATION)?.presentation?.notificationKey == key) {
            changed = activities.remove(ActivityKind.NAVIGATION) || changed
        }
        if (changed) render()
    }

    /** Removes a live activity backed by [key], when the notification stops being one. */
    private fun dropOngoing(key: String) {
        var changed = false
        for (kind in listOf(ActivityKind.ONGOING, ActivityKind.NAVIGATION)) {
            if (activities.peek(kind)?.presentation?.notificationKey == key) changed = activities.remove(kind) || changed
        }
        if (changed) render()
    }

    private fun pushCall(item: NotificationItem) {
        lastNotification = item
        // The dialer re-posts its notification as the call goes on; only a new call is news.
        val newCall = activities.peek(ActivityKind.CALL)?.presentation?.notificationKey != item.key
        if (newCall) markActivityChanged()
        activities.put(
            LiveActivity(
                kind = ActivityKind.CALL,
                presentation = presentations.call(item),
                // A call stays until its notification goes away.
                expiresAt = Long.MAX_VALUE,
                autoExpand = settings.autoExpandCalls,
            )
        )
        // Placed straight in the queue rather than pushed, so the arrival is handled here: before,
        // "open the island for calls" was never applied and a ringing call only showed compact.
        if (newCall) {
            arrivals++
            if (settings.autoExpandCalls) userStage = IslandMode.EXPANDED
        }
        render()
        if (newCall) {
            haptics.pop()
            island?.announce(presentations.call(item).accent)
        }
    }

    private fun pushOngoing(item: NotificationItem) {
        if (item.isNavigation && settings.featureNavigation) {
            pushNavigation(item)
            return
        }
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

    /** Directions stay while the route does; a new turn is news, the distance ticking down is not. */
    private fun pushNavigation(item: NotificationItem) {
        val presentation = presentations.navigation(item)
        val previous = activities.peek(ActivityKind.NAVIGATION)?.presentation
        if (previous?.notificationKey != item.key || previous.title != presentation.title) markActivityChanged()
        activities.put(LiveActivity(ActivityKind.NAVIGATION, presentation, expiresAt = Long.MAX_VALUE))
        render()
    }

    private fun pushNotification(item: NotificationItem) {
        lastNotification = item
        if (settings.featureHistory) remember(item)
        if (!seenNotifications.shouldAnnounce(item.key, item.title, item.text, item.alertOnce)) {
            // Updated in place with nothing new: refresh it if it is on screen, quietly.
            activities.peek(ActivityKind.NOTIFICATION)
                ?.takeIf { it.presentation.notificationKey == item.key }
                ?.let {
                    activities.put(it.copy(presentation = presentations.notification(item)))
                    render()
                }
            return
        }
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
        if (settings.featureBatteryHeat && heatWatch.update(state.temperatureC)) {
            push(
                LiveActivity(
                    ActivityKind.BATTERY_HOT,
                    presentations.batteryHot(state),
                    expiresAt = SystemClock.elapsedRealtime() + 6_000,
                )
            )
        }
        if (lowBattery.update(state.level, state.plugged) && settings.featureBatteryLow) {
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
        if (stream == AudioManager.STREAM_MUSIC) {
            // The island's own slider moved it, and the slider already shows where it is.
            val own = ownVolumeChangeAt
            if (own != null && SystemClock.elapsedRealtime() - own < OWN_VOLUME_WINDOW_MS) return
            // An open panel with a volume slider — music, the resting panel — moves its slider
            // rather than being swapped for the volume readout.
            val view = island
            if (view != null && view.mode == IslandMode.EXPANDED && view.refreshVolume(level, max)) return
        }
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

    /** Samples transfer speed only while it is switched on and the screen is on. */
    private fun updateNetworkWatch() {
        if (settings.featureNetworkSpeed && screenOn) {
            networkMonitor.start()
        } else {
            networkMonitor.stop()
            if (activities.remove(ActivityKind.NETWORK)) render()
        }
    }

    private fun onNetworkSpeed(reading: SpeedReading) {
        if (!settings.featureNetworkSpeed) return
        if (!reading.active) {
            if (activities.remove(ActivityKind.NETWORK)) render()
            return
        }
        if (activities.peek(ActivityKind.NETWORK) == null) markActivityChanged()
        activities.put(LiveActivity(ActivityKind.NETWORK, presentations.network(reading), expiresAt = Long.MAX_VALUE))
        render()
    }

    /** The pinned note is on the island for as long as the setting holds it. */
    private fun applyNote() {
        val note = settings.pinnedNote.trim()
        if (note != dismissedNote) dismissedNote = null
        if (note.isEmpty() || note == dismissedNote) {
            activities.remove(ActivityKind.NOTE)
            return
        }
        if (activities.peek(ActivityKind.NOTE)?.presentation?.subtitle == note) return
        markActivityChanged()
        activities.put(LiveActivity(ActivityKind.NOTE, presentations.note(note), expiresAt = Long.MAX_VALUE))
    }

    /** Ticks the note off: gone from the island at once, and from the setting as soon as it is written. */
    private fun finishNote() {
        val done = settings.pinnedNote.trim()
        dismissedNote = done
        activities.remove(ActivityKind.NOTE)
        userStage = null
        haptics.pop()
        render()
        scope.launch {
            runCatching {
                // Only if it is still the same note: a new one written meanwhile stays.
                SettingsRepository.get(context).update { if (it.pinnedNote.trim() == done) it.copy(pinnedNote = "") else it }
            }
        }
    }

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
        val before = activities.peek(ActivityKind.TIMER)?.presentation?.body as? ExpandedBody.Timer
        onClockTick(
            LiveActivity(ActivityKind.TIMER, presentations.timer(remaining, total, running), expiresAt = Long.MAX_VALUE),
            changed = before == null || before.running != running,
        ) { it.refreshTimer(remaining, total) }
    }

    /**
     * A clock ticking. It keeps its activity current, but redraws only what can be seen: the
     * digits of an open panel, or the compact readout. The full render — sizes, rest timers, the
     * window — is for when something about the clock changes (started, paused, a lap), not for
     * every tick; a stopwatch otherwise re-laid-out the whole window sixteen times a second, even
     * while the island rested as a bare pill.
     */
    private fun onClockTick(activity: LiveActivity, changed: Boolean, refreshOpen: (IslandView) -> Unit) {
        activities.put(activity)
        val view = island
        when {
            view == null -> Unit
            changed -> render()
            // In the bubble, or behind something else: nothing of it is on screen to move.
            currentTop()?.kind != activity.kind -> Unit
            view.mode == IslandMode.EXPANDED -> refreshOpen(view)
            view.mode == IslandMode.COMPACT || view.mode == IslandMode.MEDIUM -> updateCompactOnly()
            // Resting as a pill: the digits are not shown.
            else -> Unit
        }
    }

    private fun onTimerFinished() {
        haptics.pop()
        activities.remove(ActivityKind.TIMER)
        val ringing = settings.timerAlarm
        if (ringing) alarm.start()
        push(
            LiveActivity(
                if (ringing) ActivityKind.ALARM else ActivityKind.NOTIFICATION,
                presentations.timerFinished(timer.totalMs, ringing),
                // Ringing, it stays as long as the ringing does; silent, it passes.
                expiresAt = SystemClock.elapsedRealtime() + if (ringing) TimerAlarm.LIMIT_MS else 4_000,
                autoExpand = ringing,
            )
        )
    }

    /** Silences a finished timer and takes it off the island. */
    private fun stopAlarm() {
        alarm.stop()
        activities.remove(ActivityKind.ALARM)
        if (activities.peek(ActivityKind.NOTIFICATION)?.presentation?.body is ExpandedBody.TimerDone) {
            activities.remove(ActivityKind.NOTIFICATION)
        }
        userStage = null
    }

    // ------------------------------------------------------------------ connectivity

    /**
     * Offline is only said after a few seconds of it — switching from Wi-Fi to mobile data drops
     * the connection for a moment, and that is not news — and "back online" only after it was.
     */
    private fun onConnectivity(nowOnline: Boolean, wifi: Boolean) {
        if (!settings.featureConnectivity) return
        val was = online
        online = nowOnline
        if (was == null || was == nowOnline) return
        if (!nowOnline) {
            handler.removeCallbacks(offlineCheck)
            handler.postDelayed(offlineCheck, OFFLINE_GRACE_MS)
            return
        }
        handler.removeCallbacks(offlineCheck)
        if (offlineAnnounced) {
            offlineAnnounced = false
            push(LiveActivity(ActivityKind.CONNECTIVITY, presentations.online(wifi), SystemClock.elapsedRealtime() + 2_500))
        }
    }

    private fun onAirplane(on: Boolean) {
        if (!settings.featureConnectivity) return
        // Airplane mode explains the silence; it does not need "no internet" said as well.
        handler.removeCallbacks(offlineCheck)
        push(LiveActivity(ActivityKind.CONNECTIVITY, presentations.airplane(on), SystemClock.elapsedRealtime() + 2_000))
    }

    // ------------------------------------------------------------------ sleep timer

    override fun sleepEndsAt(): Long? = sleepAt

    override fun onSleepTimer() {
        haptics.tick()
        val now = System.currentTimeMillis()
        // The next length up from what is left, rounded to the step; past the longest, off.
        // Rounded up, so a second tap straight after the first goes on to 30 rather than
        // finding 14 minutes and some seconds left and setting 15 again.
        val leftMin = sleepAt?.let { ((it - now + 59_999L) / 60_000L).toInt() }
        val next = SLEEP_STEPS_MIN.firstOrNull { leftMin == null || it > leftMin }
        handler.removeCallbacks(sleepRunnable)
        sleepAt = next?.let { now + it * 60_000L }
        next?.let { handler.postDelayed(sleepRunnable, it * 60_000L) }
        render()
    }

    private fun onSleepTimerDone() {
        sleepAt = null
        // Only pause: a player already stopped is left alone rather than started again.
        if (mediaMonitor.snapshot()?.playing == true) mediaMonitor.command(MediaCommand.PLAY_PAUSE)
        render()
    }

    // ------------------------------------------------------------------ rendering

    /**
     * What owns the island. By priority, except that among the long-lived activities the one the
     * person brought forward from the bubble stays in front. Something passing through — a
     * notification, a volume step — still takes over, and a call always wins.
     */
    private fun currentTop(): LiveActivity? {
        val top = activities.top() ?: return null
        val preferred = promoted?.let { activities.peek(it) }
        if (preferred == null) {
            promoted = null
            return top
        }
        return if (top.longLived && top.kind != ActivityKind.CALL) preferred else top
    }

    /** The long-lived activity that goes in the bubble beside [shown], if there is one. */
    private fun secondaryFor(shown: LiveActivity?): LiveActivity? {
        if (shown == null || !shown.longLived) return null
        return activities.live()
            .filter { it.longLived && it.kind != shown.kind }
            .maxByOrNull { it.kind.priority }
    }

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

    private fun render() = runCatching { renders++; renderInternal() }.getOrElse {
        // A render must never take the service down with it.
        renderFailures++
        android.util.Log.w("NotchIsland", "render failed", it)
    }

    /** Activities pushed with a fresh arrival — haptic, bounce and all. For tests. */
    @VisibleForTesting
    internal var arrivals = 0
        private set

    /** Full renders so far, for tests that check the island is not redrawing for nothing. */
    @VisibleForTesting
    internal var renders = 0
        private set

    /** Renders that threw and were swallowed. Always zero unless something is broken; for tests. */
    @VisibleForTesting
    internal var renderFailures = 0
        private set

    private fun renderInternal() {
        // However the ringing timer left — stopped, dismissed, timed out — its sound goes with it.
        if (alarm.ringing && activities.peek(ActivityKind.ALARM) == null) alarm.stop()
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
        // Two long-lived activities at once: the second sits in a bubble beside the compact island.
        val second = if (settings.splitIsland && target == IslandMode.COMPACT && !showingHistory) {
            secondaryFor(top)
        } else {
            null
        }
        secondaryKind = second?.kind
        window.setSecondary(second?.presentation)
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

        /** Arrivals that get the bounce and edge glow. */
        val ANNOUNCED = setOf(
            ActivityKind.NOTIFICATION, ActivityKind.CALL, ActivityKind.CHARGING,
            ActivityKind.BATTERY_LOW, ActivityKind.EXTERNAL, ActivityKind.WEATHER,
            ActivityKind.CALENDAR, ActivityKind.HEADPHONES, ActivityKind.FOCUS,
            ActivityKind.FLASHLIGHT, ActivityKind.NAVIGATION, ActivityKind.BATTERY_HOT,
        )

        const val DAY_MS = 24 * 60 * 60_000L

        /** AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, which the island waits a moment for. */
        const val SCREENSHOT_ACTION = 9

        /** How long the connection has to stay gone before "no internet" is said. */
        const val OFFLINE_GRACE_MS = 5_000L

        /** The sleep timer's lengths, in minutes, stepped through a tap at a time. */
        val SLEEP_STEPS_MIN = listOf(15, 30, 45, 60)

        /** Volume changes this soon after the island's own slider moved it are its own echo. */
        const val OWN_VOLUME_WINDOW_MS = 1_000L

        /** Rain starts closer together than this are one shower, announced once. */
        const val RAIN_SAME_SHOWER_MS = 90 * 60_000L
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
            GestureAction.OPEN_CAMERA -> openCamera()
            // Both arrived in Android 9; a setting restored onto an older phone does nothing.
            GestureAction.LOCK_SCREEN -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                helperAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }
            GestureAction.TAKE_SCREENSHOT -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                helperAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
            GestureAction.OPEN_NOTIFICATIONS -> helperAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.OPEN_QUICK_SETTINGS -> helperAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
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
            TimerCommand.STOP_ALARM -> stopAlarm()
            TimerCommand.REPEAT -> {
                val length = timer.totalMs
                stopAlarm()
                if (length > 0) startTimer(length)
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

    override fun onVolumeChange(progress: Int) {
        ownVolumeChangeAt = SystemClock.elapsedRealtime()
        volumeMonitor.setMusicVolume(progress)
    }

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

    override fun onStartTimer(minutes: Int) {
        haptics.pop()
        startTimer(minutes * 60_000L)
    }

    override fun onStartStopwatch() {
        haptics.pop()
        startStopwatch()
    }

    override fun onSecondaryTap() {
        val kind = secondaryKind ?: return
        promoted = kind
        markActivityChanged()
        haptics.pop()
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
        val laps = stopwatch.laps.toList()
        val before = activities.peek(ActivityKind.STOPWATCH)?.presentation?.body as? ExpandedBody.Stopwatch
        onClockTick(
            LiveActivity(ActivityKind.STOPWATCH, presentations.stopwatch(elapsed, running, laps), expiresAt = Long.MAX_VALUE),
            changed = before == null || before.running != running || before.laps.size != laps.size,
        ) { it.refreshStopwatch(elapsed) }
    }

    fun startStopwatch() {
        if (!settings.featureStopwatch) return
        markActivityChanged()
        stopwatch.start()
        userStage = IslandMode.EXPANDED
        render()
    }

    /** A global action through the helper; without it, says so instead of doing nothing. */
    private fun helperAction(action: Int) {
        val perform = IslandBus.globalAction
        if (perform == null) {
            toast(context.getString(R.string.needs_helper))
            return
        }
        haptics.pop()
        // A screenshot of the island opened over everything is not what anyone wants.
        userStage = null
        render()
        handler.postDelayed({ runCatching { perform(action) } }, if (action == SCREENSHOT_ACTION) 350L else 0L)
    }

    private fun openCamera() {
        haptics.pop()
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) toast(context.getString(R.string.no_camera_app))
        userStage = null
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
        // The flashlight's button, and a tap on it, turn the torch off rather than open anything.
        if (currentTop()?.kind == ActivityKind.FLASHLIGHT) {
            quickActions.perform(QuickToggle.TORCH)
            userStage = null
            render()
            return
        }
        // The note's button ticks it off.
        if (currentTop()?.kind == ActivityKind.NOTE) {
            finishNote()
            return
        }
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
