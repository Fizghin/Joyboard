package com.joyboard.notchisland.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings as portable JSON, for the backup and restore buttons. Unknown keys are ignored and
 * missing keys fall back to defaults, so a backup taken from an older build still restores.
 */
object SettingsCodec {

    const val FORMAT_VERSION = 1

    fun toJson(settings: IslandSettings): String = JSONObject().apply {
        put("format", FORMAT_VERSION)
        put("collapsedWidth", settings.collapsedWidth)
        put("collapsedHeight", settings.collapsedHeight)
        put("cornerRadius", settings.cornerRadius)
        put("offsetX", settings.offsetX)
        put("offsetY", settings.offsetY)
        put("positionMode", settings.positionMode.name)
        put("touchStripHeight", settings.touchStripHeight)
        put("showTouchHint", settings.showTouchHint)
        put("expandedWidth", settings.expandedWidth)
        put("compactWidth", settings.compactWidth)
        put("mediumWidth", settings.mediumWidth)
        put("preset", settings.preset.name)
        put("iosMode", settings.iosMode)
        put("showFauxCamera", settings.showFauxCamera)
        put("tapExpansion", settings.tapExpansion.name)
        put("backgroundColor", settings.backgroundColor)
        put("opacity", settings.opacity.toDouble())
        put("borderEnabled", settings.borderEnabled)
        put("borderColor", settings.borderColor)
        put("borderWidth", settings.borderWidth)
        put("shadowEnabled", settings.shadowEnabled)
        put("accentSource", settings.accentSource.name)
        put("backgroundSource", settings.backgroundSource.name)
        put("accentColor", settings.accentColor)
        put("animationSpeed", settings.animationSpeed.toDouble())
        put("themeMode", settings.themeMode.name)
        put("hapticsEnabled", settings.hapticsEnabled)
        put("hapticStrength", settings.hapticStrength)
        put("autoCollapseSeconds", settings.autoCollapseSeconds)
        put("hideInLandscape", settings.hideInLandscape)
        put("showOnLockScreen", settings.showOnLockScreen)
        put("dimBackgroundWhenExpanded", settings.dimBackgroundWhenExpanded)
        put("alwaysShowPill", settings.alwaysShowPill)
        put("tapAction", settings.tapAction.name)
        put("doubleTapAction", settings.doubleTapAction.name)
        put("longPressAction", settings.longPressAction.name)
        put("swipeDownAction", settings.swipeDownAction.name)
        put("swipeUpAction", settings.swipeUpAction.name)
        put("swipeLeftAction", settings.swipeLeftAction.name)
        put("swipeRightAction", settings.swipeRightAction.name)
        put("featureMedia", settings.featureMedia)
        put("featureNotifications", settings.featureNotifications)
        put("featureCharging", settings.featureCharging)
        put("featureVolume", settings.featureVolume)
        put("featureRinger", settings.featureRinger)
        put("featureUnlock", settings.featureUnlock)
        put("featureTimer", settings.featureTimer)
        put("featurePrivacy", settings.featurePrivacy)
        put("featureBatteryLow", settings.featureBatteryLow)
        put("featureCalls", settings.featureCalls)
        put("featureOngoing", settings.featureOngoing)
        put("featureStopwatch", settings.featureStopwatch)
        put("featureHistory", settings.featureHistory)
        put("notificationStyle", settings.notificationStyle.name)
        put("notificationDurationMs", settings.notificationDurationMs)
        put("blockedPackages", JSONArray(settings.blockedPackages.toList()))
        put("autoExpandPackages", JSONArray(settings.autoExpandPackages.toList()))
        put("quickReplyEnabled", settings.quickReplyEnabled)
        put("otpDetection", settings.otpDetection)
        put("autoExpandOtp", settings.autoExpandOtp)
        put("autoExpandCalls", settings.autoExpandCalls)
        put("quietHoursEnabled", settings.quietHoursEnabled)
        put("quietStartMinutes", settings.quietStartMinutes)
        put("quietEndMinutes", settings.quietEndMinutes)
        put("suspendWhenScreenOff", settings.suspendWhenScreenOff)
        put("respectSystemAnimationScale", settings.respectSystemAnimationScale)
        put("startOnBoot", settings.startOnBoot)
        put("autoCheckUpdates", settings.autoCheckUpdates)
        put("updateManifestUrl", settings.updateManifestUrl)
    }.toString(2)

    /** Applies everything the document contains on top of [base]; throws on malformed JSON. */
    fun fromJson(json: String, base: IslandSettings = IslandSettings()): IslandSettings {
        val o = JSONObject(json)
        fun strings(key: String): Set<String>? {
            val array = o.optJSONArray(key) ?: return null
            return (0 until array.length()).map { array.getString(it) }.toSet()
        }
        return base.copy(
            collapsedWidth = o.optInt("collapsedWidth", base.collapsedWidth),
            collapsedHeight = o.optInt("collapsedHeight", base.collapsedHeight),
            cornerRadius = o.optInt("cornerRadius", base.cornerRadius),
            offsetX = o.optInt("offsetX", base.offsetX),
            offsetY = o.optInt("offsetY", base.offsetY),
            positionMode = o.enum("positionMode", base.positionMode),
            touchStripHeight = o.optInt("touchStripHeight", base.touchStripHeight),
            showTouchHint = o.optBoolean("showTouchHint", base.showTouchHint),
            expandedWidth = o.optInt("expandedWidth", base.expandedWidth),
            compactWidth = o.optInt("compactWidth", base.compactWidth),
            mediumWidth = o.optInt("mediumWidth", base.mediumWidth),
            preset = o.enum("preset", base.preset),
            iosMode = o.optBoolean("iosMode", base.iosMode),
            showFauxCamera = o.optBoolean("showFauxCamera", base.showFauxCamera),
            tapExpansion = o.enum("tapExpansion", base.tapExpansion),
            backgroundColor = o.optInt("backgroundColor", base.backgroundColor),
            opacity = o.optDouble("opacity", base.opacity.toDouble()).toFloat(),
            borderEnabled = o.optBoolean("borderEnabled", base.borderEnabled),
            borderColor = o.optInt("borderColor", base.borderColor),
            borderWidth = o.optInt("borderWidth", base.borderWidth),
            shadowEnabled = o.optBoolean("shadowEnabled", base.shadowEnabled),
            accentSource = o.enum("accentSource", base.accentSource),
            backgroundSource = o.enum("backgroundSource", base.backgroundSource),
            accentColor = o.optInt("accentColor", base.accentColor),
            animationSpeed = o.optDouble("animationSpeed", base.animationSpeed.toDouble()).toFloat(),
            themeMode = o.enum("themeMode", base.themeMode),
            hapticsEnabled = o.optBoolean("hapticsEnabled", base.hapticsEnabled),
            hapticStrength = o.optInt("hapticStrength", base.hapticStrength),
            autoCollapseSeconds = o.optInt("autoCollapseSeconds", base.autoCollapseSeconds),
            hideInLandscape = o.optBoolean("hideInLandscape", base.hideInLandscape),
            showOnLockScreen = o.optBoolean("showOnLockScreen", base.showOnLockScreen),
            dimBackgroundWhenExpanded =
                o.optBoolean("dimBackgroundWhenExpanded", base.dimBackgroundWhenExpanded),
            alwaysShowPill = o.optBoolean("alwaysShowPill", base.alwaysShowPill),
            tapAction = o.enum("tapAction", base.tapAction),
            doubleTapAction = o.enum("doubleTapAction", base.doubleTapAction),
            longPressAction = o.enum("longPressAction", base.longPressAction),
            swipeDownAction = o.enum("swipeDownAction", base.swipeDownAction),
            swipeUpAction = o.enum("swipeUpAction", base.swipeUpAction),
            swipeLeftAction = o.enum("swipeLeftAction", base.swipeLeftAction),
            swipeRightAction = o.enum("swipeRightAction", base.swipeRightAction),
            featureMedia = o.optBoolean("featureMedia", base.featureMedia),
            featureNotifications = o.optBoolean("featureNotifications", base.featureNotifications),
            featureCharging = o.optBoolean("featureCharging", base.featureCharging),
            featureVolume = o.optBoolean("featureVolume", base.featureVolume),
            featureRinger = o.optBoolean("featureRinger", base.featureRinger),
            featureUnlock = o.optBoolean("featureUnlock", base.featureUnlock),
            featureTimer = o.optBoolean("featureTimer", base.featureTimer),
            featurePrivacy = o.optBoolean("featurePrivacy", base.featurePrivacy),
            featureBatteryLow = o.optBoolean("featureBatteryLow", base.featureBatteryLow),
            featureCalls = o.optBoolean("featureCalls", base.featureCalls),
            featureOngoing = o.optBoolean("featureOngoing", base.featureOngoing),
            featureStopwatch = o.optBoolean("featureStopwatch", base.featureStopwatch),
            featureHistory = o.optBoolean("featureHistory", base.featureHistory),
            notificationStyle = o.enum("notificationStyle", base.notificationStyle),
            notificationDurationMs = o.optInt("notificationDurationMs", base.notificationDurationMs),
            blockedPackages = strings("blockedPackages") ?: base.blockedPackages,
            autoExpandPackages = strings("autoExpandPackages") ?: base.autoExpandPackages,
            quickReplyEnabled = o.optBoolean("quickReplyEnabled", base.quickReplyEnabled),
            otpDetection = o.optBoolean("otpDetection", base.otpDetection),
            autoExpandOtp = o.optBoolean("autoExpandOtp", base.autoExpandOtp),
            autoExpandCalls = o.optBoolean("autoExpandCalls", base.autoExpandCalls),
            quietHoursEnabled = o.optBoolean("quietHoursEnabled", base.quietHoursEnabled),
            quietStartMinutes = o.optInt("quietStartMinutes", base.quietStartMinutes),
            quietEndMinutes = o.optInt("quietEndMinutes", base.quietEndMinutes),
            suspendWhenScreenOff = o.optBoolean("suspendWhenScreenOff", base.suspendWhenScreenOff),
            respectSystemAnimationScale =
                o.optBoolean("respectSystemAnimationScale", base.respectSystemAnimationScale),
            startOnBoot = o.optBoolean("startOnBoot", base.startOnBoot),
            autoCheckUpdates = o.optBoolean("autoCheckUpdates", base.autoCheckUpdates),
            updateManifestUrl = o.optString("updateManifestUrl", base.updateManifestUrl)
                .ifBlank { base.updateManifestUrl },
        )
    }

    private inline fun <reified T : Enum<T>> JSONObject.enum(key: String, fallback: T): T {
        val raw = optString(key).takeIf { it.isNotBlank() } ?: return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
