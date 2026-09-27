package com.joyboard.notchisland.data

import android.graphics.Color
import com.joyboard.notchisland.R
import androidx.annotation.StringRes

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

    // ---- the phone's own camera cutout ----
    /**
     * The camera hole, in screen terms: dp from the screen's horizontal centre and from its top
     * edge. It is hardware, so it is stored against the screen and the island arranges itself
     * around it. A zero width means no hole is known.
     */
    val holeCenterX: Float = 0f,
    val holeCenterY: Float = 0f,
    val holeWidth: Float = 0f,
    val holeHeight: Float = 0f,
    /**
     * How wide the screen was, in dp, when the hole was placed. A foldable has a different
     * screen — and a different camera — on each side, so a mismatch means the stored hole
     * belongs to the other screen. Zero means unknown, and the hole is trusted as it is.
     */
    val holeScreenWidthDp: Float = 0f,
    val cameraSource: CameraSource = CameraSource.NONE,
    val devicePresetId: String? = null,
    /** Keep text and icons out from under the camera. */
    val avoidHole: Boolean = true,
    /** Draw a stand-in lens over the hole, for when the island's body would otherwise hide it. */
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
    /**
     * How long the compact readout stays up after something happens, before the island settles
     * back to its resting pill. Without this a long-running notification would hold it open.
     */
    val compactRestSeconds: Int = 10,
    /** Keep the compact readout for as long as an activity is alive, the way iOS does. */
    val stayCompactForActivities: Boolean = false,
    val hideInLandscape: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val dimBackgroundWhenExpanded: Boolean = true,
    val alwaysShowPill: Boolean = true,
    /** Step aside when a video or game hides the status bar. */
    val hideInFullscreen: Boolean = true,
    /** Step aside while the keyboard is up. Needs the helper service. */
    val hideWhileTyping: Boolean = false,
    /** Apps the island stays out of while they are on screen. Needs the helper service. */
    val hiddenInPackages: Set<String> = emptySet(),

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
    /** The next calendar event, counting down. Off by default: it needs calendar access. */
    val featureCalendar: Boolean = false,
    /** How long before an event the island starts counting down to it. */
    val calendarLeadMinutes: Int = 15,

    // ---- notification handling ----
    val notificationStyle: NotificationStyle = NotificationStyle.PREVIEW,
    val notificationDurationMs: Int = 4000,
    val blockedPackages: Set<String> = emptySet(),
    val autoExpandPackages: Set<String> = emptySet(),
    /** Let notifications the phone considers silent take over the island too. */
    val silentNotifications: Boolean = false,
    /** When Do Not Disturb stops a notification interrupting, the island does not either. */
    val respectDoNotDisturb: Boolean = true,
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

    // ---- automation ----
    /** Let other apps — Tasker, Automate, shortcuts — put their own messages on the island. */
    val externalApiEnabled: Boolean = false,

    // ---- updates ----
    val updateManifestUrl: String = DEFAULT_UPDATE_MANIFEST_URL,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val skippedVersion: Int = 0,
)

/**
 * Where the in-app updater looks for a release manifest. The default points at this repository,
 * which has to be public for a phone to read it; anything else that serves the same JSON works
 * just as well.
 */
const val DEFAULT_UPDATE_MANIFEST_URL =
    "https://raw.githubusercontent.com/Fizghin/Joyboard/notch/update.json"

/** Where the known camera position came from. */
enum class CameraSource(@StringRes val label: Int) {
    NONE(R.string.not_set),
    DETECTED(R.string.reported_by_phone),
    PRESET(R.string.from_device_preset),
    MANUAL(R.string.calibrated_by_hand),
}

/** The hole as geometry, or null when none is known. */
val IslandSettings.hole: com.joyboard.notchisland.island.Hole?
    get() = if (holeWidth > 0f && holeHeight > 0f) {
        com.joyboard.notchisland.island.Hole(holeCenterX, holeCenterY, holeWidth, holeHeight)
    } else {
        null
    }

/** Notes which screen the hole was placed on, so a foldable's other screen can tell. */
fun IslandSettings.stampedFor(screenDp: Float) =
    copy(holeScreenWidthDp = if (hole != null) screenDp else 0f)

fun IslandSettings.withHole(
    hole: com.joyboard.notchisland.island.Hole?,
    source: CameraSource,
    screenWidthDp: Float = 0f,
) = copy(
    holeScreenWidthDp = if (hole == null) 0f else screenWidthDp,
    holeCenterX = hole?.centerX ?: 0f,
    holeCenterY = hole?.centerY ?: 0f,
    holeWidth = hole?.width ?: 0f,
    holeHeight = hole?.height ?: 0f,
    cameraSource = if (hole == null) CameraSource.NONE else source,
)

/** Where the island is anchored, and therefore what can be touched. */
enum class PositionMode(@StringRes val label: Int) {
    /** Entirely below the status bar: every pixel of the island is tappable. */
    BELOW_STATUS_BAR(R.string.below_status_bar),

    /** Drawn up in the status bar for the notch look, with a touch strip hanging below. */
    OVERLAP_STATUS_BAR(R.string.over_status_bar),

    /** Wherever the offsets put it, untouched by either rule. */
    CUSTOM(R.string.custom_offset),
}

/** Where a colour comes from: picked by hand, from the wallpaper, or from the album art. */
enum class ColorSource(@StringRes val label: Int) {
    MANUAL(R.string.chosen_colour),
    MATERIAL_YOU(R.string.match_my_wallpaper),
    ARTWORK(R.string.match_what_playing),
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
enum class TapExpansion(@StringRes val label: Int) {
    /** Pill → compact preview → small card → everything, one tap at a time. */
    STEP(R.string.step_through_sizes),

    /** Straight to the full panel. */
    DIRECT(R.string.open_everything_at_once),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class NotificationStyle { PREVIEW, MINIMAL, ICON_ONLY }

enum class GestureAction(@StringRes val label: Int) {
    NONE(R.string.do_nothing),
    EXPAND(R.string.expand_island),
    EXPAND_FULL(R.string.open_everything),
    COLLAPSE(R.string.collapse_island),
    MEDIA_PLAY_PAUSE(R.string.play_pause),
    MEDIA_NEXT(R.string.next_track),
    MEDIA_PREVIOUS(R.string.previous_track),
    TOGGLE_TORCH(R.string.toggle_flashlight),
    TOGGLE_RINGER(R.string.cycle_ringer_mode),
    OPEN_SETTINGS(R.string.open_notch_island),
    OPEN_LAST_NOTIFICATION(R.string.open_last_notification),
    SHOW_HISTORY(R.string.show_recent_notifications),
    START_STOPWATCH(R.string.start_stopwatch),
    SHOW_QUICK_PANEL(R.string.show_quick_toggles),
    HIDE_TEMPORARILY(R.string.hide_30_seconds),
}
