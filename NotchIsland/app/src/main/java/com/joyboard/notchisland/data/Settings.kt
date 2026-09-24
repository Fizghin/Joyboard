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
    val positionMode: PositionMode = PositionMode.BELOW_STATUS_BAR,
    /**
     * In [PositionMode.OVERLAP_STATUS_BAR] the pill is drawn up in the status bar band, where
     * touches never arrive. This transparent strip hangs below it, inside the same window, so
     * there is always somewhere to tap.
     */
    val touchStripHeight: Int = 20,
    val showTouchHint: Boolean = true,
    val expandedWidth: Int = 330,
    val compactWidth: Int = 190,
    /** The halfway "preview card" stage between compact and fully open. */
    val mediumWidth: Int = 260,
    val preset: IslandPreset = IslandPreset.CUSTOM,
    /**
     * Pure black, fully rounded corners and the SwiftUI spring the real Dynamic Island uses,
     * regardless of what the colour and motion settings say.
     */
    val iosMode: Boolean = false,
    val showFauxCamera: Boolean = false,

    // ---- appearance ----
    val backgroundColor: Int = Color.BLACK,
    val opacity: Float = 1f,
    val borderEnabled: Boolean = false,
    val borderColor: Int = 0xFF2A2A2E.toInt(),
    val borderWidth: Int = 1,
    val shadowEnabled: Boolean = true,
    val accentSource: ColorSource = ColorSource.MATERIAL_YOU,
    val backgroundSource: ColorSource = ColorSource.MANUAL,
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
    /** Whether a tap walks through the sizes or jumps straight to the full panel. */
    val tapExpansion: TapExpansion = TapExpansion.STEP,
    val tapAction: GestureAction = GestureAction.EXPAND,
    val doubleTapAction: GestureAction = GestureAction.MEDIA_PLAY_PAUSE,
    val longPressAction: GestureAction = GestureAction.OPEN_SETTINGS,
    val swipeDownAction: GestureAction = GestureAction.EXPAND_FULL,
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
    val featureCalls: Boolean = true,
    val featureOngoing: Boolean = true,
    val featureStopwatch: Boolean = true,
    val featureHistory: Boolean = true,

    // ---- notification handling ----
    val notificationStyle: NotificationStyle = NotificationStyle.PREVIEW,
    val notificationDurationMs: Int = 4000,
    val blockedPackages: Set<String> = emptySet(),
    val autoExpandPackages: Set<String> = emptySet(),
    val silentNotifications: Boolean = false,
    val quickReplyEnabled: Boolean = true,
    val otpDetection: Boolean = true,
    val autoExpandOtp: Boolean = true,
    val autoExpandCalls: Boolean = true,

    // ---- quiet hours and power ----
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = 23 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val suspendWhenScreenOff: Boolean = true,
    val respectSystemAnimationScale: Boolean = true,

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

/** Where the island is anchored, and therefore what can be touched. */
enum class PositionMode(val label: String) {
    /** Entirely below the status bar: every pixel of the island is tappable. */
    BELOW_STATUS_BAR("Below the status bar"),

    /** Drawn up in the status bar for the notch look, with a touch strip hanging below. */
    OVERLAP_STATUS_BAR("Over the status bar"),

    /** Wherever the offsets put it, untouched by either rule. */
    CUSTOM("Custom offset"),
}

/** Where a colour comes from: picked by hand, from the wallpaper, or from the album art. */
enum class ColorSource(val label: String) {
    MANUAL("Chosen colour"),
    MATERIAL_YOU("Match my wallpaper"),
    ARTWORK("Match what's playing"),
}

/** True when the wall-clock minute falls inside the quiet window, wrapping over midnight. */
fun IslandSettings.isQuietAt(minuteOfDay: Int): Boolean {
    if (!quietHoursEnabled) return false
    if (quietStartMinutes == quietEndMinutes) return false
    return if (quietStartMinutes < quietEndMinutes) {
        minuteOfDay >= quietStartMinutes && minuteOfDay < quietEndMinutes
    } else {
        minuteOfDay >= quietStartMinutes || minuteOfDay < quietEndMinutes
    }
}

/** What a tap does to the island's size. */
enum class TapExpansion(val label: String) {
    /** Pill → compact preview → small card → everything, one tap at a time. */
    STEP("Step through the sizes"),

    /** Straight to the full panel. */
    DIRECT("Open everything at once"),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class NotificationStyle { PREVIEW, MINIMAL, ICON_ONLY }

enum class GestureAction(val label: String) {
    NONE("Do nothing"),
    EXPAND("Expand island"),
    EXPAND_FULL("Open everything"),
    COLLAPSE("Collapse island"),
    MEDIA_PLAY_PAUSE("Play / pause"),
    MEDIA_NEXT("Next track"),
    MEDIA_PREVIOUS("Previous track"),
    TOGGLE_TORCH("Toggle flashlight"),
    TOGGLE_RINGER("Cycle ringer mode"),
    OPEN_SETTINGS("Open Notch Island"),
    OPEN_LAST_NOTIFICATION("Open last notification"),
    SHOW_HISTORY("Show recent notifications"),
    START_STOPWATCH("Start the stopwatch"),
    SHOW_QUICK_PANEL("Show quick toggles"),
    HIDE_TEMPORARILY("Hide for 30 seconds"),
}
