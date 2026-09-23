package com.joyboard.notchisland.data

import android.graphics.Color

/** Everything the island knows about how it should look and behave. */
data class IslandSettings(
    // ---- master ----
    val enabled: Boolean = false,
    val startOnBoot: Boolean = true,

    // ---- geometry (dp) ----
    val collapsedWidth: Int = 118,
    val collapsedHeight: Int = 30,
    val cornerRadius: Int = 18,
    val offsetX: Int = 0,
    val offsetY: Int = 4,
    /**
     * Overlay windows always sit below the system status bar, which swallows touches in its own
     * band. Keeping clear of it is what makes the island tappable, so this defaults to on.
     */
    val avoidStatusBar: Boolean = true,
    val expandedWidth: Int = 330,
    val compactWidth: Int = 190,
    val separateTouchTrigger: Boolean = false,
    val triggerOffsetX: Int = 0,
    val triggerOffsetY: Int = 60,

    // ---- appearance ----
    val backgroundColor: Int = Color.BLACK,
    val opacity: Float = 1f,
    val borderEnabled: Boolean = false,
    val borderColor: Int = 0xFF2A2A2E.toInt(),
    val borderWidth: Int = 1,
    val shadowEnabled: Boolean = true,
    val tintFromArtwork: Boolean = true,
    val accentColor: Int = 0xFF3B82F6.toInt(),
    val animationSpeed: Float = 1f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    // ---- behaviour ----
    val hapticsEnabled: Boolean = true,
    val hapticStrength: Int = 2,           // 1..3
    val autoCollapseSeconds: Int = 6,
    val hideInLandscape: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val dimBackgroundWhenExpanded: Boolean = true,
    val idleClock: Boolean = false,
    val alwaysShowPill: Boolean = true,

    // ---- gestures ----
    val tapAction: GestureAction = GestureAction.EXPAND,
    val doubleTapAction: GestureAction = GestureAction.MEDIA_PLAY_PAUSE,
    val longPressAction: GestureAction = GestureAction.OPEN_SETTINGS,
    val swipeDownAction: GestureAction = GestureAction.EXPAND,
    val swipeUpAction: GestureAction = GestureAction.COLLAPSE,
    val swipeLeftAction: GestureAction = GestureAction.MEDIA_NEXT,
    val swipeRightAction: GestureAction = GestureAction.MEDIA_PREVIOUS,

    // ---- live activities ----
    val featureMedia: Boolean = true,
    val featureNotifications: Boolean = true,
    val featureCharging: Boolean = true,
    val featureVolume: Boolean = true,
    val featureRinger: Boolean = true,
    val featureUnlock: Boolean = true,
    val featureTimer: Boolean = true,
    val featurePrivacy: Boolean = true,
    val featureBatteryLow: Boolean = true,

    // ---- notification handling ----
    val notificationStyle: NotificationStyle = NotificationStyle.PREVIEW,
    val notificationDurationMs: Int = 4000,
    val blockedPackages: Set<String> = emptySet(),
    val silentNotifications: Boolean = false,

    // ---- updates ----
    val updateManifestUrl: String = DEFAULT_UPDATE_MANIFEST_URL,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val skippedVersion: Int = 0,
) {
    val alphaBackgroundColor: Int
        get() = Color.argb(
            (Color.alpha(backgroundColor) * opacity.coerceIn(0f, 1f)).toInt(),
            Color.red(backgroundColor), Color.green(backgroundColor), Color.blue(backgroundColor)
        )
}

/**
 * Where the in-app updater looks for a release manifest. The default points at this repository,
 * which has to be public for a phone to read it; anything else that serves the same JSON works
 * just as well.
 */
const val DEFAULT_UPDATE_MANIFEST_URL =
    "https://raw.githubusercontent.com/Fizghin/Joyboard/notch/update.json"

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class NotificationStyle { PREVIEW, MINIMAL, ICON_ONLY }

enum class GestureAction(val label: String) {
    NONE("Do nothing"),
    EXPAND("Expand island"),
    COLLAPSE("Collapse island"),
    MEDIA_PLAY_PAUSE("Play / pause"),
    MEDIA_NEXT("Next track"),
    MEDIA_PREVIOUS("Previous track"),
    TOGGLE_TORCH("Toggle flashlight"),
    TOGGLE_RINGER("Cycle ringer mode"),
    OPEN_SETTINGS("Open Notch Island"),
    OPEN_LAST_NOTIFICATION("Open last notification"),
    SHOW_QUICK_PANEL("Show quick toggles"),
    HIDE_TEMPORARILY("Hide for 30 seconds"),
}
