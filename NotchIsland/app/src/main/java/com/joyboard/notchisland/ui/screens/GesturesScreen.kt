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
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.components.DropdownRow
import com.joyboard.notchisland.ui.components.SectionCard
import com.joyboard.notchisland.ui.components.SliderRow
import com.joyboard.notchisland.ui.components.SwitchRow
import kotlin.math.roundToInt

@Composable
fun GesturesScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateLifecycle()
    val actions = GestureAction.entries.toList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        SectionCard(
            title = "Gestures",
            subtitle = "Every touch on the island can be remapped."
        ) {
            DropdownRow("Tap", settings.tapAction, actions, { it.label }) { value ->
                viewModel.update { it.copy(tapAction = value) }
            }
            DropdownRow("Double tap", settings.doubleTapAction, actions, { it.label }) { value ->
                viewModel.update { it.copy(doubleTapAction = value) }
            }
            DropdownRow("Long press", settings.longPressAction, actions, { it.label }) { value ->
                viewModel.update { it.copy(longPressAction = value) }
            }
            DropdownRow("Swipe down", settings.swipeDownAction, actions, { it.label }) { value ->
                viewModel.update { it.copy(swipeDownAction = value) }
            }
            DropdownRow("Swipe up", settings.swipeUpAction, actions, { it.label }) { value ->
                viewModel.update { it.copy(swipeUpAction = value) }
            }
            DropdownRow("Swipe left", settings.swipeLeftAction, actions, { it.label }) { value ->
                viewModel.update { it.copy(swipeLeftAction = value) }
            }
            DropdownRow("Swipe right", settings.swipeRightAction, actions, { it.label }) { value ->
                viewModel.update { it.copy(swipeRightAction = value) }
            }
        }

        SectionCard(title = "Feedback") {
            SwitchRow(
                title = "Haptics",
                subtitle = "A small tap whenever the island changes shape",
                checked = settings.hapticsEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(hapticsEnabled = value) } }
            )
            if (settings.hapticsEnabled) {
                SliderRow(
                    title = "Haptic strength",
                    value = settings.hapticStrength.toFloat(),
                    range = 1f..3f,
                    steps = 1,
                    valueLabel = when (settings.hapticStrength) {
                        1 -> "Light"
                        3 -> "Strong"
                        else -> "Medium"
                    },
                    onValueChange = { value ->
                        viewModel.update { it.copy(hapticStrength = value.roundToInt()) }
                    }
                )
            }
            SliderRow(
                title = "Collapse after",
                value = settings.autoCollapseSeconds.toFloat(),
                range = 0f..20f,
                valueLabel = if (settings.autoCollapseSeconds == 0) "Never"
                else "${settings.autoCollapseSeconds}s",
                onValueChange = { value ->
                    viewModel.update { it.copy(autoCollapseSeconds = value.roundToInt()) }
                }
            )
        }
    }
}
