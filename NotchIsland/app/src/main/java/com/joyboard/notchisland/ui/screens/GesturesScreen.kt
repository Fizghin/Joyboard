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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.TapExpansion
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.components.DropdownRow
import com.joyboard.notchisland.ui.components.SectionCard
import com.joyboard.notchisland.ui.components.SliderRow
import com.joyboard.notchisland.ui.components.SwitchRow
import kotlin.math.roundToInt
import com.joyboard.notchisland.R
import androidx.compose.ui.res.stringResource

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
            title = stringResource(R.string.opening),
            subtitle = stringResource(R.string.how_far_tap_takes_island)
        ) {
            DropdownRow(
                title = stringResource(R.string.tap_behaviour),
                selected = settings.tapExpansion,
                options = TapExpansion.entries.toList(),
                label = { stringResource(it.label) },
                onSelected = { value -> viewModel.update { it.copy(tapExpansion = value) } }
            )
            Text(
                when (settings.tapExpansion) {
                    TapExpansion.STEP ->
                        stringResource(R.string.tap_once_compact_preview_again)
                    TapExpansion.DIRECT ->
                        stringResource(R.string.tap_opens_full_panel_one)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
        }

        SectionCard(
            title = stringResource(R.string.gestures),
            subtitle = stringResource(R.string.every_touch_island_can_be)
        ) {
            DropdownRow(stringResource(R.string.tap), settings.tapAction, actions, { stringResource(it.label) }) { value ->
                viewModel.update { it.copy(tapAction = value) }
            }
            DropdownRow(stringResource(R.string.double_tap), settings.doubleTapAction, actions, { stringResource(it.label) }) { value ->
                viewModel.update { it.copy(doubleTapAction = value) }
            }
            DropdownRow(stringResource(R.string.long_press), settings.longPressAction, actions, { stringResource(it.label) }) { value ->
                viewModel.update { it.copy(longPressAction = value) }
            }
            DropdownRow(stringResource(R.string.swipe_down), settings.swipeDownAction, actions, { stringResource(it.label) }) { value ->
                viewModel.update { it.copy(swipeDownAction = value) }
            }
            DropdownRow(stringResource(R.string.swipe_up), settings.swipeUpAction, actions, { stringResource(it.label) }) { value ->
                viewModel.update { it.copy(swipeUpAction = value) }
            }
            DropdownRow(stringResource(R.string.swipe_left), settings.swipeLeftAction, actions, { stringResource(it.label) }) { value ->
                viewModel.update { it.copy(swipeLeftAction = value) }
            }
            DropdownRow(stringResource(R.string.swipe_right), settings.swipeRightAction, actions, { stringResource(it.label) }) { value ->
                viewModel.update { it.copy(swipeRightAction = value) }
            }
        }

        SectionCard(title = stringResource(R.string.feedback)) {
            SwitchRow(
                title = stringResource(R.string.haptics),
                subtitle = stringResource(R.string.small_tap_whenever_island_changes),
                checked = settings.hapticsEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(hapticsEnabled = value) } }
            )
            if (settings.hapticsEnabled) {
                SliderRow(
                    title = stringResource(R.string.haptic_strength),
                    value = settings.hapticStrength.toFloat(),
                    range = 1f..3f,
                    steps = 1,
                    valueLabel = when (settings.hapticStrength) {
                        1 -> stringResource(R.string.light)
                        3 -> stringResource(R.string.strong)
                        else -> stringResource(R.string.medium)
                    },
                    onValueChange = { value ->
                        viewModel.update { it.copy(hapticStrength = value.roundToInt()) }
                    }
                )
            }
            SliderRow(
                title = stringResource(R.string.close_again_after),
                value = settings.autoCollapseSeconds.toFloat(),
                range = 0f..20f,
                valueLabel = if (settings.autoCollapseSeconds == 0) stringResource(R.string.never)
                else "${settings.autoCollapseSeconds}s",
                onValueChange = { value ->
                    viewModel.update { it.copy(autoCollapseSeconds = value.roundToInt()) }
                }
            )
        }

        SectionCard(
            title = stringResource(R.string.going_back_rest),
            subtitle = stringResource(R.string.what_island_does_when_you)
        ) {
            SwitchRow(
                title = stringResource(R.string.stay_compact_while_something_live),
                subtitle = stringResource(R.string.keeps_readout_up_as_long),
                checked = settings.stayCompactForActivities,
                onCheckedChange = { value ->
                    viewModel.update { it.copy(stayCompactForActivities = value) }
                }
            )
            if (!settings.stayCompactForActivities) {
                SliderRow(
                    title = stringResource(R.string.settle_back_after),
                    value = settings.compactRestSeconds.toFloat(),
                    range = 0f..60f,
                    valueLabel = if (settings.compactRestSeconds == 0) stringResource(R.string.straight_away)
                    else "${settings.compactRestSeconds}s",
                    onValueChange = { value ->
                        viewModel.update { it.copy(compactRestSeconds = value.roundToInt()) }
                    }
                )
                Text(
                    stringResource(R.string.measured_from_last_time_something),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
            }
        }
    }
}
