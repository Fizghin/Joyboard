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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.joyboard.notchisland.ui.components.SwitchRow
import com.joyboard.notchisland.update.UpdateState
import com.joyboard.notchisland.util.openDndAccessSettings
import com.joyboard.notchisland.util.openNotificationAccessSettings
import com.joyboard.notchisland.util.openOverlaySettings
import com.joyboard.notchisland.util.openWriteSettings

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
                        "Dynamic Island",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            !permissions.overlay -> "Needs the display-over-apps permission"
                            settings.enabled -> "Running · tap the island to expand it"
                            else -> "Switched off"
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
                    Text("Allow display over other apps")
                }
            }
        }

        IslandPreview(
            settings = settings,
            mode = previewMode,
            onModeChange = { previewMode = it },
        )

        SectionCard(
            title = "Permissions",
            subtitle = "Each one unlocks a different part of the island."
        ) {
            PermissionRow(
                title = "Display over other apps",
                subtitle = "Required — draws the island itself",
                granted = permissions.overlay,
                onGrant = { context.openOverlaySettings() }
            )
            PermissionRow(
                title = "Notification access",
                subtitle = "Media controls, alerts and now-playing art",
                granted = permissions.notificationAccess,
                onGrant = { context.openNotificationAccessSettings() }
            )
            PermissionRow(
                title = "Modify system settings",
                subtitle = "Brightness and auto-rotate from the quick panel",
                granted = permissions.writeSettings,
                onGrant = { context.openWriteSettings() }
            )
            PermissionRow(
                title = "Do Not Disturb access",
                subtitle = "Lets the DND toggle work in place",
                granted = permissions.dndAccess,
                onGrant = { context.openDndAccessSettings() }
            )
        }

        SectionCard(title = "Try it out") {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.expandIsland() },
                    modifier = Modifier.weight(1f)
                ) { Text("Expand") }
                OutlinedButton(
                    onClick = { viewModel.startTimer(1) },
                    modifier = Modifier.weight(1f)
                ) { Text("1 min timer") }
            }
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.startTimer(5) },
                    modifier = Modifier.weight(1f)
                ) { Text("5 min timer") }
                OutlinedButton(
                    onClick = onOpenAppearance,
                    modifier = Modifier.weight(1f)
                ) { Text("Customise") }
            }
            Text(
                "Timers and the expand action need the island to be switched on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
        }

        SectionCard(title = "Updates") {
            val updateState by viewModel.updateState.collectAsStateLifecycle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        when (val state = updateState) {
                            is UpdateState.Checking -> "Checking…"
                            is UpdateState.UpToDate -> "You're on the latest version"
                            is UpdateState.Available -> "Version ${state.info.versionName} is ready"
                            is UpdateState.Downloading ->
                                "Downloading ${(state.progress * 100).toInt()}%"
                            is UpdateState.ReadyToInstall -> "Downloaded — tap to install"
                            is UpdateState.Failed -> state.message
                            UpdateState.Idle -> "Tap to check for a newer build"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.checkForUpdatesNow() },
                    enabled = updateState !is UpdateState.Checking &&
                        updateState !is UpdateState.Downloading
                ) { Text("Check") }
            }
            SwitchRow(
                title = "Check automatically",
                subtitle = "Looks once every few hours while the app is open",
                checked = settings.autoCheckUpdates,
                onCheckedChange = { value -> viewModel.update { it.copy(autoCheckUpdates = value) } }
            )
        }

        SectionCard(title = "Tips") {
            Tip("Tap the island to expand it, swipe up to put it away.")
            Tip("Double tap plays or pauses whatever is on the speakers.")
            Tip("Swipe left or right on the island to change track.")
            Tip("Long press opens this app — every gesture can be remapped.")
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
            OutlinedButton(onClick = onGrant) { Text("Grant") }
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
