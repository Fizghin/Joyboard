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
import com.joyboard.notchisland.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

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
            title = stringResource(R.string.live_activities),
            subtitle = stringResource(R.string.pick_which_events_take_over)
        ) {
            SwitchRow(
                title = stringResource(R.string.now_playing),
                subtitle = if (permissions.notificationAccess) stringResource(R.string.artwork_scrubbing_transport_controls)
                else stringResource(R.string.needs_notification_access),
                checked = settings.featureMedia,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureMedia = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.notifications),
                subtitle = if (permissions.notificationAccess) stringResource(R.string.previews_slide_out_island)
                else stringResource(R.string.needs_notification_access),
                checked = settings.featureNotifications,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureNotifications = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.charging),
                subtitle = stringResource(R.string.shows_level_when_you_plug),
                checked = settings.featureCharging,
                onCheckedChange = { value -> viewModel.update { it.copy(featureCharging = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.low_battery),
                subtitle = stringResource(R.string.red_warning_under_15),
                checked = settings.featureBatteryLow,
                onCheckedChange = { value -> viewModel.update { it.copy(featureBatteryLow = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.volume),
                subtitle = stringResource(R.string.replaces_nothing_just_adds_readout),
                checked = settings.featureVolume,
                onCheckedChange = { value -> viewModel.update { it.copy(featureVolume = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.ringer_mode),
                subtitle = stringResource(R.string.ring_vibrate_silent_changes),
                checked = settings.featureRinger,
                onCheckedChange = { value -> viewModel.update { it.copy(featureRinger = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.unlock),
                subtitle = stringResource(R.string.short_confirmation_after_you_unlock),
                checked = settings.featureUnlock,
                onCheckedChange = { value -> viewModel.update { it.copy(featureUnlock = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.timers),
                subtitle = stringResource(R.string.countdowns_live_island_until_they),
                checked = settings.featureTimer,
                onCheckedChange = { value -> viewModel.update { it.copy(featureTimer = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.privacy_indicators),
                subtitle = stringResource(R.string.green_dot_when_mic_or),
                checked = settings.featurePrivacy,
                onCheckedChange = { value -> viewModel.update { it.copy(featurePrivacy = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.calls),
                subtitle = stringResource(R.string.ringing_connected_calls_take_over),
                checked = settings.featureCalls,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureCalls = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.ongoing_activities),
                subtitle = stringResource(R.string.navigation_downloads_deliveries_recordings_stay),
                checked = settings.featureOngoing,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureOngoing = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.stopwatch),
                subtitle = stringResource(R.string.laps_all_live_island),
                checked = settings.featureStopwatch,
                onCheckedChange = { value -> viewModel.update { it.copy(featureStopwatch = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.recent_notifications),
                subtitle = stringResource(R.string.keeps_last_dozen_you_can),
                checked = settings.featureHistory,
                onCheckedChange = { value -> viewModel.update { it.copy(featureHistory = value) } }
            )
        }

        SectionCard(
            title = stringResource(R.string.smart_handling),
            subtitle = stringResource(R.string.what_island_does_with_notification)
        ) {
            SwitchRow(
                title = stringResource(R.string.quick_reply),
                subtitle = stringResource(R.string.answer_message_from_island_keyboard),
                checked = settings.quickReplyEnabled,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(quickReplyEnabled = value) }
                }
            )
            SwitchRow(
                title = stringResource(R.string.find_passcodes),
                subtitle = if (android.os.Build.VERSION.SDK_INT >= 35) {
                    stringResource(R.string.spots_one_time_codes_offers)
                } else {
                    stringResource(R.string.spots_one_time_codes_offers_2)
                },
                checked = settings.otpDetection,
                onCheckedChange = { value -> viewModel.update { it.copy(otpDetection = value) } }
            )
            if (settings.otpDetection) {
                SwitchRow(
                    title = stringResource(R.string.open_passcode),
                    subtitle = stringResource(R.string.expands_its_own_when_code),
                    checked = settings.autoExpandOtp,
                    onCheckedChange = { value ->
                        viewModel.update { it.copy(autoExpandOtp = value) }
                    }
                )
            }
            SwitchRow(
                title = stringResource(R.string.open_call),
                checked = settings.autoExpandCalls,
                onCheckedChange = { value -> viewModel.update { it.copy(autoExpandCalls = value) } }
            )
        }

        SectionCard(
            title = stringResource(R.string.quiet_hours),
            subtitle = stringResource(R.string.island_steps_aside_stretch_day)
        ) {
            SwitchRow(
                title = stringResource(R.string.quiet_hours),
                checked = settings.quietHoursEnabled,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(quietHoursEnabled = value) }
                }
            )
            if (settings.quietHoursEnabled) {
                SliderRow(
                    title = stringResource(R.string.from),
                    value = settings.quietStartMinutes / 15f,
                    range = 0f..95f,
                    valueLabel = formatMinutes(settings.quietStartMinutes),
                    onValueChange = { value ->
                        viewModel.update { it.copy(quietStartMinutes = (value.roundToInt() * 15) % 1440) }
                    }
                )
                SliderRow(
                    title = stringResource(R.string.until),
                    value = settings.quietEndMinutes / 15f,
                    range = 0f..95f,
                    valueLabel = formatMinutes(settings.quietEndMinutes),
                    onValueChange = { value ->
                        viewModel.update { it.copy(quietEndMinutes = (value.roundToInt() * 15) % 1440) }
                    }
                )
            }
            SwitchRow(
                title = stringResource(R.string.rest_while_screen_off),
                subtitle = stringResource(R.string.stops_watching_media_sensors_until),
                checked = settings.suspendWhenScreenOff,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(suspendWhenScreenOff = value) }
                }
            )
        }

        SectionCard(title = stringResource(R.string.notifications)) {
            DropdownRow(
                title = stringResource(R.string.preview_style),
                selected = settings.notificationStyle,
                options = NotificationStyle.entries.toList(),
                label = {
                    when (it) {
                        NotificationStyle.PREVIEW -> stringResource(R.string.title_preview)
                        NotificationStyle.MINIMAL -> stringResource(R.string.app_name_only)
                        NotificationStyle.ICON_ONLY -> stringResource(R.string.icon_only)
                    }
                },
                onSelected = { value -> viewModel.update { it.copy(notificationStyle = value) } }
            )
            SliderRow(
                title = stringResource(R.string.how_long_previews_stay),
                value = settings.notificationDurationMs / 1000f,
                range = 1.5f..12f,
                valueLabel = "${(settings.notificationDurationMs / 1000f * 10).roundToInt() / 10f}s",
                onValueChange = { value ->
                    viewModel.update { it.copy(notificationDurationMs = (value * 1000).roundToInt()) }
                }
            )
            SwitchRow(
                title = stringResource(R.string.show_silent_notifications),
                subtitle = stringResource(R.string.off_means_only_notifications_would),
                checked = settings.silentNotifications,
                onCheckedChange = { value -> viewModel.update { it.copy(silentNotifications = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.follow_do_not_disturb),
                subtitle = stringResource(R.string.when_do_not_disturb_keeps),
                checked = settings.respectDoNotDisturb,
                onCheckedChange = { value -> viewModel.update { it.copy(respectDoNotDisturb = value) } }
            )
            NavRow(
                title = stringResource(R.string.per_app_rules),
                subtitle = buildString {
                    append(
                        if (settings.blockedPackages.isEmpty()) stringResource(R.string.nothing_blocked)
                        else pluralStringResource(R.plurals.blocked_apps, settings.blockedPackages.size, settings.blockedPackages.size)
                    )
                    if (settings.autoExpandPackages.isNotEmpty()) {
                        append(pluralStringResource(R.plurals.auto_expand_apps, settings.autoExpandPackages.size, settings.autoExpandPackages.size))
                    }
                },
                onClick = onOpenBlockedApps
            )
        }

        SectionCard(title = stringResource(R.string.behaviour)) {
            SwitchRow(
                title = stringResource(R.string.always_show_pill),
                subtitle = stringResource(R.string.keep_resting_island_when_nothing),
                checked = settings.alwaysShowPill,
                onCheckedChange = { value -> viewModel.update { it.copy(alwaysShowPill = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.hide_landscape),
                subtitle = stringResource(R.string.stay_out_way_games_video),
                checked = settings.hideInLandscape,
                onCheckedChange = { value -> viewModel.update { it.copy(hideInLandscape = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.show_lock_screen),
                checked = settings.showOnLockScreen,
                onCheckedChange = { value -> viewModel.update { it.copy(showOnLockScreen = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.dim_screen_when_expanded),
                checked = settings.dimBackgroundWhenExpanded,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(dimBackgroundWhenExpanded = value) }
                }
            )
            SwitchRow(
                title = stringResource(R.string.start_boot),
                subtitle = stringResource(R.string.bring_island_back_after_restart),
                checked = settings.startOnBoot,
                onCheckedChange = { value -> viewModel.update { it.copy(startOnBoot = value) } }
            )
        }
    }
}
