package com.joyboard.notchisland.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.joyboard.notchisland.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign

@Composable
fun AboutScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // The app itself: its icon, name and version, then what it is.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.ios_style_dynamic_island_android),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (viewModel.updaterEnabled) {
                FilledTonalButton(
                    onClick = { viewModel.checkForUpdatesNow() },
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text(stringResource(R.string.check_updates)) }
            }
        }

        SectionCard(title = stringResource(R.string.how_island_decides_what_show)) {
            Body(stringResource(R.string.notifications_outrank_everything_then_privacy))
            Body(stringResource(R.string.music_timers_are_sticky_they))
            Body(stringResource(R.string.anything_transient_fades_back_resting))
        }

        SectionCard(title = stringResource(R.string.why_can_sit_top_status)) {
            Body(
                stringResource(R.string.android_layers_windows_by_type)
            )
            Body(
                stringResource(R.string.over_status_bar_done_by)
            )
            Body(
                stringResource(R.string.anything_genuinely_draws_above_status)
            )
        }

        SectionCard(title = stringResource(R.string.keeping_alive)) {
            Body(
                stringResource(R.string.android_may_stop_background_overlays)
            )
            FilledTonalButton(
                onClick = { context.openBatteryOptimisationSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.battery_optimisation_settings)) }
        }

        SectionCard(
            title = stringResource(R.string.backup),
            subtitle = stringResource(R.string.every_setting_as_small_json)
        ) {
            val exporter = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri -> uri?.let { viewModel.exportSettings(it) } }
            val importer = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> uri?.let { viewModel.importSettings(it) } }

            FilledTonalButton(
                onClick = { exporter.launch("notch-island-settings.json") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) { Text(stringResource(R.string.export_settings)) }
            FilledTonalButton(
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp)
            ) { Text(stringResource(R.string.restore_from_backup)) }
        }

        SectionCard(
            title = stringResource(R.string.diagnostics),
            subtitle = stringResource(R.string.device_display_cutout_anything_has)
        ) {
            val shareSubject = stringResource(R.string.notch_island_diagnostics)
            val shareTitle = stringResource(R.string.share_diagnostics)
            FilledTonalButton(
                onClick = {
                    val share = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                        .putExtra(Intent.EXTRA_TEXT, viewModel.diagnosticsText())
                    context.startActivity(Intent.createChooser(share, shareTitle))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) { Text(stringResource(R.string.share_diagnostics)) }
            FilledTonalButton(
                onClick = { viewModel.clearCrashLog() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 2.dp)
            ) { Text(stringResource(R.string.clear_crash_log)) }
            Body(
                stringResource(R.string.crashes_are_written_app_own)
            )
        }

        SectionCard(title = stringResource(R.string.privacy)) {
            Body(
                stringResource(R.string.everything_stays_device_notification_content)
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}
