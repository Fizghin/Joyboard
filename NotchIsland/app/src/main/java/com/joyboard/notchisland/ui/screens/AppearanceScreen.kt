package com.joyboard.notchisland.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.data.ColorSource
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

@Composable
fun AppearanceScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateLifecycle()
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

        SectionCard(title = "Size", subtitle = "Match your phone's camera cutout.") {
            SliderRow(
                title = "Resting width",
                value = settings.collapsedWidth.toFloat(),
                range = 60f..260f,
                valueLabel = "${settings.collapsedWidth} dp",
                onValueChange = { value ->
                    viewModel.update { it.copy(collapsedWidth = value.roundToInt()) }
                }
            )
            SliderRow(
                title = "Height",
                value = settings.collapsedHeight.toFloat(),
                range = 18f..64f,
                valueLabel = "${settings.collapsedHeight} dp",
                onValueChange = { value ->
                    viewModel.update { it.copy(collapsedHeight = value.roundToInt()) }
                }
            )
            SliderRow(
                title = "Corner radius",
                value = settings.cornerRadius.toFloat(),
                range = 0f..40f,
                valueLabel = "${settings.cornerRadius} dp",
                onValueChange = { value ->
                    viewModel.update { it.copy(cornerRadius = value.roundToInt()) }
                }
            )
            SliderRow(
                title = "Compact width",
                value = settings.compactWidth.toFloat(),
                range = 120f..320f,
                valueLabel = "${settings.compactWidth} dp",
                onValueChange = { value ->
                    viewModel.update { it.copy(compactWidth = value.roundToInt()) }
                }
            )
            SliderRow(
                title = "Expanded width",
                value = settings.expandedWidth.toFloat(),
                range = 240f..420f,
                valueLabel = "${settings.expandedWidth} dp",
                onValueChange = { value ->
                    viewModel.update { it.copy(expandedWidth = value.roundToInt()) }
                }
            )
        }

        SectionCard(
            title = "Position",
            subtitle = "Where the island sits decides what can be touched."
        ) {
            DropdownRow(
                title = "Anchor",
                selected = settings.positionMode,
                options = PositionMode.entries.toList(),
                label = { it.label },
                onSelected = { value -> viewModel.update { it.copy(positionMode = value) } }
            )
            Text(
                when (settings.positionMode) {
                    PositionMode.BELOW_STATUS_BAR ->
                        "The whole island is tappable. Safest, and what most people want."
                    PositionMode.OVERLAP_STATUS_BAR ->
                        "The island is drawn up in the status bar for the notch look. Android " +
                            "never delivers touches there, so a transparent strip hangs just " +
                            "below it to catch them — the island looks like it comes from above " +
                            "and still responds."
                    PositionMode.CUSTOM ->
                        "Only the offsets below decide where it goes. Anything sitting inside " +
                            "the status bar will not receive touches."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
            if (settings.positionMode == PositionMode.OVERLAP_STATUS_BAR) {
                SliderRow(
                    title = "Touch strip",
                    value = settings.touchStripHeight.toFloat(),
                    range = 8f..48f,
                    valueLabel = "${settings.touchStripHeight} dp",
                    onValueChange = { value ->
                        viewModel.update { it.copy(touchStripHeight = value.roundToInt()) }
                    }
                )
                SwitchRow(
                    title = "Show a hint under the island",
                    subtitle = "A faint handle marking where taps land",
                    checked = settings.showTouchHint,
                    onCheckedChange = { value ->
                        viewModel.update { it.copy(showTouchHint = value) }
                    }
                )
            }
            SliderRow(
                title = "Horizontal offset",
                value = settings.offsetX.toFloat(),
                range = -120f..120f,
                valueLabel = "${settings.offsetX} dp",
                onValueChange = { value ->
                    viewModel.update { it.copy(offsetX = value.roundToInt()) }
                }
            )
            SliderRow(
                title = "Vertical offset",
                value = settings.offsetY.toFloat(),
                range = 0f..90f,
                valueLabel = "${settings.offsetY} dp",
                onValueChange = { value ->
                    viewModel.update { it.copy(offsetY = value.roundToInt()) }
                }
            )
        }

        SectionCard(
            title = "Colour",
            subtitle = if (DynamicColors.supported) "Material You pulls these from your wallpaper."
            else "Material You needs Android 12 or newer, so the presets stand in for it."
        ) {
            DropdownRow(
                title = "Accent",
                selected = settings.accentSource,
                options = ColorSource.entries.toList(),
                label = { it.label },
                onSelected = { value -> viewModel.update { it.copy(accentSource = value) } }
            )
            if (settings.accentSource == ColorSource.MANUAL) {
                ColorRow(
                    title = "Accent colour",
                    selected = settings.accentColor,
                    onSelected = { value -> viewModel.update { it.copy(accentColor = value) } }
                )
            }
            DropdownRow(
                title = "Island body",
                selected = settings.backgroundSource,
                options = listOf(ColorSource.MANUAL, ColorSource.MATERIAL_YOU),
                label = { it.label },
                onSelected = { value -> viewModel.update { it.copy(backgroundSource = value) } }
            )
            if (settings.backgroundSource == ColorSource.MANUAL) {
                ColorRow(
                    title = "Body colour",
                    selected = settings.backgroundColor,
                    onSelected = { value -> viewModel.update { it.copy(backgroundColor = value) } }
                )
            }
            SliderRow(
                title = "Opacity",
                value = settings.opacity,
                range = 0.2f..1f,
                valueLabel = "${(settings.opacity * 100).roundToInt()}%",
                onValueChange = { value -> viewModel.update { it.copy(opacity = value) } }
            )
            SwitchRow(
                title = "Outline",
                checked = settings.borderEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(borderEnabled = value) } }
            )
            if (settings.borderEnabled) {
                ColorRow(
                    title = "Outline colour",
                    selected = settings.borderColor,
                    onSelected = { value -> viewModel.update { it.copy(borderColor = value) } }
                )
                SliderRow(
                    title = "Outline width",
                    value = settings.borderWidth.toFloat(),
                    range = 1f..6f,
                    steps = 4,
                    valueLabel = "${settings.borderWidth} dp",
                    onValueChange = { value ->
                        viewModel.update { it.copy(borderWidth = value.roundToInt()) }
                    }
                )
            }
            SwitchRow(
                title = "Drop shadow",
                checked = settings.shadowEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(shadowEnabled = value) } }
            )
        }

        SectionCard(title = "Motion and theme") {
            SliderRow(
                title = "Animation speed",
                value = settings.animationSpeed,
                range = 0.5f..2f,
                valueLabel = "${(settings.animationSpeed * 10).roundToInt() / 10f}x",
                onValueChange = { value -> viewModel.update { it.copy(animationSpeed = value) } }
            )
            DropdownRow(
                title = "App theme",
                selected = settings.themeMode,
                options = ThemeMode.entries.toList(),
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> "Follow system"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                },
                onSelected = { value -> viewModel.update { it.copy(themeMode = value) } }
            )
        }

        SectionCard(title = "Apply") {
            Text(
                "Size and position changes settle immediately, but restarting the overlay gives " +
                    "the window a clean slate if anything looks off.",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { viewModel.restartOverlay() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) { Text("Restart overlay") }
            OutlinedButton(
                onClick = { viewModel.resetToDefaults() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            ) { Text("Reset everything to defaults") }
        }
    }
}
