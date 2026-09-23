package com.joyboard.notchisland.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.data.ThemeMode
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.components.ActionRow
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

        SectionCard(title = "Position") {
            SwitchRow(
                title = "Keep clear of the status bar",
                subtitle = "Android layers overlays under the status bar. Turn this off to make " +
                    "the dock/island show above the notification bar over the camera cutout " +
                    "(note: status bar swallows touch inside its band).",
                checked = settings.avoidStatusBar,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(avoidStatusBar = value) }
                }
            )
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

        SectionCard(title = "Colour") {
            val materialYouPrimary = MaterialTheme.colorScheme.primary
            ActionRow(
                label = "Auto-match Material You theme color",
                onClick = {
                    val colorInt = materialYouPrimary.toArgb()
                    viewModel.update { it.copy(accentColor = colorInt) }
                }
            )
            ColorRow(
                title = "Island background",
                selected = settings.backgroundColor,
                onSelected = { value -> viewModel.update { it.copy(backgroundColor = value) } }
            )
            ColorRow(
                title = "Accent",
                selected = settings.accentColor,
                onSelected = { value -> viewModel.update { it.copy(accentColor = value) } }
            )
            SliderRow(
                title = "Opacity",
                value = settings.opacity,
                range = 0.2f..1f,
                valueLabel = "${(settings.opacity * 100).roundToInt()}%",
                onValueChange = { value -> viewModel.update { it.copy(opacity = value) } }
            )
            SwitchRow(
                title = "Tint from album art",
                subtitle = "Borrow the accent from whatever is playing",
                checked = settings.tintFromArtwork,
                onCheckedChange = { value -> viewModel.update { it.copy(tintFromArtwork = value) } }
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
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
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
