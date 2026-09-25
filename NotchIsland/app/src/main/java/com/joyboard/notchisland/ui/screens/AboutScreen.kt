package com.joyboard.notchisland.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.BuildConfig
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.components.SectionCard
import com.joyboard.notchisland.util.openBatteryOptimisationSettings

@Composable
fun AboutScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        SectionCard(title = "Notch Island") {
            Body("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            if (viewModel.updaterEnabled) OutlinedButton(
                onClick = { viewModel.checkForUpdatesNow() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) { Text("Check for updates") }
            Body(
                "An iOS-style Dynamic Island for Android. It draws a floating pill near the " +
                    "camera cutout and lets live activities — music, notifications, charging, " +
                    "timers, privacy indicators — take it over, one at a time, by priority."
            )
        }

        SectionCard(title = "How the island decides what to show") {
            Body("Notifications outrank everything, then privacy, unlock, volume, ringer, battery.")
            Body("Music and timers are sticky: they stay until playback stops or the timer ends.")
            Body("Anything transient fades back to the resting pill once its time is up.")
        }

        SectionCard(title = "Why it can't sit on top of the status bar") {
            Body(
                "Android layers windows by type. An app overlay — the only kind an app without " +
                    "system privileges can create — is always placed below the status bar, and " +
                    "the status bar consumes every touch inside its own band. No permission, " +
                    "flag or window type changes that for a normal app."
            )
            Body(
                "So \"over the status bar\" is done by drawing, not by layering. In Look → " +
                    "Position → Over the status bar the island is drawn up at the cutout, where " +
                    "it reads as part of the hardware, and a transparent strip hangs just below " +
                    "the status bar to catch taps. Centre it and the system clock and icons sit " +
                    "either side of it rather than through it."
            )
            Body(
                "Anything that genuinely draws above the status bar is a system app, a launcher, " +
                    "or a build with elevated privileges."
            )
        }

        SectionCard(title = "Keeping it alive") {
            Body(
                "Android may stop background overlays on aggressive battery settings. Excluding " +
                    "Notch Island from battery optimisation keeps it steady."
            )
            OutlinedButton(
                onClick = { context.openBatteryOptimisationSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) { Text("Battery optimisation settings") }
        }

        SectionCard(
            title = "Backup",
            subtitle = "Every setting, as a small JSON file you keep."
        ) {
            val exporter = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri -> uri?.let { viewModel.exportSettings(it) } }
            val importer = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> uri?.let { viewModel.importSettings(it) } }

            OutlinedButton(
                onClick = { exporter.launch("notch-island-settings.json") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) { Text("Export settings") }
            OutlinedButton(
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp)
            ) { Text("Restore from a backup") }
        }

        SectionCard(
            title = "Diagnostics",
            subtitle = "Device, display, cutout and anything that has crashed."
        ) {
            OutlinedButton(
                onClick = {
                    val share = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Notch Island diagnostics")
                        .putExtra(Intent.EXTRA_TEXT, viewModel.diagnosticsText())
                    context.startActivity(Intent.createChooser(share, "Share diagnostics"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) { Text("Share diagnostics") }
            OutlinedButton(
                onClick = { viewModel.clearCrashLog() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp)
            ) { Text("Clear the crash log") }
            Body(
                "Crashes are written to the app's own storage and stay there. Nothing is sent " +
                    "anywhere unless you share it yourself."
            )
        }

        SectionCard(title = "Privacy") {
            Body(
                "Everything stays on the device. Notification content is rendered straight into " +
                    "the overlay and never stored, logged or uploaded."
            )
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
    )
}
