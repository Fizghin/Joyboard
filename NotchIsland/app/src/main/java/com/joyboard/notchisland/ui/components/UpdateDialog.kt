package com.joyboard.notchisland.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.update.UpdateState

/**
 * The one dialog the updater needs: it announces the release, downloads it in place and hands
 * over to the installer, without ever leaving the app.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
) {
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.ReadyToInstall -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    } ?: return

    val downloading = state is UpdateState.Downloading
    val progress = (state as? UpdateState.Downloading)?.progress ?: 0f

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        icon = {
            Row(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SystemUpdateAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        title = {
            Text(
                when (state) {
                    is UpdateState.ReadyToInstall -> "Ready to install"
                    is UpdateState.Downloading -> "Downloading ${info.versionName}"
                    else -> "Version ${info.versionName} is available"
                }
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (state is UpdateState.Failed) {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (info.notes.isNotEmpty()) {
                    Text(
                        "What's new",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    info.notes.forEach { note ->
                        Text(
                            "•  $note",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (info.readableSize.isNotBlank() && state !is UpdateState.Failed) {
                    Text(
                        "Download size ${info.readableSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !downloading) {
                Text(
                    when (state) {
                        is UpdateState.ReadyToInstall -> "Install"
                        is UpdateState.Failed -> "Try again"
                        is UpdateState.Downloading -> "Downloading…"
                        else -> "Update"
                    }
                )
            }
        },
        dismissButton = {
            if (!downloading) {
                Row {
                    if (state is UpdateState.Available && !info.mandatory) {
                        TextButton(onClick = onSkip) { Text("Skip") }
                    }
                    TextButton(onClick = onDismiss) { Text("Later") }
                }
            }
        }
    )
}
