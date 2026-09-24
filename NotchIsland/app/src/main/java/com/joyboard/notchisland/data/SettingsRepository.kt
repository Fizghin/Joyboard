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
class SettingsRepository(private val context: Context) {

    val settings: Flow<IslandSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (IslandSettings) -> IslandSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.writeSettings(updated)
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { prefs ->
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
        val expandedWidth = intPreferencesKey("expanded_width")
        val compactWidth = intPreferencesKey("compact_width")
        val mediumWidth = intPreferencesKey("medium_width")
        val preset = stringPreferencesKey("preset")
        val iosMode = booleanPreferencesKey("ios_mode")
        val showFauxCamera = booleanPreferencesKey("show_faux_camera")
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
        val hideInLandscape = booleanPreferencesKey("hide_in_landscape")
        val showOnLockScreen = booleanPreferencesKey("show_on_lock_screen")
        val dimBackground = booleanPreferencesKey("dim_background")
        val idleClock = booleanPreferencesKey("idle_clock")
        val alwaysShowPill = booleanPreferencesKey("always_show_pill")
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
        val updateManifestUrl = stringPreferencesKey("update_manifest_url")
        val autoCheckUpdates = booleanPreferencesKey("auto_check_updates")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val skippedVersion = intPreferencesKey("skipped_version")
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
            expandedWidth = this[K.expandedWidth] ?: d.expandedWidth,
            compactWidth = this[K.compactWidth] ?: d.compactWidth,
            mediumWidth = this[K.mediumWidth] ?: d.mediumWidth,
            preset = this[K.preset]?.toEnum<IslandPreset>() ?: d.preset,
            iosMode = this[K.iosMode] ?: d.iosMode,
            showFauxCamera = this[K.showFauxCamera] ?: d.showFauxCamera,
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
            hideInLandscape = this[K.hideInLandscape] ?: d.hideInLandscape,
            showOnLockScreen = this[K.showOnLockScreen] ?: d.showOnLockScreen,
            dimBackgroundWhenExpanded = this[K.dimBackground] ?: d.dimBackgroundWhenExpanded,
            idleClock = this[K.idleClock] ?: d.idleClock,
            alwaysShowPill = this[K.alwaysShowPill] ?: d.alwaysShowPill,
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
        this[K.hideInLandscape] = s.hideInLandscape
        this[K.showOnLockScreen] = s.showOnLockScreen
        this[K.dimBackground] = s.dimBackgroundWhenExpanded
        this[K.idleClock] = s.idleClock
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
        this[K.updateManifestUrl] = s.updateManifestUrl
        this[K.autoCheckUpdates] = s.autoCheckUpdates
        this[K.lastUpdateCheck] = s.lastUpdateCheck
        this[K.skippedVersion] = s.skippedVersion
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}

private inline fun <reified T : Enum<T>> String.toEnum(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()
