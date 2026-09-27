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
import com.joyboard.notchisland.BuildConfig
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import com.joyboard.notchisland.island.AutomationRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import com.joyboard.notchisland.ui.theme.Tint
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.joyboard.notchisland.ui.components.NoteDialog

private fun formatMinutes(minuteOfDay: Int): String {
    val hours = (minuteOfDay / 60) % 24
    val minutes = minuteOfDay % 60
    return String.format(java.util.Locale.US, "%02d:%02d", hours, minutes)
}

@Composable
fun FeaturesScreen(viewModel: MainViewModel, onOpenBlockedApps: () -> Unit) {
    val settings by viewModel.settings.collectAsStateLifecycle()
    val permissions by viewModel.permissions.collectAsStateLifecycle()
    var editingNote by remember { mutableStateOf(false) }

    if (editingNote) {
        NoteDialog(
            initial = settings.pinnedNote,
            onDismiss = { editingNote = false },
            onSave = { note ->
                viewModel.update { it.copy(pinnedNote = note) }
                editingNote = false
            }
        )
    }

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
                icon = Icons.Rounded.MusicNote,
                tint = Tint.Pink,
                subtitle = if (permissions.notificationAccess) stringResource(R.string.artwork_scrubbing_transport_controls)
                else stringResource(R.string.needs_notification_access),
                checked = settings.featureMedia,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureMedia = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.synced_lyrics),
                icon = Icons.Rounded.Lyrics,
                tint = Tint.Pink,
                subtitle = stringResource(R.string.synced_lyrics_desc),
                checked = settings.featureLyrics,
                enabled = permissions.notificationAccess && settings.featureMedia,
                onCheckedChange = { value -> viewModel.update { it.copy(featureLyrics = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.notifications),
                icon = Icons.Rounded.Notifications,
                tint = Tint.Red,
                subtitle = if (permissions.notificationAccess) stringResource(R.string.previews_slide_out_island)
                else stringResource(R.string.needs_notification_access),
                checked = settings.featureNotifications,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureNotifications = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.charging),
                icon = Icons.Rounded.BatteryChargingFull,
                tint = Tint.Green,
                subtitle = stringResource(R.string.shows_level_when_you_plug),
                checked = settings.featureCharging,
                onCheckedChange = { value -> viewModel.update { it.copy(featureCharging = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.low_battery),
                icon = Icons.Rounded.BatteryAlert,
                tint = Tint.Red,
                subtitle = stringResource(R.string.red_warning_under_15),
                checked = settings.featureBatteryLow,
                onCheckedChange = { value -> viewModel.update { it.copy(featureBatteryLow = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.battery_heat),
                icon = Icons.Rounded.Thermostat,
                tint = Tint.Red,
                subtitle = stringResource(R.string.battery_heat_desc),
                checked = settings.featureBatteryHeat,
                onCheckedChange = { value -> viewModel.update { it.copy(featureBatteryHeat = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.volume),
                icon = Icons.Rounded.VolumeUp,
                tint = Tint.Blue,
                subtitle = stringResource(R.string.replaces_nothing_just_adds_readout),
                checked = settings.featureVolume,
                onCheckedChange = { value -> viewModel.update { it.copy(featureVolume = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.ringer_mode),
                icon = Icons.Rounded.Vibration,
                tint = Tint.Orange,
                subtitle = stringResource(R.string.ring_vibrate_silent_changes),
                checked = settings.featureRinger,
                onCheckedChange = { value -> viewModel.update { it.copy(featureRinger = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.unlock),
                icon = Icons.Rounded.LockOpen,
                tint = Tint.Teal,
                subtitle = stringResource(R.string.short_confirmation_after_you_unlock),
                checked = settings.featureUnlock,
                onCheckedChange = { value -> viewModel.update { it.copy(featureUnlock = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.timers),
                icon = Icons.Rounded.Timer,
                tint = Tint.Orange,
                subtitle = stringResource(R.string.countdowns_live_island_until_they),
                checked = settings.featureTimer,
                onCheckedChange = { value -> viewModel.update { it.copy(featureTimer = value) } }
            )
            AnimatedVisibility(visible = settings.featureTimer) {
                Column {
                    SwitchRow(
                        title = stringResource(R.string.quick_timers),
                        icon = Icons.Rounded.MoreTime,
                        tint = Tint.Orange,
                        subtitle = stringResource(R.string.quick_timers_desc),
                        checked = settings.quickTimers,
                        onCheckedChange = { value -> viewModel.update { it.copy(quickTimers = value) } }
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.privacy_indicators),
                icon = Icons.Rounded.Mic,
                tint = Tint.Green,
                subtitle = stringResource(R.string.green_dot_when_mic_or),
                checked = settings.featurePrivacy,
                onCheckedChange = { value -> viewModel.update { it.copy(featurePrivacy = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.calls),
                icon = Icons.Rounded.Call,
                tint = Tint.Green,
                subtitle = stringResource(R.string.ringing_connected_calls_take_over),
                checked = settings.featureCalls,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureCalls = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.ongoing_activities),
                icon = Icons.Rounded.Navigation,
                tint = Tint.Blue,
                subtitle = stringResource(R.string.navigation_downloads_deliveries_recordings_stay),
                checked = settings.featureOngoing,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureOngoing = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.navigation),
                icon = Icons.Rounded.Directions,
                tint = Tint.Blue,
                subtitle = if (permissions.notificationAccess) stringResource(R.string.navigation_desc)
                else stringResource(R.string.needs_notification_access),
                checked = settings.featureNavigation,
                enabled = permissions.notificationAccess,
                onCheckedChange = { value -> viewModel.update { it.copy(featureNavigation = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.network_speed),
                icon = Icons.Rounded.Speed,
                tint = Tint.Cyan,
                subtitle = stringResource(R.string.network_speed_desc),
                checked = settings.featureNetworkSpeed,
                onCheckedChange = { value -> viewModel.update { it.copy(featureNetworkSpeed = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.stopwatch),
                icon = Icons.Rounded.AvTimer,
                tint = Tint.Orange,
                subtitle = stringResource(R.string.laps_all_live_island),
                checked = settings.featureStopwatch,
                onCheckedChange = { value -> viewModel.update { it.copy(featureStopwatch = value) } }
            )
            // Asked for in place, so switching this on is the whole setup.
            val calendarPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.refreshPermissions()
                if (granted) viewModel.update { it.copy(featureCalendar = true) }
            }
            SwitchRow(
                title = stringResource(R.string.next_calendar_event),
                icon = Icons.Rounded.CalendarMonth,
                tint = Tint.Red,
                subtitle = stringResource(
                    if (permissions.calendar) R.string.next_calendar_event_desc else R.string.needs_calendar_access
                ),
                checked = settings.featureCalendar && permissions.calendar,
                onCheckedChange = { value ->
                    if (value && !permissions.calendar) {
                        calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    } else {
                        viewModel.update { it.copy(featureCalendar = value) }
                    }
                }
            )
            AnimatedVisibility(visible = settings.featureCalendar && permissions.calendar) {
                Column {
                    SliderRow(
                        title = stringResource(R.string.count_down_from),
                        icon = Icons.Rounded.Timelapse,
                        tint = Tint.Red,
                        value = settings.calendarLeadMinutes.toFloat(),
                        range = 5f..60f,
                        steps = 10,
                        valueLabel = stringResource(R.string.n_min, settings.calendarLeadMinutes),
                        onValueChange = { value ->
                            viewModel.update { it.copy(calendarLeadMinutes = value.roundToInt()) }
                        }
                    )
                }
            }
            val locationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.refreshPermissions()
                if (granted) {
                    viewModel.update { it.copy(featureWeather = true) }
                    viewModel.captureWeatherArea()
                }
            }
            SwitchRow(
                title = stringResource(R.string.weather),
                icon = Icons.Rounded.Cloud,
                tint = Tint.Cyan,
                subtitle = stringResource(
                    when {
                        !settings.featureWeather -> R.string.weather_desc
                        settings.weatherArea.isNotBlank() -> R.string.weather_desc
                        permissions.location -> R.string.weather_finding_area
                        else -> R.string.weather_needs_area
                    }
                ),
                checked = settings.featureWeather,
                onCheckedChange = { value ->
                    when {
                        // Off means forgotten: the stored area goes with it.
                        !value -> viewModel.update { it.copy(featureWeather = false, weatherArea = "") }
                        !permissions.location && settings.weatherArea.isBlank() ->
                            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        else -> {
                            viewModel.update { it.copy(featureWeather = true) }
                            viewModel.captureWeatherArea()
                        }
                    }
                }
            )
            AnimatedVisibility(visible = settings.featureWeather) {
                Column {
                    SwitchRow(
                        title = stringResource(R.string.rain_alerts),
                        icon = Icons.Rounded.Umbrella,
                        tint = Tint.Blue,
                        subtitle = stringResource(R.string.rain_alerts_desc),
                        checked = settings.rainAlerts,
                        onCheckedChange = { value -> viewModel.update { it.copy(rainAlerts = value) } }
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.headphones),
                icon = Icons.Rounded.Headphones,
                tint = Tint.Indigo,
                subtitle = stringResource(R.string.headphones_desc),
                checked = settings.featureHeadphones,
                onCheckedChange = { value -> viewModel.update { it.copy(featureHeadphones = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.flashlight_on),
                icon = Icons.Rounded.FlashlightOn,
                tint = Tint.Yellow,
                subtitle = stringResource(R.string.flashlight_desc),
                checked = settings.featureFlashlight,
                onCheckedChange = { value -> viewModel.update { it.copy(featureFlashlight = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.do_not_disturb),
                icon = Icons.Rounded.DoNotDisturbOn,
                tint = Tint.Indigo,
                subtitle = stringResource(R.string.focus_desc),
                checked = settings.featureFocus,
                onCheckedChange = { value -> viewModel.update { it.copy(featureFocus = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.next_alarm),
                icon = Icons.Rounded.Alarm,
                tint = Tint.Orange,
                subtitle = stringResource(R.string.next_alarm_desc),
                checked = settings.showNextAlarm,
                onCheckedChange = { value -> viewModel.update { it.copy(showNextAlarm = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.recent_notifications),
                icon = Icons.Rounded.History,
                tint = Tint.Gray,
                subtitle = stringResource(R.string.keeps_last_dozen_you_can),
                checked = settings.featureHistory,
                onCheckedChange = { value -> viewModel.update { it.copy(featureHistory = value) } }
            )
            NavRow(
                title = stringResource(R.string.pinned_note),
                icon = Icons.Rounded.NoteAlt,
                tint = Tint.Yellow,
                subtitle = settings.pinnedNote.ifBlank { stringResource(R.string.pinned_note_desc) },
                onClick = { editingNote = true }
            )
        }

        SectionCard(
            title = stringResource(R.string.smart_handling),
            subtitle = stringResource(R.string.what_island_does_with_notification)
        ) {
            SwitchRow(
                title = stringResource(R.string.quick_reply),
                icon = Icons.Rounded.Reply,
                tint = Tint.Blue,
                subtitle = stringResource(R.string.answer_message_from_island_keyboard),
                checked = settings.quickReplyEnabled,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(quickReplyEnabled = value) }
                }
            )
            SwitchRow(
                title = stringResource(R.string.find_passcodes),
                icon = Icons.Rounded.Password,
                tint = Tint.Green,
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
                    icon = Icons.Rounded.OpenInFull,
                    tint = Tint.Green,
                    subtitle = stringResource(R.string.expands_its_own_when_code),
                    checked = settings.autoExpandOtp,
                    onCheckedChange = { value ->
                        viewModel.update { it.copy(autoExpandOtp = value) }
                    }
                )
            }
            SwitchRow(
                title = stringResource(R.string.open_call),
                icon = Icons.Rounded.OpenInFull,
                tint = Tint.Green,
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
                icon = Icons.Rounded.Bedtime,
                tint = Tint.Indigo,
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
                icon = Icons.Rounded.ScreenLockPortrait,
                tint = Tint.Gray,
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
                icon = Icons.Rounded.ViewAgenda,
                tint = Tint.Red,
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
                icon = Icons.Rounded.Timelapse,
                tint = Tint.Red,
                value = settings.notificationDurationMs / 1000f,
                range = 1.5f..12f,
                valueLabel = "${(settings.notificationDurationMs / 1000f * 10).roundToInt() / 10f}s",
                onValueChange = { value ->
                    viewModel.update { it.copy(notificationDurationMs = (value * 1000).roundToInt()) }
                }
            )
            SwitchRow(
                title = stringResource(R.string.show_silent_notifications),
                icon = Icons.Rounded.NotificationsOff,
                tint = Tint.Gray,
                subtitle = stringResource(R.string.off_means_only_notifications_would),
                checked = settings.silentNotifications,
                onCheckedChange = { value -> viewModel.update { it.copy(silentNotifications = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.follow_do_not_disturb),
                icon = Icons.Rounded.DoNotDisturbOn,
                tint = Tint.Indigo,
                subtitle = stringResource(R.string.when_do_not_disturb_keeps),
                checked = settings.respectDoNotDisturb,
                onCheckedChange = { value -> viewModel.update { it.copy(respectDoNotDisturb = value) } }
            )
            NavRow(
                title = stringResource(R.string.per_app_rules),
                icon = Icons.Rounded.Apps,
                tint = Tint.Blue,
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
                title = stringResource(R.string.split_island),
                icon = Icons.Rounded.ViewColumn,
                tint = Tint.Indigo,
                subtitle = stringResource(R.string.split_island_desc),
                checked = settings.splitIsland,
                onCheckedChange = { value -> viewModel.update { it.copy(splitIsland = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.always_show_pill),
                icon = Icons.Rounded.Adjust,
                tint = Tint.Blue,
                subtitle = stringResource(R.string.keep_resting_island_when_nothing),
                checked = settings.alwaysShowPill,
                onCheckedChange = { value -> viewModel.update { it.copy(alwaysShowPill = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.hide_landscape),
                icon = Icons.Rounded.AspectRatio,
                tint = Tint.Gray,
                subtitle = stringResource(R.string.stay_out_way_games_video),
                checked = settings.hideInLandscape,
                onCheckedChange = { value -> viewModel.update { it.copy(hideInLandscape = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.hide_in_fullscreen),
                icon = Icons.Rounded.Fullscreen,
                tint = Tint.Gray,
                subtitle = stringResource(R.string.hide_in_fullscreen_desc),
                checked = settings.hideInFullscreen,
                onCheckedChange = { value -> viewModel.update { it.copy(hideInFullscreen = value) } }
            )
            if (BuildConfig.HELPER_AVAILABLE) {
                SwitchRow(
                    title = stringResource(R.string.hide_while_typing),
                    icon = Icons.Rounded.Keyboard,
                    tint = Tint.Gray,
                    subtitle = stringResource(
                        if (permissions.helper) R.string.hide_while_typing_desc else R.string.needs_helper
                    ),
                    checked = settings.hideWhileTyping,
                    onCheckedChange = { value -> viewModel.update { it.copy(hideWhileTyping = value) } }
                )
            }
            SwitchRow(
                title = stringResource(R.string.show_lock_screen),
                icon = Icons.Rounded.Lock,
                tint = Tint.Teal,
                checked = settings.showOnLockScreen,
                onCheckedChange = { value -> viewModel.update { it.copy(showOnLockScreen = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.dim_screen_when_expanded),
                icon = Icons.Rounded.Brightness6,
                tint = Tint.Gray,
                checked = settings.dimBackgroundWhenExpanded,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(dimBackgroundWhenExpanded = value) }
                }
            )
            SwitchRow(
                title = stringResource(R.string.start_boot),
                icon = Icons.Rounded.PowerSettingsNew,
                tint = Tint.Green,
                subtitle = stringResource(R.string.bring_island_back_after_restart),
                checked = settings.startOnBoot,
                onCheckedChange = { value -> viewModel.update { it.copy(startOnBoot = value) } }
            )
        }

        SectionCard(
            title = stringResource(R.string.automation),
            subtitle = stringResource(R.string.automation_desc)
        ) {
            SwitchRow(
                title = stringResource(R.string.allow_other_apps),
                icon = Icons.Rounded.Webhook,
                tint = Tint.Purple,
                subtitle = stringResource(R.string.allow_other_apps_desc),
                checked = settings.externalApiEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(externalApiEnabled = value) } }
            )
            AnimatedVisibility(visible = settings.externalApiEnabled) {
                Column {
                    Text(
                        stringResource(R.string.automation_how),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                    val context = LocalContext.current
                    val testTitle = stringResource(R.string.test_message_title)
                    val testText = stringResource(R.string.test_message_text)
                    FilledTonalButton(
                        onClick = {
                            context.sendBroadcast(
                                Intent(AutomationRequest.ACTION_SHOW)
                                    .setPackage(context.packageName)
                                    .putExtra(AutomationRequest.EXTRA_TITLE, testTitle)
                                    .putExtra(AutomationRequest.EXTRA_TEXT, testText)
                                    .putExtra(AutomationRequest.EXTRA_ICON, "island")
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.send_test_message))
                    }
                }
            }
        }
    }
}
