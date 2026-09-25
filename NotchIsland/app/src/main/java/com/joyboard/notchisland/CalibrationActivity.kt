package com.joyboard.notchisland

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.screens.collectAsStateLifecycle
import com.joyboard.notchisland.ui.theme.NotchIslandTheme
import kotlin.math.roundToInt

/**
 * Lines the island up with the phone's real camera cutout. The activity draws right into the
 * cutout area with the system bars hidden, so what you see here is what the overlay will do —
 * drag the pill onto the hole rather than guessing at numbers.
 */
class CalibrationActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val settings by viewModel.settings.collectAsStateLifecycle()
            NotchIslandTheme(themeMode = settings.themeMode) {
                CalibrationScreen(
                    settings = settings,
                    onSave = { draft ->
                        viewModel.update { draft }
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun CalibrationScreen(
    settings: IslandSettings,
    onSave: (IslandSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember(settings.preset) { mutableStateOf(settings) }
    var lightBackground by remember { mutableStateOf(true) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (lightBackground) Color.White else Color.Black)
    ) {
        // The island as the overlay will draw it, in the same place.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = draft.offsetX.dp, y = draft.offsetY.dp)
                .size(draft.collapsedWidth.dp, draft.collapsedHeight.dp)
                .clip(RoundedCornerShape(draft.cornerRadius.dp))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        with(density) {
                            draft = draft.copy(
                                offsetX = (draft.offsetX + dragAmount.x.toDp().value)
                                    .roundToInt().coerceIn(-160, 160),
                                offsetY = (draft.offsetY + dragAmount.y.toDp().value)
                                    .roundToInt().coerceIn(0, 120),
                            )
                        }
                    }
                }
        ) {
            if (draft.showFauxCamera) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = draft.cameraOffsetX.dp, y = draft.cameraOffsetY.dp)
                        .size(draft.cameraSize.dp)
                        .background(Color(0xFF0A0A0C), CircleShape)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Drag the pill onto your camera",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "A light background makes the hole easy to see. Size it until the pill covers " +
                    "the camera the way you want, then save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            CalibrationSlider("Width", draft.collapsedWidth, 48..320) {
                draft = draft.copy(collapsedWidth = it)
            }
            CalibrationSlider("Height", draft.collapsedHeight, 16..72) {
                draft = draft.copy(collapsedHeight = it, cornerRadius = minOf(draft.cornerRadius, it / 2))
            }
            CalibrationSlider("Corner", draft.cornerRadius, 0..40) {
                draft = draft.copy(cornerRadius = it)
            }
            CalibrationSlider("Left / right", draft.offsetX, -160..160) {
                draft = draft.copy(offsetX = it)
            }
            CalibrationSlider("Down from the top", draft.offsetY, 0..120) {
                draft = draft.copy(offsetY = it)
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lens", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(60.dp))
                OutlinedButton(onClick = {
                    draft = draft.copy(showFauxCamera = !draft.showFauxCamera)
                }) {
                    Text(if (draft.showFauxCamera) "Drawn" else "Hidden")
                }
            }
            if (draft.showFauxCamera) {
                CalibrationSlider("Lens size", draft.cameraSize, 4..40) {
                    draft = draft.copy(cameraSize = it)
                }
                CalibrationSlider("Lens across", draft.cameraOffsetX, -150..150) {
                    draft = draft.copy(cameraOffsetX = it)
                }
                CalibrationSlider("Lens down", draft.cameraOffsetY, -30..30) {
                    draft = draft.copy(cameraOffsetY = it)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { lightBackground = !lightBackground },
                    modifier = Modifier.weight(1f)
                ) { Text(if (lightBackground) "Dark" else "Light") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        // Calibrated numbers only mean anything against the top of the screen.
                        onSave(draft.copy(positionMode = PositionMode.CUSTOM))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }
            Text(
                "Saving switches the anchor to Custom, so these offsets are used exactly as " +
                    "measured. Remember that taps only register below the status bar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CalibrationSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(96.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f)
        )
        Text(
            "$value",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp)
        )
    }
}
