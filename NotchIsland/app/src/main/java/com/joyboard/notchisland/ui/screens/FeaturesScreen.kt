package com.joyboard.notchisland.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.data.NotificationStyle
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.components.DropdownRow
import com.joyboard.notchisland.ui.components.NavRow
import com.joyboard.notchisland.ui.components.SectionCard
import com.joyboard.notchisland.ui.components.SliderRow
import com.joyboard.notchisland.ui.components.SwitchRow
import kotlin.math.roundToInt

private fun formatMinutes(minuteOfDay: Int): String {
    val hours = (minuteOfDay / 60) % 24
    val minutes = minuteOfDay % 60
    return String.format(java.util.Locale.US, "%02d:%02d", hours, minutes)
}

@Composable
fun FeaturesScreen(viewModel: MainViewModel, onOpenBlockedApps: () -> Unit) {
    val settings by viewModel.settings.collectAsStateLifecycle()
    val permissions by viewModel.permissions.collectAsStateLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        SectionCard(
            title = "Live activities",
            subtitle = "Pick which events take over the island."
        ) {
            SwitchRow(
                title = "Now playing",
                subtitle = if (permissions.notificationAccess) "Artwork, scrubbing and transport controls"
                else "Needs notification access",
                checked = settings.featureMedia,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureMedia = value) } }
            )
            SwitchRow(
                title = "Notifications",
                subtitle = if (permissions.notificationAccess) "Previews that slide out of the island"
                else "Needs notification access",
                checked = settings.featureNotifications,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureNotifications = value) } }
            )
            SwitchRow(
                title = "Charging",
                subtitle = "Shows the level when you plug in or unplug",
                checked = settings.featureCharging,
                onCheckedChange = { value -> viewModel.update { it.copy(featureCharging = value) } }
            )
            SwitchRow(
                title = "Low battery",
                subtitle = "A red warning under 15%",
                checked = settings.featureBatteryLow,
                onCheckedChange = { value -> viewModel.update { it.copy(featureBatteryLow = value) } }
            )
            SwitchRow(
                title = "Volume",
                subtitle = "Replaces nothing — it just adds a readout",
                checked = settings.featureVolume,
                onCheckedChange = { value -> viewModel.update { it.copy(featureVolume = value) } }
            )
            SwitchRow(
                title = "Ringer mode",
                subtitle = "Ring, vibrate and silent changes",
                checked = settings.featureRinger,
                onCheckedChange = { value -> viewModel.update { it.copy(featureRinger = value) } }
            )
            SwitchRow(
                title = "Unlock",
                subtitle = "A short confirmation after you unlock",
                checked = settings.featureUnlock,
                onCheckedChange = { value -> viewModel.update { it.copy(featureUnlock = value) } }
            )
            SwitchRow(
                title = "Timers",
                subtitle = "Countdowns live in the island until they finish",
                checked = settings.featureTimer,
                onCheckedChange = { value -> viewModel.update { it.copy(featureTimer = value) } }
            )
            SwitchRow(
                title = "Privacy indicators",
                subtitle = "A green dot when the mic or camera goes live",
                checked = settings.featurePrivacy,
                onCheckedChange = { value -> viewModel.update { it.copy(featurePrivacy = value) } }
            )
            SwitchRow(
                title = "Calls",
                subtitle = "Ringing and connected calls take over the island, with the caller's own answer and hang-up buttons",
                checked = settings.featureCalls,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureCalls = value) } }
            )
            SwitchRow(
                title = "Ongoing activities",
                subtitle = "Navigation, downloads, deliveries and recordings stay in the island with their progress",
                checked = settings.featureOngoing,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureOngoing = value) } }
            )
            SwitchRow(
                title = "Stopwatch",
                subtitle = "Laps and all, live in the island",
                checked = settings.featureStopwatch,
                onCheckedChange = { value -> viewModel.update { it.copy(featureStopwatch = value) } }
            )
            SwitchRow(
                title = "Recent notifications",
                subtitle = "Keeps the last dozen so you can pull them back up",
                checked = settings.featureHistory,
                onCheckedChange = { value -> viewModel.update { it.copy(featureHistory = value) } }
            )
        }

        SectionCard(
            title = "Smart handling",
            subtitle = "What the island does with a notification beyond showing it."
        ) {
            SwitchRow(
                title = "Quick reply",
                subtitle = "Answer a message from the island; the keyboard opens over it",
                checked = settings.quickReplyEnabled,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(quickReplyEnabled = value) }
                }
            )
            SwitchRow(
                title = "Find passcodes",
                subtitle = "Spots one-time codes and offers a single tap to copy",
                checked = settings.otpDetection,
                onCheckedChange = { value -> viewModel.update { it.copy(otpDetection = value) } }
            )
            if (settings.otpDetection) {
                SwitchRow(
                    title = "Open for a passcode",
                    subtitle = "Expands on its own when a code arrives",
                    checked = settings.autoExpandOtp,
                    onCheckedChange = { value ->
                        viewModel.update { it.copy(autoExpandOtp = value) }
                    }
                )
            }
            SwitchRow(
                title = "Open for a call",
                checked = settings.autoExpandCalls,
                onCheckedChange = { value -> viewModel.update { it.copy(autoExpandCalls = value) } }
            )
        }

        SectionCard(
            title = "Quiet hours",
            subtitle = "The island steps aside for a stretch of the day."
        ) {
            SwitchRow(
                title = "Quiet hours",
                checked = settings.quietHoursEnabled,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(quietHoursEnabled = value) }
                }
            )
            if (settings.quietHoursEnabled) {
                SliderRow(
                    title = "From",
                    value = settings.quietStartMinutes / 15f,
                    range = 0f..95f,
                    valueLabel = formatMinutes(settings.quietStartMinutes),
                    onValueChange = { value ->
                        viewModel.update { it.copy(quietStartMinutes = (value.roundToInt() * 15) % 1440) }
                    }
                )
                SliderRow(
                    title = "Until",
                    value = settings.quietEndMinutes / 15f,
                    range = 0f..95f,
                    valueLabel = formatMinutes(settings.quietEndMinutes),
                    onValueChange = { value ->
                        viewModel.update { it.copy(quietEndMinutes = (value.roundToInt() * 15) % 1440) }
                    }
                )
            }
            SwitchRow(
                title = "Rest while the screen is off",
                subtitle = "Stops watching media and sensors until the screen comes back",
                checked = settings.suspendWhenScreenOff,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(suspendWhenScreenOff = value) }
                }
            )
        }

        SectionCard(title = "Notifications") {
            DropdownRow(
                title = "Preview style",
                selected = settings.notificationStyle,
                options = NotificationStyle.entries.toList(),
                label = {
                    when (it) {
                        NotificationStyle.PREVIEW -> "Title preview"
                        NotificationStyle.MINIMAL -> "App name only"
                        NotificationStyle.ICON_ONLY -> "Icon only"
                    }
                },
                onSelected = { value -> viewModel.update { it.copy(notificationStyle = value) } }
            )
            SliderRow(
                title = "How long previews stay",
                value = settings.notificationDurationMs / 1000f,
                range = 1.5f..12f,
                valueLabel = "${(settings.notificationDurationMs / 1000f * 10).roundToInt() / 10f}s",
                onValueChange = { value ->
                    viewModel.update { it.copy(notificationDurationMs = (value * 1000).roundToInt()) }
                }
            )
            NavRow(
                title = "Per-app rules",
                subtitle = buildString {
                    append(
                        if (settings.blockedPackages.isEmpty()) "Nothing blocked"
                        else "${settings.blockedPackages.size} blocked"
                    )
                    if (settings.autoExpandPackages.isNotEmpty()) {
                        append(" · ${settings.autoExpandPackages.size} auto-expand")
                    }
                },
                onClick = onOpenBlockedApps
            )
        }

        SectionCard(title = "Behaviour") {
            SwitchRow(
                title = "Always show the pill",
                subtitle = "Keep a resting island when nothing is happening",
                checked = settings.alwaysShowPill,
                onCheckedChange = { value -> viewModel.update { it.copy(alwaysShowPill = value) } }
            )
            SwitchRow(
                title = "Hide in landscape",
                subtitle = "Stay out of the way in games and video",
                checked = settings.hideInLandscape,
                onCheckedChange = { value -> viewModel.update { it.copy(hideInLandscape = value) } }
            )
            SwitchRow(
                title = "Show on the lock screen",
                checked = settings.showOnLockScreen,
                onCheckedChange = { value -> viewModel.update { it.copy(showOnLockScreen = value) } }
            )
            SwitchRow(
                title = "Dim the screen when expanded",
                checked = settings.dimBackgroundWhenExpanded,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(dimBackgroundWhenExpanded = value) }
                }
            )
            SwitchRow(
                title = "Start on boot",
                subtitle = "Bring the island back after a restart",
                checked = settings.startOnBoot,
                onCheckedChange = { value -> viewModel.update { it.copy(startOnBoot = value) } }
            )
        }
    }
}
