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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.joyboard.notchisland.util.openAccessibilitySettings
import com.joyboard.notchisland.util.openAppInfo
import com.joyboard.notchisland.ui.theme.Tint
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.joyboard.notchisland.ui.components.IconTile
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.res.pluralStringResource

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
        HeroCard(
            enabled = settings.enabled,
            canRun = permissions.overlay,
            onToggle = { viewModel.setEnabled(it) },
            onAllow = { context.openOverlaySettings() },
        )

        IslandPreview(
            settings = settings,
            mode = previewMode,
            onModeChange = { previewMode = it },
        )

        SectionCard(
            title = stringResource(R.string.permissions),
            subtitle = stringResource(R.string.each_one_unlocks_different_part)
        ) {
            val checks = listOfNotNull(
                permissions.overlay, permissions.notificationAccess, permissions.writeSettings,
                permissions.dndAccess, permissions.helper.takeIf { BuildConfig.HELPER_AVAILABLE },
            )
            SetupProgress(done = checks.count { it }, total = checks.size)
            PermissionRow(
                title = stringResource(R.string.display_over_other_apps),
                icon = Icons.Rounded.Layers,
                tint = Tint.Blue,
                subtitle = stringResource(R.string.required_draws_island_itself),
                granted = permissions.overlay,
                onGrant = { context.openOverlaySettings() }
            )
            PermissionRow(
                title = stringResource(R.string.notification_access),
                icon = Icons.Rounded.Notifications,
                tint = Tint.Red,
                subtitle = stringResource(R.string.media_controls_alerts_now_playing),
                granted = permissions.notificationAccess,
                onGrant = { context.openNotificationAccessSettings() }
            )
            PermissionRow(
                title = stringResource(R.string.modify_system_settings),
                icon = Icons.Rounded.Settings,
                tint = Tint.Gray,
                subtitle = stringResource(R.string.brightness_auto_rotate_from_quick),
                granted = permissions.writeSettings,
                onGrant = { context.openWriteSettings() }
            )
            PermissionRow(
                title = stringResource(R.string.do_not_disturb_access),
                icon = Icons.Rounded.DoNotDisturbOn,
                tint = Tint.Indigo,
                subtitle = stringResource(R.string.lets_dnd_toggle_work_place),
                granted = permissions.dndAccess,
                onGrant = { context.openDndAccessSettings() }
            )
            if (BuildConfig.HELPER_AVAILABLE) {
                var disclosing by remember { mutableStateOf(false) }
                PermissionRow(
                    title = stringResource(R.string.helper_row_title),
                    icon = Icons.Rounded.Accessibility,
                    tint = Tint.Purple,
                    subtitle = stringResource(R.string.helper_row_desc),
                    granted = permissions.helper,
                    onGrant = { disclosing = true }
                )
                // Accessibility access is powerful, so say plainly what it is used for first.
                if (disclosing) {
                    AlertDialog(
                        onDismissRequest = { disclosing = false },
                        title = { Text(stringResource(R.string.helper_disclosure_title)) },
                        text = { Text(stringResource(R.string.helper_disclosure_body)) },
                        confirmButton = {
                            TextButton(onClick = {
                                disclosing = false
                                context.openAccessibilitySettings()
                            }) { Text(stringResource(R.string.continue_label)) }
                        },
                        dismissButton = {
                            Row {
                                // Android 13+ holds a sideloaded app's accessibility service
                                // behind "Allow restricted settings" in App info.
                                TextButton(onClick = { context.openAppInfo() }) {
                                    Text(stringResource(R.string.app_info))
                                }
                                TextButton(onClick = { disclosing = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.try_out)) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActionTile(Icons.Rounded.OpenInFull, Tint.Blue, stringResource(R.string.expand), Modifier.weight(1f)) {
                    viewModel.expandIsland()
                }
                ActionTile(Icons.Rounded.Timer, Tint.Orange, stringResource(R.string.n_1_min_timer), Modifier.weight(1f)) {
                    viewModel.startTimer(1)
                }
                ActionTile(Icons.Rounded.AvTimer, Tint.Orange, stringResource(R.string.n_5_min_timer), Modifier.weight(1f)) {
                    viewModel.startTimer(5)
                }
                ActionTile(Icons.Rounded.Palette, Tint.Pink, stringResource(R.string.customise), Modifier.weight(1f)) {
                    onOpenAppearance()
                }
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTile(Icons.Rounded.SystemUpdate, Tint.Green)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.version_2, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
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
                FilledTonalButton(
                    onClick = { viewModel.checkForUpdatesNow() },
                    enabled = updateState !is UpdateState.Checking &&
                        updateState !is UpdateState.Downloading
                ) { Text(stringResource(R.string.check)) }
            }
            SwitchRow(
                title = stringResource(R.string.check_automatically),
                icon = Icons.Rounded.Sync,
                tint = Tint.Blue,
                subtitle = stringResource(R.string.looks_once_every_few_hours),
                checked = settings.autoCheckUpdates,
                onCheckedChange = { value -> viewModel.update { it.copy(autoCheckUpdates = value) } }
            )
            var editingSource by remember { mutableStateOf(false) }
            NavRow(
                title = stringResource(R.string.update_source),
                icon = Icons.Rounded.Link,
                tint = Tint.Gray,
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

/**
 * The top of the screen: whether the island is running, and the one switch that matters, on
 * a card tinted from the wallpaper so it reads as the heart of the app.
 */
@Composable
private fun HeroCard(
    enabled: Boolean,
    canRun: Boolean,
    onToggle: (Boolean) -> Unit,
    onAllow: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (dot, shortStatus, longStatus) = when {
        !canRun -> Triple(Tint.Orange, R.string.status_setup, R.string.needs_display_over_apps_permission)
        enabled -> Triple(Tint.Green, R.string.status_running, R.string.running_tap_island_expand)
        else -> Triple(Tint.Gray, R.string.status_off, R.string.switched_off)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(shortStatus),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurface
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.dynamic_island),
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onPrimaryContainer
                )
                Text(
                    stringResource(longStatus),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.78f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                enabled = canRun,
                onCheckedChange = onToggle,
                thumbContent = if (enabled) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        }
        if (!canRun) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAllow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.allow_display_over_other_apps))
            }
        }
    }
}

/** How much of the setup is done, as a count and a bar. */
@Composable
private fun SetupProgress(done: Int, total: Int) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
        Text(
            pluralStringResource(R.plurals.setup_progress, total, done, total),
            style = MaterialTheme.typography.labelMedium,
            color = if (done == total) Tint.Green else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 1f else done / total.toFloat() },
            color = if (done == total) Tint.Green else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    icon: ImageVector,
    tint: Color,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tint)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (granted) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Tint.Green,
                modifier = Modifier.size(26.dp)
            )
        } else {
            FilledTonalButton(
                onClick = onGrant,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) { Text(stringResource(R.string.grant)) }
        }
    }
}

/** A quick action: a large tile over a short label, like a control centre button. */
@Composable
private fun ActionTile(
    icon: ImageVector,
    tint: Color,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconTile(icon, tint, size = 46.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun Tip(text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconTile(Icons.Rounded.Lightbulb, Tint.Yellow, size = 26.dp)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp, top = 3.dp)
        )
    }
}
