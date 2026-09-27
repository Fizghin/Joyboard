package com.joyboard.notchisland.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("notch_island")

/**
 * Single source of truth for [IslandSettings]. The UI collects [settings]; the overlay service
 * collects the same flow, so a change in the app is reflected on screen immediately.
 */
class SettingsRepository private constructor(context: Context) {

    // This is a process-wide singleton, so it keeps the store rather than a Context: holding an
    // activity or service here would keep it alive long after it was gone.
    private val store: DataStore<Preferences> = context.applicationContext.dataStore

    val settings: Flow<IslandSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.writeSettings(updated)
        }
    }

    suspend fun resetToDefaults() {
        store.edit { prefs ->
            val enabled = prefs[K.enabled] ?: false
            prefs.clear()
            prefs.writeSettings(IslandSettings(enabled = enabled))
        }
    }

    suspend fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    suspend fun toggleAutoExpand(pkg: String) = update {
        val next = it.autoExpandPackages.toMutableSet()
        if (!next.add(pkg)) next.remove(pkg)
        it.copy(autoExpandPackages = next)
    }

    suspend fun toggleHidden(pkg: String) = update {
        val next = it.hiddenInPackages.toMutableSet()
        if (!next.add(pkg)) next.remove(pkg)
        it.copy(hiddenInPackages = next)
    }

    suspend fun toggleBlocked(pkg: String) = update {
        val next = it.blockedPackages.toMutableSet()
        if (!next.add(pkg)) next.remove(pkg)
        it.copy(blockedPackages = next)
    }

    private object K {
        val enabled = booleanPreferencesKey("enabled")
        val startOnBoot = booleanPreferencesKey("start_on_boot")
        val collapsedWidth = intPreferencesKey("collapsed_width")
        val collapsedHeight = intPreferencesKey("collapsed_height")
        val cornerRadius = intPreferencesKey("corner_radius")
        val offsetX = intPreferencesKey("offset_x")
        val offsetY = intPreferencesKey("offset_y")
        val avoidStatusBar = booleanPreferencesKey("avoid_status_bar")
        val positionMode = stringPreferencesKey("position_mode")
        val touchStripHeight = intPreferencesKey("touch_strip_height")
        val showTouchHint = booleanPreferencesKey("show_touch_hint")
        val drawAboveStatusBar = booleanPreferencesKey("draw_above_status_bar")
        val expandedWidth = intPreferencesKey("expanded_width")
        val compactWidth = intPreferencesKey("compact_width")
        val mediumWidth = intPreferencesKey("medium_width")
        val preset = stringPreferencesKey("preset")
        val iosMode = booleanPreferencesKey("ios_mode")
        val showFauxCamera = booleanPreferencesKey("show_faux_camera")
        // 2.2 stored the lens relative to the island; kept only to migrate from.
        val legacyCameraSize = intPreferencesKey("camera_size")
        val legacyCameraOffsetX = intPreferencesKey("camera_offset_x")
        val legacyCameraOffsetY = intPreferencesKey("camera_offset_y")
        val holeCenterX = floatPreferencesKey("hole_center_x")
        val holeCenterY = floatPreferencesKey("hole_center_y")
        val holeWidth = floatPreferencesKey("hole_width")
        val holeHeight = floatPreferencesKey("hole_height")
        val holeScreenWidth = floatPreferencesKey("hole_screen_width")
        val cameraSource = stringPreferencesKey("camera_source")
        val devicePresetId = stringPreferencesKey("device_preset_id")
        val avoidHole = booleanPreferencesKey("avoid_hole")
        val tapExpansion = stringPreferencesKey("tap_expansion")
        val backgroundColor = intPreferencesKey("background_color")
        val opacity = floatPreferencesKey("opacity")
        val borderEnabled = booleanPreferencesKey("border_enabled")
        val borderColor = intPreferencesKey("border_color")
        val borderWidth = intPreferencesKey("border_width")
        val shadowEnabled = booleanPreferencesKey("shadow_enabled")
        val accentSource = stringPreferencesKey("accent_source")
        val backgroundSource = stringPreferencesKey("background_source")
        val accentColor = intPreferencesKey("accent_color")
        val animationSpeed = floatPreferencesKey("animation_speed")
        val themeMode = stringPreferencesKey("theme_mode")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val hapticStrength = intPreferencesKey("haptic_strength")
        val autoCollapse = intPreferencesKey("auto_collapse")
        val compactRestSeconds = intPreferencesKey("compact_rest_seconds")
        val stayCompactForActivities = booleanPreferencesKey("stay_compact_for_activities")
        val hideInLandscape = booleanPreferencesKey("hide_in_landscape")
        val showOnLockScreen = booleanPreferencesKey("show_on_lock_screen")
        val dimBackground = booleanPreferencesKey("dim_background")
        val alwaysShowPill = booleanPreferencesKey("always_show_pill")
        val hideInFullscreen = booleanPreferencesKey("hide_in_fullscreen")
        val hideWhileTyping = booleanPreferencesKey("hide_while_typing")
        val hiddenInPackages = stringSetPreferencesKey("hidden_in_packages")
        val featureCalendar = booleanPreferencesKey("feature_calendar")
        val featureLyrics = booleanPreferencesKey("feature_lyrics")
        val effects = booleanPreferencesKey("effects")
        val featureHeadphones = booleanPreferencesKey("feature_headphones")
        val featureFlashlight = booleanPreferencesKey("feature_flashlight")
        val featureFocus = booleanPreferencesKey("feature_focus")
        val featureNavigation = booleanPreferencesKey("feature_navigation")
        val splitIsland = booleanPreferencesKey("split_island")
        val quickTimers = booleanPreferencesKey("quick_timers")
        val featureBatteryHeat = booleanPreferencesKey("feature_battery_heat")
        val featureNetworkSpeed = booleanPreferencesKey("feature_network_speed")
        val pinnedNote = stringPreferencesKey("pinned_note")
        val showNextAlarm = booleanPreferencesKey("show_next_alarm")
        val featureWeather = booleanPreferencesKey("feature_weather")
        val rainAlerts = booleanPreferencesKey("rain_alerts")
        val weatherArea = stringPreferencesKey("weather_area")
        val calendarLeadMinutes = intPreferencesKey("calendar_lead_minutes")
        val externalApiEnabled = booleanPreferencesKey("external_api_enabled")
        val tapAction = stringPreferencesKey("tap_action")
        val doubleTapAction = stringPreferencesKey("double_tap_action")
        val longPressAction = stringPreferencesKey("long_press_action")
        val swipeDownAction = stringPreferencesKey("swipe_down_action")
        val swipeUpAction = stringPreferencesKey("swipe_up_action")
        val swipeLeftAction = stringPreferencesKey("swipe_left_action")
        val swipeRightAction = stringPreferencesKey("swipe_right_action")
        val featureMedia = booleanPreferencesKey("feature_media")
        val featureNotifications = booleanPreferencesKey("feature_notifications")
        val featureCharging = booleanPreferencesKey("feature_charging")
        val featureVolume = booleanPreferencesKey("feature_volume")
        val featureRinger = booleanPreferencesKey("feature_ringer")
        val featureUnlock = booleanPreferencesKey("feature_unlock")
        val featureTimer = booleanPreferencesKey("feature_timer")
        val featurePrivacy = booleanPreferencesKey("feature_privacy")
        val featureBatteryLow = booleanPreferencesKey("feature_battery_low")
        val featureCalls = booleanPreferencesKey("feature_calls")
        val featureOngoing = booleanPreferencesKey("feature_ongoing")
        val featureStopwatch = booleanPreferencesKey("feature_stopwatch")
        val featureHistory = booleanPreferencesKey("feature_history")
        val autoExpandPackages = stringSetPreferencesKey("auto_expand_packages")
        val quickReplyEnabled = booleanPreferencesKey("quick_reply_enabled")
        val otpDetection = booleanPreferencesKey("otp_detection")
        val autoExpandOtp = booleanPreferencesKey("auto_expand_otp")
        val autoExpandCalls = booleanPreferencesKey("auto_expand_calls")
        val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietStartMinutes = intPreferencesKey("quiet_start_minutes")
        val quietEndMinutes = intPreferencesKey("quiet_end_minutes")
        val suspendWhenScreenOff = booleanPreferencesKey("suspend_when_screen_off")
        val respectSystemAnimationScale = booleanPreferencesKey("respect_animation_scale")
        val notificationStyle = stringPreferencesKey("notification_style")
        val notificationDuration = intPreferencesKey("notification_duration")
        val blockedPackages = stringSetPreferencesKey("blocked_packages")
        val silentNotifications = booleanPreferencesKey("silent_notifications")
        val respectDoNotDisturb = booleanPreferencesKey("respect_do_not_disturb")
        val updateManifestUrl = stringPreferencesKey("update_manifest_url")
        val autoCheckUpdates = booleanPreferencesKey("auto_check_updates")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val skippedVersion = intPreferencesKey("skipped_version")
    }

    // A lens placed in 2.2 was measured from the island's centre, and the island's centre sat at
    // (offsetX, offsetY + height / 2) — which is enough to put it back on the screen.
    private fun Preferences.legacyHoleSize(): Float? =
        if (this[K.showFauxCamera] == true) this[K.legacyCameraSize]?.toFloat() else null

    private fun Preferences.legacyHoleX(): Float? = legacyHoleSize()?.let {
        ((this[K.offsetX] ?: 0) + (this[K.legacyCameraOffsetX] ?: 0)).toFloat()
    }

    private fun Preferences.legacyHoleY(): Float? = legacyHoleSize()?.let {
        ((this[K.offsetY] ?: 0) + (this[K.collapsedHeight] ?: 30) / 2 + (this[K.legacyCameraOffsetY] ?: 0)).toFloat()
    }

    private fun Preferences.toSettings(): IslandSettings {
        val d = IslandSettings()
        return IslandSettings(
            enabled = this[K.enabled] ?: d.enabled,
            startOnBoot = this[K.startOnBoot] ?: d.startOnBoot,
            collapsedWidth = this[K.collapsedWidth] ?: d.collapsedWidth,
            collapsedHeight = this[K.collapsedHeight] ?: d.collapsedHeight,
            cornerRadius = this[K.cornerRadius] ?: d.cornerRadius,
            offsetX = this[K.offsetX] ?: d.offsetX,
            offsetY = this[K.offsetY] ?: d.offsetY,
            // Carries forward the old boolean for anyone upgrading from 1.1.
            positionMode = this[K.positionMode]?.toEnum<PositionMode>()
                ?: if (this[K.avoidStatusBar] == false) PositionMode.OVERLAP_STATUS_BAR
                else d.positionMode,
            touchStripHeight = this[K.touchStripHeight] ?: d.touchStripHeight,
            showTouchHint = this[K.showTouchHint] ?: d.showTouchHint,
            drawAboveStatusBar = this[K.drawAboveStatusBar] ?: d.drawAboveStatusBar,
            expandedWidth = this[K.expandedWidth] ?: d.expandedWidth,
            compactWidth = this[K.compactWidth] ?: d.compactWidth,
            mediumWidth = this[K.mediumWidth] ?: d.mediumWidth,
            preset = this[K.preset]?.toEnum<IslandPreset>() ?: d.preset,
            iosMode = this[K.iosMode] ?: d.iosMode,
            showFauxCamera = this[K.showFauxCamera] ?: d.showFauxCamera,
            holeCenterX = this[K.holeCenterX] ?: legacyHoleX() ?: d.holeCenterX,
            holeCenterY = this[K.holeCenterY] ?: legacyHoleY() ?: d.holeCenterY,
            holeWidth = this[K.holeWidth] ?: legacyHoleSize() ?: d.holeWidth,
            holeHeight = this[K.holeHeight] ?: legacyHoleSize() ?: d.holeHeight,
            holeScreenWidthDp = this[K.holeScreenWidth] ?: d.holeScreenWidthDp,
            cameraSource = this[K.cameraSource]?.toEnum<CameraSource>()
                ?: if (legacyHoleSize() != null) CameraSource.MANUAL else d.cameraSource,
            devicePresetId = this[K.devicePresetId],
            avoidHole = this[K.avoidHole] ?: d.avoidHole,
            tapExpansion = this[K.tapExpansion]?.toEnum<TapExpansion>() ?: d.tapExpansion,
            backgroundColor = this[K.backgroundColor] ?: d.backgroundColor,
            opacity = this[K.opacity] ?: d.opacity,
            borderEnabled = this[K.borderEnabled] ?: d.borderEnabled,
            borderColor = this[K.borderColor] ?: d.borderColor,
            borderWidth = this[K.borderWidth] ?: d.borderWidth,
            shadowEnabled = this[K.shadowEnabled] ?: d.shadowEnabled,
            accentSource = this[K.accentSource]?.toEnum<ColorSource>() ?: d.accentSource,
            backgroundSource = this[K.backgroundSource]?.toEnum<ColorSource>() ?: d.backgroundSource,
            accentColor = this[K.accentColor] ?: d.accentColor,
            animationSpeed = this[K.animationSpeed] ?: d.animationSpeed,
            themeMode = this[K.themeMode]?.toEnum<ThemeMode>() ?: d.themeMode,
            hapticsEnabled = this[K.hapticsEnabled] ?: d.hapticsEnabled,
            hapticStrength = this[K.hapticStrength] ?: d.hapticStrength,
            autoCollapseSeconds = this[K.autoCollapse] ?: d.autoCollapseSeconds,
            compactRestSeconds = this[K.compactRestSeconds] ?: d.compactRestSeconds,
            stayCompactForActivities = this[K.stayCompactForActivities]
                ?: d.stayCompactForActivities,
            hideInLandscape = this[K.hideInLandscape] ?: d.hideInLandscape,
            showOnLockScreen = this[K.showOnLockScreen] ?: d.showOnLockScreen,
            dimBackgroundWhenExpanded = this[K.dimBackground] ?: d.dimBackgroundWhenExpanded,
            alwaysShowPill = this[K.alwaysShowPill] ?: d.alwaysShowPill,
            hideInFullscreen = this[K.hideInFullscreen] ?: d.hideInFullscreen,
            hideWhileTyping = this[K.hideWhileTyping] ?: d.hideWhileTyping,
            hiddenInPackages = this[K.hiddenInPackages] ?: d.hiddenInPackages,
            featureCalendar = this[K.featureCalendar] ?: d.featureCalendar,
            featureLyrics = this[K.featureLyrics] ?: d.featureLyrics,
            effects = this[K.effects] ?: d.effects,
            featureHeadphones = this[K.featureHeadphones] ?: d.featureHeadphones,
            featureFlashlight = this[K.featureFlashlight] ?: d.featureFlashlight,
            featureFocus = this[K.featureFocus] ?: d.featureFocus,
            featureNavigation = this[K.featureNavigation] ?: d.featureNavigation,
            splitIsland = this[K.splitIsland] ?: d.splitIsland,
            quickTimers = this[K.quickTimers] ?: d.quickTimers,
            featureBatteryHeat = this[K.featureBatteryHeat] ?: d.featureBatteryHeat,
            featureNetworkSpeed = this[K.featureNetworkSpeed] ?: d.featureNetworkSpeed,
            pinnedNote = this[K.pinnedNote] ?: d.pinnedNote,
            showNextAlarm = this[K.showNextAlarm] ?: d.showNextAlarm,
            featureWeather = this[K.featureWeather] ?: d.featureWeather,
            rainAlerts = this[K.rainAlerts] ?: d.rainAlerts,
            weatherArea = this[K.weatherArea] ?: d.weatherArea,
            calendarLeadMinutes = this[K.calendarLeadMinutes] ?: d.calendarLeadMinutes,
            externalApiEnabled = this[K.externalApiEnabled] ?: d.externalApiEnabled,
            tapAction = this[K.tapAction]?.toEnum<GestureAction>() ?: d.tapAction,
            doubleTapAction = this[K.doubleTapAction]?.toEnum<GestureAction>() ?: d.doubleTapAction,
            longPressAction = this[K.longPressAction]?.toEnum<GestureAction>() ?: d.longPressAction,
            swipeDownAction = this[K.swipeDownAction]?.toEnum<GestureAction>() ?: d.swipeDownAction,
            swipeUpAction = this[K.swipeUpAction]?.toEnum<GestureAction>() ?: d.swipeUpAction,
            swipeLeftAction = this[K.swipeLeftAction]?.toEnum<GestureAction>() ?: d.swipeLeftAction,
            swipeRightAction = this[K.swipeRightAction]?.toEnum<GestureAction>() ?: d.swipeRightAction,
            featureMedia = this[K.featureMedia] ?: d.featureMedia,
            featureNotifications = this[K.featureNotifications] ?: d.featureNotifications,
            featureCharging = this[K.featureCharging] ?: d.featureCharging,
            featureVolume = this[K.featureVolume] ?: d.featureVolume,
            featureRinger = this[K.featureRinger] ?: d.featureRinger,
            featureUnlock = this[K.featureUnlock] ?: d.featureUnlock,
            featureTimer = this[K.featureTimer] ?: d.featureTimer,
            featurePrivacy = this[K.featurePrivacy] ?: d.featurePrivacy,
            featureBatteryLow = this[K.featureBatteryLow] ?: d.featureBatteryLow,
            featureCalls = this[K.featureCalls] ?: d.featureCalls,
            featureOngoing = this[K.featureOngoing] ?: d.featureOngoing,
            featureStopwatch = this[K.featureStopwatch] ?: d.featureStopwatch,
            featureHistory = this[K.featureHistory] ?: d.featureHistory,
            autoExpandPackages = this[K.autoExpandPackages] ?: d.autoExpandPackages,
            quickReplyEnabled = this[K.quickReplyEnabled] ?: d.quickReplyEnabled,
            otpDetection = this[K.otpDetection] ?: d.otpDetection,
            autoExpandOtp = this[K.autoExpandOtp] ?: d.autoExpandOtp,
            autoExpandCalls = this[K.autoExpandCalls] ?: d.autoExpandCalls,
            quietHoursEnabled = this[K.quietHoursEnabled] ?: d.quietHoursEnabled,
            quietStartMinutes = this[K.quietStartMinutes] ?: d.quietStartMinutes,
            quietEndMinutes = this[K.quietEndMinutes] ?: d.quietEndMinutes,
            suspendWhenScreenOff = this[K.suspendWhenScreenOff] ?: d.suspendWhenScreenOff,
            respectSystemAnimationScale = this[K.respectSystemAnimationScale]
                ?: d.respectSystemAnimationScale,
            notificationStyle = this[K.notificationStyle]?.toEnum<NotificationStyle>() ?: d.notificationStyle,
            notificationDurationMs = this[K.notificationDuration] ?: d.notificationDurationMs,
            blockedPackages = this[K.blockedPackages] ?: d.blockedPackages,
            silentNotifications = this[K.silentNotifications] ?: d.silentNotifications,
            respectDoNotDisturb = this[K.respectDoNotDisturb] ?: d.respectDoNotDisturb,
            updateManifestUrl = this[K.updateManifestUrl]?.takeIf { it.isNotBlank() }
                ?: d.updateManifestUrl,
            autoCheckUpdates = this[K.autoCheckUpdates] ?: d.autoCheckUpdates,
            lastUpdateCheck = this[K.lastUpdateCheck] ?: d.lastUpdateCheck,
            skippedVersion = this[K.skippedVersion] ?: d.skippedVersion,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.writeSettings(s: IslandSettings) {
        this[K.enabled] = s.enabled
        this[K.startOnBoot] = s.startOnBoot
        this[K.collapsedWidth] = s.collapsedWidth
        this[K.collapsedHeight] = s.collapsedHeight
        this[K.cornerRadius] = s.cornerRadius
        this[K.offsetX] = s.offsetX
        this[K.offsetY] = s.offsetY
        this[K.positionMode] = s.positionMode.name
        this[K.touchStripHeight] = s.touchStripHeight
        this[K.showTouchHint] = s.showTouchHint
        this[K.expandedWidth] = s.expandedWidth
        this[K.compactWidth] = s.compactWidth
        this[K.mediumWidth] = s.mediumWidth
        this[K.preset] = s.preset.name
        this[K.iosMode] = s.iosMode
        this[K.showFauxCamera] = s.showFauxCamera
        this[K.holeCenterX] = s.holeCenterX
        this[K.holeCenterY] = s.holeCenterY
        this[K.holeWidth] = s.holeWidth
        this[K.holeHeight] = s.holeHeight
        this[K.cameraSource] = s.cameraSource.name
        s.devicePresetId?.let { this[K.devicePresetId] = it } ?: remove(K.devicePresetId)
        this[K.avoidHole] = s.avoidHole
        remove(K.legacyCameraSize)
        remove(K.legacyCameraOffsetX)
        remove(K.legacyCameraOffsetY)
        this[K.tapExpansion] = s.tapExpansion.name
        this[K.backgroundColor] = s.backgroundColor
        this[K.opacity] = s.opacity
        this[K.borderEnabled] = s.borderEnabled
        this[K.borderColor] = s.borderColor
        this[K.borderWidth] = s.borderWidth
        this[K.shadowEnabled] = s.shadowEnabled
        this[K.accentSource] = s.accentSource.name
        this[K.backgroundSource] = s.backgroundSource.name
        this[K.accentColor] = s.accentColor
        this[K.animationSpeed] = s.animationSpeed
        this[K.themeMode] = s.themeMode.name
        this[K.hapticsEnabled] = s.hapticsEnabled
        this[K.hapticStrength] = s.hapticStrength
        this[K.autoCollapse] = s.autoCollapseSeconds
        this[K.compactRestSeconds] = s.compactRestSeconds
        this[K.stayCompactForActivities] = s.stayCompactForActivities
        this[K.hideInLandscape] = s.hideInLandscape
        this[K.showOnLockScreen] = s.showOnLockScreen
        this[K.dimBackground] = s.dimBackgroundWhenExpanded
        this[K.alwaysShowPill] = s.alwaysShowPill
        this[K.tapAction] = s.tapAction.name
        this[K.doubleTapAction] = s.doubleTapAction.name
        this[K.longPressAction] = s.longPressAction.name
        this[K.swipeDownAction] = s.swipeDownAction.name
        this[K.swipeUpAction] = s.swipeUpAction.name
        this[K.swipeLeftAction] = s.swipeLeftAction.name
        this[K.swipeRightAction] = s.swipeRightAction.name
        this[K.featureMedia] = s.featureMedia
        this[K.featureNotifications] = s.featureNotifications
        this[K.featureCharging] = s.featureCharging
        this[K.featureVolume] = s.featureVolume
        this[K.featureRinger] = s.featureRinger
        this[K.featureUnlock] = s.featureUnlock
        this[K.featureTimer] = s.featureTimer
        this[K.featurePrivacy] = s.featurePrivacy
        this[K.featureBatteryLow] = s.featureBatteryLow
        this[K.featureCalls] = s.featureCalls
        this[K.featureOngoing] = s.featureOngoing
        this[K.featureStopwatch] = s.featureStopwatch
        this[K.featureHistory] = s.featureHistory
        this[K.autoExpandPackages] = s.autoExpandPackages
        this[K.quickReplyEnabled] = s.quickReplyEnabled
        this[K.otpDetection] = s.otpDetection
        this[K.autoExpandOtp] = s.autoExpandOtp
        this[K.autoExpandCalls] = s.autoExpandCalls
        this[K.quietHoursEnabled] = s.quietHoursEnabled
        this[K.quietStartMinutes] = s.quietStartMinutes
        this[K.quietEndMinutes] = s.quietEndMinutes
        this[K.suspendWhenScreenOff] = s.suspendWhenScreenOff
        this[K.respectSystemAnimationScale] = s.respectSystemAnimationScale
        this[K.notificationStyle] = s.notificationStyle.name
        this[K.notificationDuration] = s.notificationDurationMs
        this[K.blockedPackages] = s.blockedPackages
        this[K.silentNotifications] = s.silentNotifications
        this[K.respectDoNotDisturb] = s.respectDoNotDisturb
        this[K.updateManifestUrl] = s.updateManifestUrl
        this[K.autoCheckUpdates] = s.autoCheckUpdates
        this[K.lastUpdateCheck] = s.lastUpdateCheck
        this[K.skippedVersion] = s.skippedVersion
        this[K.holeScreenWidth] = s.holeScreenWidthDp
        this[K.drawAboveStatusBar] = s.drawAboveStatusBar
        this[K.hideInFullscreen] = s.hideInFullscreen
        this[K.hideWhileTyping] = s.hideWhileTyping
        this[K.hiddenInPackages] = s.hiddenInPackages
        this[K.featureCalendar] = s.featureCalendar
        this[K.featureLyrics] = s.featureLyrics
        this[K.effects] = s.effects
        this[K.featureHeadphones] = s.featureHeadphones
        this[K.featureFlashlight] = s.featureFlashlight
        this[K.featureFocus] = s.featureFocus
        this[K.featureNavigation] = s.featureNavigation
        this[K.splitIsland] = s.splitIsland
        this[K.quickTimers] = s.quickTimers
        this[K.featureBatteryHeat] = s.featureBatteryHeat
        this[K.featureNetworkSpeed] = s.featureNetworkSpeed
        this[K.pinnedNote] = s.pinnedNote
        this[K.showNextAlarm] = s.showNextAlarm
        this[K.featureWeather] = s.featureWeather
        this[K.rainAlerts] = s.rainAlerts
        this[K.weatherArea] = s.weatherArea
        this[K.calendarLeadMinutes] = s.calendarLeadMinutes
        this[K.externalApiEnabled] = s.externalApiEnabled
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}

private inline fun <reified T : Enum<T>> String.toEnum(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()
