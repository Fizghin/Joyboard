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
                title = "Blocked apps",
                subtitle = if (settings.blockedPackages.isEmpty()) "Nothing blocked"
                else "${settings.blockedPackages.size} app(s) hidden from the island",
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
