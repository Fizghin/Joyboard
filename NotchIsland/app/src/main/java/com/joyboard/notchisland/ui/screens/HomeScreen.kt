package com.joyboard.notchisland.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.components.IslandPreview
import com.joyboard.notchisland.BuildConfig
import com.joyboard.notchisland.ui.components.SectionCard
import com.joyboard.notchisland.ui.components.NavRow
import com.joyboard.notchisland.ui.components.SwitchRow
import com.joyboard.notchisland.ui.components.UrlDialog
import com.joyboard.notchisland.update.UpdateState
import com.joyboard.notchisland.util.openDndAccessSettings
import com.joyboard.notchisland.util.openNotificationAccessSettings
import com.joyboard.notchisland.util.openOverlaySettings
import com.joyboard.notchisland.util.openWriteSettings
import com.joyboard.notchisland.R
import androidx.compose.ui.res.stringResource

@Composable
fun HomeScreen(viewModel: MainViewModel, onOpenAppearance: () -> Unit) {
    val settings by viewModel.settings.collectAsStateLifecycle()
    val permissions by viewModel.permissions.collectAsStateLifecycle()
    val context = LocalContext.current
    var previewMode by remember { mutableStateOf(IslandMode.COMPACT) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dynamic_island),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            !permissions.overlay -> stringResource(R.string.needs_display_over_apps_permission)
                            settings.enabled -> stringResource(R.string.running_tap_island_expand)
                            else -> stringResource(R.string.switched_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.enabled,
                    enabled = permissions.overlay,
                    onCheckedChange = { viewModel.setEnabled(it) }
                )
            }
            if (!permissions.overlay) {
                Button(
                    onClick = { context.openOverlaySettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.allow_display_over_other_apps))
                }
            }
        }

        IslandPreview(
            settings = settings,
            mode = previewMode,
            onModeChange = { previewMode = it },
        )

        SectionCard(
            title = stringResource(R.string.permissions),
            subtitle = stringResource(R.string.each_one_unlocks_different_part)
        ) {
            PermissionRow(
                title = stringResource(R.string.display_over_other_apps),
                subtitle = stringResource(R.string.required_draws_island_itself),
                granted = permissions.overlay,
                onGrant = { context.openOverlaySettings() }
            )
            PermissionRow(
                title = stringResource(R.string.notification_access),
                subtitle = stringResource(R.string.media_controls_alerts_now_playing),
                granted = permissions.notificationAccess,
                onGrant = { context.openNotificationAccessSettings() }
            )
            PermissionRow(
                title = stringResource(R.string.modify_system_settings),
                subtitle = stringResource(R.string.brightness_auto_rotate_from_quick),
                granted = permissions.writeSettings,
                onGrant = { context.openWriteSettings() }
            )
            PermissionRow(
                title = stringResource(R.string.do_not_disturb_access),
                subtitle = stringResource(R.string.lets_dnd_toggle_work_place),
                granted = permissions.dndAccess,
                onGrant = { context.openDndAccessSettings() }
            )
        }

        SectionCard(title = stringResource(R.string.try_out)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.expandIsland() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.expand)) }
                OutlinedButton(
                    onClick = { viewModel.startTimer(1) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.n_1_min_timer)) }
            }
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.startTimer(5) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.n_5_min_timer)) }
                OutlinedButton(
                    onClick = onOpenAppearance,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.customise)) }
            }
            Text(
                stringResource(R.string.timers_expand_action_need_island),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
        }

        if (viewModel.updaterEnabled) SectionCard(title = stringResource(R.string.updates)) {
            val updateState by viewModel.updateState.collectAsStateLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.version_2, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        when (val state = updateState) {
                            is UpdateState.Checking -> stringResource(R.string.checking)
                            is UpdateState.UpToDate -> stringResource(R.string.you_re_latest_version)
                            is UpdateState.Available -> stringResource(R.string.version_ready, state.info.versionName)
                            is UpdateState.Downloading ->
                                stringResource(R.string.downloading, (state.progress * 100).toInt())
                            is UpdateState.ReadyToInstall -> stringResource(R.string.downloaded_tap_install)
                            is UpdateState.Failed -> state.message
                            UpdateState.Idle -> stringResource(R.string.tap_check_newer_build)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.checkForUpdatesNow() },
                    enabled = updateState !is UpdateState.Checking &&
                        updateState !is UpdateState.Downloading
                ) { Text(stringResource(R.string.check)) }
            }
            SwitchRow(
                title = stringResource(R.string.check_automatically),
                subtitle = stringResource(R.string.looks_once_every_few_hours),
                checked = settings.autoCheckUpdates,
                onCheckedChange = { value -> viewModel.update { it.copy(autoCheckUpdates = value) } }
            )
            var editingSource by remember { mutableStateOf(false) }
            NavRow(
                title = stringResource(R.string.update_source),
                subtitle = settings.updateManifestUrl,
                onClick = { editingSource = true }
            )
            if (editingSource) {
                UrlDialog(
                    initial = settings.updateManifestUrl,
                    onDismiss = { editingSource = false },
                    onConfirm = { url ->
                        viewModel.setUpdateManifestUrl(url)
                        editingSource = false
                    }
                )
            }
        }

        SectionCard(title = stringResource(R.string.tips)) {
            Tip(stringResource(R.string.tap_island_expand_swipe_up))
            Tip(stringResource(R.string.double_tap_plays_or_pauses))
            Tip(stringResource(R.string.swipe_left_or_right_island))
            Tip(stringResource(R.string.long_press_opens_app_every))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (granted) Color(0xFF34C759).copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.PriorityHigh,
                contentDescription = null,
                tint = if (granted) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
        }
    }
}

@Composable
private fun Tip(text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}
