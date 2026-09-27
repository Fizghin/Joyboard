package com.joyboard.notchisland.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.joyboard.notchisland.CalibrationActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.joyboard.notchisland.data.CameraSource
import com.joyboard.notchisland.data.ColorSource
import com.joyboard.notchisland.data.DevicePreset
import com.joyboard.notchisland.data.DevicePresets
import com.joyboard.notchisland.data.IslandPreset
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.data.ThemeMode
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.util.DynamicColors
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.components.ColorRow
import com.joyboard.notchisland.ui.components.DropdownRow
import com.joyboard.notchisland.ui.components.IslandPreview
import com.joyboard.notchisland.ui.components.SectionCard
import com.joyboard.notchisland.ui.components.SliderRow
import com.joyboard.notchisland.ui.components.SwitchRow
import kotlin.math.roundToInt
import com.joyboard.notchisland.R
import androidx.compose.ui.res.stringResource
import com.joyboard.notchisland.BuildConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import com.joyboard.notchisland.ui.theme.Tint
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LineWeight
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewCompact
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WidthNormal
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt

@Composable
fun AppearanceScreen(viewModel: MainViewModel) {
    val permissions by viewModel.permissions.collectAsStateLifecycle()
    val settings by viewModel.settings.collectAsStateLifecycle()
    val context = LocalContext.current
    var previewMode by remember { mutableStateOf(IslandMode.COMPACT) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        IslandPreview(
            settings = settings,
            mode = previewMode,
            onModeChange = { previewMode = it },
        )

        SectionCard(
            title = stringResource(R.string.shape),
            subtitle = stringResource(R.string.start_from_iphone_island_then)
        ) {
            DropdownRow(
                title = stringResource(R.string.style),
                icon = Icons.Rounded.PhoneIphone,
                tint = Tint.Gray,
                selected = settings.preset,
                options = IslandPreset.entries.toList(),
                label = { stringResource(it.label) },
                onSelected = { value -> viewModel.applyPreset(value) }
            )
            SwitchRow(
                title = stringResource(R.string.ios_mode),
                icon = Icons.Rounded.AutoAwesome,
                tint = Tint.Blue,
                subtitle = stringResource(R.string.pure_black_fully_rounded_44),
                checked = settings.iosMode,
                onCheckedChange = { value -> viewModel.update { it.copy(iosMode = value) } }
            )
        }

        SectionCard(
            title = stringResource(R.string.phone_camera),
            subtitle = stringResource(R.string.camera_hardware_island_keeps_its)
        ) {
            val suggested = viewModel.suggestedDevice
            if (suggested != null && settings.devicePresetId != suggested.id) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.looks_like, suggested.name),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.applyDevicePreset(suggested) }) { Text(stringResource(R.string.use)) }
                }
            }
            DropdownRow(
                title = stringResource(R.string.phone),
                icon = Icons.Rounded.Smartphone,
                tint = Tint.Blue,
                selected = DevicePresets.byId(settings.devicePresetId),
                options = listOf<DevicePreset?>(null) + DevicePresets.all,
                label = { preset ->
                    when {
                        preset == null -> stringResource(R.string.choose)
                        preset.maker == "Any phone" -> preset.name
                        else -> "${preset.maker} ${preset.name}".replace("Google Pixel", "Pixel")
                            .replace("Honor Honor", "Honor").replace("OnePlus OnePlus", "OnePlus")
                    }
                },
                onSelected = { preset -> preset?.let { viewModel.applyDevicePreset(it) } }
            )
            Text(
                when (settings.cameraSource) {
                    CameraSource.NONE -> stringResource(R.string.no_camera_position_set_yet)
                    else -> stringResource(R.string.dp_dp_down, stringResource(settings.cameraSource.label), settings.holeWidth.roundToInt(), settings.holeHeight.roundToInt(), settings.holeCenterY.roundToInt()) +
                        when {
                            settings.holeCenterX < -1f -> stringResource(R.string.dp_left_centre, (-settings.holeCenterX).roundToInt())
                            settings.holeCenterX > 1f -> stringResource(R.string.dp_right_centre, settings.holeCenterX.roundToInt())
                            else -> stringResource(R.string.centred)
                        }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { viewModel.detectHole() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.detect_short), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { context.startActivity(Intent(context, CalibrationActivity::class.java)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.calibrate), maxLines = 1)
                }
            }
            Text(
                stringResource(R.string.presets_place_camera_from_phone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            SwitchRow(
                title = stringResource(R.string.keep_content_clear_camera),
                icon = Icons.Rounded.CenterFocusStrong,
                tint = Tint.Teal,
                subtitle = stringResource(R.string.icons_text_move_aside_instead),
                checked = settings.avoidHole,
                onCheckedChange = { value -> viewModel.update { it.copy(avoidHole = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.draw_lens_over_camera),
                icon = Icons.Rounded.CameraAlt,
                tint = Tint.Gray,
                subtitle = stringResource(R.string.dark_circle_exactly_over_hole),
                checked = settings.showFauxCamera,
                onCheckedChange = { value -> viewModel.update { it.copy(showFauxCamera = value) } }
            )
            if (settings.cameraSource != CameraSource.NONE) {
                TextButton(
                    onClick = { viewModel.clearHole() },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) { Text(stringResource(R.string.forget_camera_position)) }
            }
        }

        SectionCard(title = stringResource(R.string.size), subtitle = stringResource(R.string.match_phone_camera_cutout)) {
            FilledTonalButton(
                onClick = { viewModel.fitToCutout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.fit_my_camera), maxLines = 1)
                }
            Text(
                stringResource(R.string.asks_phone_where_its_camera),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
            SliderRow(
                title = stringResource(R.string.resting_width),
                icon = Icons.Rounded.WidthNormal,
                tint = Tint.Blue,
                value = settings.collapsedWidth.toFloat(),
                range = 60f..260f,
                valueLabel = stringResource(R.string.dp, settings.collapsedWidth),
                onValueChange = { value ->
                    viewModel.update { it.copy(collapsedWidth = value.roundToInt()) }
                }
            )
            SliderRow(
                title = stringResource(R.string.height),
                icon = Icons.Rounded.Height,
                tint = Tint.Blue,
                value = settings.collapsedHeight.toFloat(),
                range = 18f..64f,
                valueLabel = stringResource(R.string.dp, settings.collapsedHeight),
                onValueChange = { value ->
                    viewModel.update { it.copy(collapsedHeight = value.roundToInt()) }
                }
            )
            SliderRow(
                title = stringResource(R.string.corner_radius),
                icon = Icons.Rounded.RoundedCorner,
                tint = Tint.Blue,
                value = settings.cornerRadius.toFloat(),
                range = 0f..40f,
                valueLabel = stringResource(R.string.dp, settings.cornerRadius),
                onValueChange = { value ->
                    viewModel.update { it.copy(cornerRadius = value.roundToInt()) }
                }
            )
            SliderRow(
                title = stringResource(R.string.compact_width),
                icon = Icons.Rounded.ViewCompact,
                tint = Tint.Blue,
                value = settings.compactWidth.toFloat(),
                range = 120f..320f,
                valueLabel = stringResource(R.string.dp, settings.compactWidth),
                onValueChange = { value ->
                    viewModel.update { it.copy(compactWidth = value.roundToInt()) }
                }
            )
            SliderRow(
                title = stringResource(R.string.small_card_width),
                icon = Icons.Rounded.ViewDay,
                tint = Tint.Blue,
                value = settings.mediumWidth.toFloat(),
                range = 150f..380f,
                valueLabel = stringResource(R.string.dp, settings.mediumWidth),
                onValueChange = { value ->
                    viewModel.update { it.copy(mediumWidth = value.roundToInt()) }
                }
            )
            SliderRow(
                title = stringResource(R.string.expanded_width),
                icon = Icons.Rounded.ViewAgenda,
                tint = Tint.Blue,
                value = settings.expandedWidth.toFloat(),
                range = 240f..420f,
                valueLabel = stringResource(R.string.dp, settings.expandedWidth),
                onValueChange = { value ->
                    viewModel.update { it.copy(expandedWidth = value.roundToInt()) }
                }
            )
        }

        SectionCard(
            title = stringResource(R.string.position),
            subtitle = stringResource(R.string.where_island_sits_decides_what)
        ) {
            // Above the status bar the island takes its own taps, so it needs no touch strip.
            val drawingAbove = BuildConfig.HELPER_AVAILABLE && permissions.helper && settings.drawAboveStatusBar
            if (BuildConfig.HELPER_AVAILABLE) {
                SwitchRow(
                    title = stringResource(R.string.draw_above_status_bar),
                    icon = Icons.Rounded.Layers,
                    tint = Tint.Purple,
                    subtitle = stringResource(
                        if (permissions.helper) R.string.draw_above_status_bar_on
                        else R.string.draw_above_status_bar_needs_helper
                    ),
                    checked = settings.drawAboveStatusBar,
                    onCheckedChange = { value -> viewModel.update { it.copy(drawAboveStatusBar = value) } }
                )
            }
            DropdownRow(
                title = stringResource(R.string.anchor),
                icon = Icons.Rounded.VerticalAlignTop,
                tint = Tint.Purple,
                selected = settings.positionMode,
                options = PositionMode.entries.toList(),
                label = { stringResource(it.label) },
                onSelected = { value -> viewModel.update { it.copy(positionMode = value) } }
            )
            Text(
                when (settings.positionMode) {
                    PositionMode.BELOW_STATUS_BAR ->
                        stringResource(R.string.whole_island_tappable_safest_what)
                    PositionMode.OVERLAP_STATUS_BAR -> if (drawingAbove) {
                        stringResource(R.string.draw_above_status_bar_on)
                    } else {
                        stringResource(R.string.island_drawn_up_status_bar) + " " +
                            stringResource(R.string.icons_draw_over_island)
                    }
                    PositionMode.CUSTOM ->
                        stringResource(R.string.only_offsets_below_decide_where)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
            AnimatedVisibility(visible = settings.positionMode == PositionMode.OVERLAP_STATUS_BAR && !drawingAbove) {
                Column {
                    SliderRow(
                        title = stringResource(R.string.touch_strip),
                        icon = Icons.Rounded.TouchApp,
                        tint = Tint.Purple,
                        value = settings.touchStripHeight.toFloat(),
                        range = 8f..48f,
                        valueLabel = stringResource(R.string.dp, settings.touchStripHeight),
                        onValueChange = { value ->
                            viewModel.update { it.copy(touchStripHeight = value.roundToInt()) }
                        }
                    )
                    SwitchRow(
                        title = stringResource(R.string.show_hint_under_island),
                        icon = Icons.Rounded.Visibility,
                        tint = Tint.Purple,
                        subtitle = stringResource(R.string.faint_handle_marking_where_taps),
                        checked = settings.showTouchHint,
                        onCheckedChange = { value ->
                            viewModel.update { it.copy(showTouchHint = value) }
                        }
                    )
                }
            }
            SliderRow(
                title = stringResource(R.string.horizontal_offset),
                icon = Icons.Rounded.SwapHoriz,
                tint = Tint.Orange,
                value = settings.offsetX.toFloat(),
                range = -120f..120f,
                valueLabel = stringResource(R.string.dp, settings.offsetX),
                onValueChange = { value ->
                    viewModel.update { it.copy(offsetX = value.roundToInt()) }
                }
            )
            SliderRow(
                title = stringResource(R.string.vertical_offset),
                icon = Icons.Rounded.SwapVert,
                tint = Tint.Orange,
                value = settings.offsetY.toFloat(),
                range = 0f..90f,
                valueLabel = stringResource(R.string.dp, settings.offsetY),
                onValueChange = { value ->
                    viewModel.update { it.copy(offsetY = value.roundToInt()) }
                }
            )
        }

        SectionCard(
            title = stringResource(R.string.colour),
            subtitle = if (DynamicColors.supported) stringResource(R.string.material_you_pulls_these_from)
            else stringResource(R.string.material_you_needs_android_12)
        ) {
            DropdownRow(
                title = stringResource(R.string.accent),
                icon = Icons.Rounded.Palette,
                tint = Tint.Pink,
                selected = settings.accentSource,
                options = ColorSource.entries.toList(),
                label = { stringResource(it.label) },
                onSelected = { value -> viewModel.update { it.copy(accentSource = value) } }
            )
            if (settings.accentSource == ColorSource.MANUAL) {
                ColorRow(
                    title = stringResource(R.string.accent_colour),
                    icon = Icons.Rounded.Colorize,
                    tint = Tint.Pink,
                    selected = settings.accentColor,
                    onSelected = { value -> viewModel.update { it.copy(accentColor = value) } }
                )
            }
            DropdownRow(
                title = stringResource(R.string.island_body),
                icon = Icons.Rounded.FormatColorFill,
                tint = Tint.Gray,
                selected = settings.backgroundSource,
                options = listOf(ColorSource.MANUAL, ColorSource.MATERIAL_YOU),
                label = { stringResource(it.label) },
                onSelected = { value -> viewModel.update { it.copy(backgroundSource = value) } }
            )
            if (settings.backgroundSource == ColorSource.MANUAL) {
                ColorRow(
                    title = stringResource(R.string.body_colour),
                    icon = Icons.Rounded.FormatColorFill,
                    tint = Tint.Gray,
                    selected = settings.backgroundColor,
                    onSelected = { value -> viewModel.update { it.copy(backgroundColor = value) } }
                )
            }
            SliderRow(
                title = stringResource(R.string.opacity),
                icon = Icons.Rounded.Opacity,
                tint = Tint.Cyan,
                value = settings.opacity,
                range = 0.2f..1f,
                valueLabel = "${(settings.opacity * 100).roundToInt()}%",
                onValueChange = { value -> viewModel.update { it.copy(opacity = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.outline),
                icon = Icons.Rounded.BorderOuter,
                tint = Tint.Gray,
                checked = settings.borderEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(borderEnabled = value) } }
            )
            if (settings.borderEnabled) {
                ColorRow(
                    title = stringResource(R.string.outline_colour),
                    icon = Icons.Rounded.BorderStyle,
                    tint = Tint.Gray,
                    selected = settings.borderColor,
                    onSelected = { value -> viewModel.update { it.copy(borderColor = value) } }
                )
                SliderRow(
                    title = stringResource(R.string.outline_width),
                    icon = Icons.Rounded.LineWeight,
                    tint = Tint.Gray,
                    value = settings.borderWidth.toFloat(),
                    range = 1f..6f,
                    steps = 4,
                    valueLabel = stringResource(R.string.dp, settings.borderWidth),
                    onValueChange = { value ->
                        viewModel.update { it.copy(borderWidth = value.roundToInt()) }
                    }
                )
            }
            SwitchRow(
                title = stringResource(R.string.drop_shadow),
                icon = Icons.Rounded.Contrast,
                tint = Tint.Gray,
                checked = settings.shadowEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(shadowEnabled = value) } }
            )
        }

        SectionCard(title = stringResource(R.string.motion_theme)) {
            SliderRow(
                title = stringResource(R.string.animation_speed),
                icon = Icons.Rounded.Speed,
                tint = Tint.Indigo,
                value = settings.animationSpeed,
                range = 0.5f..2f,
                valueLabel = "${(settings.animationSpeed * 10).roundToInt() / 10f}x",
                onValueChange = { value -> viewModel.update { it.copy(animationSpeed = value) } }
            )
            SwitchRow(
                title = stringResource(R.string.effects),
                icon = Icons.Rounded.Waves,
                tint = Tint.Purple,
                subtitle = stringResource(R.string.effects_desc),
                checked = settings.effects,
                onCheckedChange = { value -> viewModel.update { it.copy(effects = value) } }
            )
            DropdownRow(
                title = stringResource(R.string.app_theme),
                icon = Icons.Rounded.DarkMode,
                tint = Tint.Indigo,
                selected = settings.themeMode,
                options = ThemeMode.entries.toList(),
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> stringResource(R.string.follow_system)
                        ThemeMode.LIGHT -> stringResource(R.string.light)
                        ThemeMode.DARK -> stringResource(R.string.dark)
                    }
                },
                onSelected = { value -> viewModel.update { it.copy(themeMode = value) } }
            )
        }

        SectionCard(title = stringResource(R.string.apply)) {
            Text(
                stringResource(R.string.size_position_changes_settle_immediately),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(
                onClick = { viewModel.restartOverlay() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.restart_overlay), maxLines = 1)
                }
            // Resetting everything is the one destructive action here, so it reads that way.
            TextButton(
                onClick = { viewModel.resetToDefaults() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_everything_defaults), maxLines = 1)
                }
        }
    }
}
