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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joyboard.notchisland.BuildConfig
import com.joyboard.notchisland.ui.components.SectionCard
import com.joyboard.notchisland.util.openBatteryOptimisationSettings

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        SectionCard(title = "Notch Island") {
            Body("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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
