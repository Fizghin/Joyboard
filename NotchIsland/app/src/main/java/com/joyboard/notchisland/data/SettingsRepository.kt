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
        val expandedWidth = intPreferencesKey("expanded_width")
        val compactWidth = intPreferencesKey("compact_width")
        val separateTouchTrigger = booleanPreferencesKey("separate_touch_trigger")
        val triggerOffsetX = intPreferencesKey("trigger_offset_x")
        val triggerOffsetY = intPreferencesKey("trigger_offset_y")
        val backgroundColor = intPreferencesKey("background_color")
        val opacity = floatPreferencesKey("opacity")
        val borderEnabled = booleanPreferencesKey("border_enabled")
        val borderColor = intPreferencesKey("border_color")
        val borderWidth = intPreferencesKey("border_width")
        val shadowEnabled = booleanPreferencesKey("shadow_enabled")
        val tintFromArtwork = booleanPreferencesKey("tint_from_artwork")
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
            avoidStatusBar = this[K.avoidStatusBar] ?: d.avoidStatusBar,
            expandedWidth = this[K.expandedWidth] ?: d.expandedWidth,
            compactWidth = this[K.compactWidth] ?: d.compactWidth,
            separateTouchTrigger = this[K.separateTouchTrigger] ?: d.separateTouchTrigger,
            triggerOffsetX = this[K.triggerOffsetX] ?: d.triggerOffsetX,
            triggerOffsetY = this[K.triggerOffsetY] ?: d.triggerOffsetY,
            backgroundColor = this[K.backgroundColor] ?: d.backgroundColor,
            opacity = this[K.opacity] ?: d.opacity,
            borderEnabled = this[K.borderEnabled] ?: d.borderEnabled,
            borderColor = this[K.borderColor] ?: d.borderColor,
            borderWidth = this[K.borderWidth] ?: d.borderWidth,
            shadowEnabled = this[K.shadowEnabled] ?: d.shadowEnabled,
            tintFromArtwork = this[K.tintFromArtwork] ?: d.tintFromArtwork,
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
        this[K.avoidStatusBar] = s.avoidStatusBar
        this[K.expandedWidth] = s.expandedWidth
        this[K.compactWidth] = s.compactWidth
        this[K.separateTouchTrigger] = s.separateTouchTrigger
        this[K.triggerOffsetX] = s.triggerOffsetX
        this[K.triggerOffsetY] = s.triggerOffsetY
        this[K.backgroundColor] = s.backgroundColor
        this[K.opacity] = s.opacity
        this[K.borderEnabled] = s.borderEnabled
        this[K.borderColor] = s.borderColor
        this[K.borderWidth] = s.borderWidth
        this[K.shadowEnabled] = s.shadowEnabled
        this[K.tintFromArtwork] = s.tintFromArtwork
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
