package com.joyboard.notchisland

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.joyboard.notchisland.data.CameraSource
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.data.hole
import com.joyboard.notchisland.data.withHole
import com.joyboard.notchisland.island.Hole
import com.joyboard.notchisland.island.HoleGeometry
import com.joyboard.notchisland.ui.MainViewModel
import com.joyboard.notchisland.ui.screens.collectAsStateLifecycle
import com.joyboard.notchisland.ui.theme.NotchIslandTheme
import com.joyboard.notchisland.util.CutoutDetector
import kotlin.math.roundToInt

/**
 * Lines the island up with the phone's real camera. The activity draws right into the cutout
 * area with the system bars hidden, so what is on screen here is exactly where the overlay will
 * be — you drag the markers onto the hardware rather than guessing at numbers.
 */
class CalibrationActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The field only exists from Android 9, so it must not even be read below that. ALWAYS
        // arrived in Android 11; SHORT_EDGES reaches the top cutout in portrait, which is the
        // only orientation calibration means anything in.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
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

private enum class Target(val label: String) { ISLAND("Island"), CAMERA("Camera") }

@Composable
private fun CalibrationScreen(
    settings: IslandSettings,
    onSave: (IslandSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by remember(settings.devicePresetId, settings.preset) { mutableStateOf(settings) }
    var target by remember { mutableStateOf(if (settings.hole == null) Target.CAMERA else Target.ISLAND) }
    var lightBackground by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val context = LocalContext.current

    // In landscape the camera sits on a side edge, so anything measured here would be wrong for
    // the island. Rather than lock the orientation — which Android 16 ignores on large screens
    // anyway — say what is needed.
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Turn your phone upright", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "The camera and the island both live at the top of the screen in portrait, so " +
                        "that is where they have to be lined up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) { Text("Close") }
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (lightBackground) Color.White else Color.Black)
    ) {
        val screenCentre: Dp = maxWidth / 2
        val hole = draft.hole

        // ---- the island, exactly where the overlay puts it ----
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (screenCentre + draft.offsetX.dp - draft.collapsedWidth.dp / 2).roundToPx(),
                        draft.offsetY.dp.roundToPx()
                    )
                }
                .size(draft.collapsedWidth.dp, draft.collapsedHeight.dp)
                .clip(RoundedCornerShape(draft.cornerRadius.dp))
                .background(Color.Black)
        ) {
            // Stand-ins for the icon and readout, placed the way the real island will place
            // them — so the clearance around the camera is something you can see.
            val local = hole?.let {
                HoleGeometry.locate(
                    it.centerX - draft.offsetX, it.centerY - draft.offsetY, it, draft.collapsedWidth.toFloat()
                )
            }
            val clearance = if (draft.avoidHole && local != null) {
                HoleGeometry.rowClearance(
                    local, draft.collapsedWidth.toFloat(), draft.collapsedHeight.toFloat(),
                    basePadding = 12f, leadingWidth = 18f, trailingWidth = 22f, margin = 6f,
                )
            } else {
                com.joyboard.notchisland.island.Clearance.NONE
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (12f + clearance.start).dp)
                    .size(18.dp)
                    .background(Color(0xFF3B82F6), RoundedCornerShape(5.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = -(12f + clearance.end).dp)
                    .size(22.dp, 10.dp)
                    .background(Color(0xFF34C759), RoundedCornerShape(3.dp))
            )
        }

        // ---- the camera hole: a ring you drag onto the real one ----
        if (hole != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (screenCentre + hole.centerX.dp - hole.width.dp / 2).roundToPx(),
                            (hole.centerY.dp - hole.height.dp / 2).roundToPx()
                        )
                    }
                    .size(hole.width.dp, hole.height.dp)
                    .border(2.dp, Color(0xFFFF375F), CircleShape)
            )
        }

        // ---- one surface catches drags and moves whichever is selected ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .size(width = maxWidth, height = 160.dp)
                .pointerInput(target) {
                    detectDragGestures { _, drag ->
                        with(density) {
                            val dx = drag.x.toDp().value
                            val dy = drag.y.toDp().value
                            draft = when (target) {
                                Target.ISLAND -> draft.copy(
                                    offsetX = (draft.offsetX + dx).roundToInt().coerceIn(-180, 180),
                                    offsetY = (draft.offsetY + dy).roundToInt().coerceIn(0, 120),
                                )
                                Target.CAMERA -> {
                                    val current = draft.hole ?: Hole(0f, 24f, 22f, 22f)
                                    draft.withHole(
                                        current.copy(
                                            centerX = (current.centerX + dx).coerceIn(-200f, 200f),
                                            centerY = (current.centerY + dy).coerceIn(0f, 140f),
                                        ),
                                        CameraSource.MANUAL,
                                    )
                                }
                            }
                        }
                    }
                }
        )

        // ---- controls ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (target == Target.CAMERA) "Put the red ring on your camera" else "Drag the island into place",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Drag near the top of the screen. The camera is treated as hardware: text and " +
                    "icons in the island move out of its way, as the blue and green markers show.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Target.entries.forEach { option ->
                    FilterChip(
                        selected = target == option,
                        onClick = { target = option },
                        label = { Text("Move ${option.label.lowercase()}") }
                    )
                }
            }

            if (target == Target.CAMERA) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = {
                        CutoutDetector.detectHole(context)
                            .onSuccess {
                                draft = draft.withHole(it, CameraSource.DETECTED)
                                message = "Found a ${it.width.roundToInt()}×${it.height.roundToInt()} dp cutout"
                            }
                            .onFailure { message = it.message }
                    }) { Text("Detect from this phone") }
                    OutlinedButton(
                        enabled = hole != null,
                        onClick = {
                            val h = draft.hole ?: return@OutlinedButton
                            // Wrap the island around the camera, iPhone-style.
                            val height = maxOf(draft.collapsedHeight, (h.height + 12).roundToInt())
                            draft = draft.copy(
                                offsetX = h.centerX.roundToInt(),
                                collapsedHeight = height,
                                cornerRadius = height / 2,
                                collapsedWidth = maxOf(draft.collapsedWidth, (h.width + 88).roundToInt()),
                                offsetY = (h.centerY - height / 2f).roundToInt().coerceAtLeast(0),
                            )
                        }
                    ) { Text("Wrap island") }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }
                if (hole != null) {
                    CalibrationSlider("Size", hole.width.roundToInt(), 6..90) { size ->
                        // Punch holes are round, so width and height move together; a pill or
                        // notch keeps its proportions.
                        val ratio = if (hole.width > 0f) hole.height / hole.width else 1f
                        draft = draft.withHole(hole.copy(width = size.toFloat(), height = size * ratio), CameraSource.MANUAL)
                    }
                    CalibrationSlider("Across", hole.centerX.roundToInt(), -200..200) {
                        draft = draft.withHole(hole.copy(centerX = it.toFloat()), CameraSource.MANUAL)
                    }
                    CalibrationSlider("Down", hole.centerY.roundToInt(), 0..140) {
                        draft = draft.withHole(hole.copy(centerY = it.toFloat()), CameraSource.MANUAL)
                    }
                }
                ToggleRow("Keep content clear of the camera", draft.avoidHole) {
                    draft = draft.copy(avoidHole = it)
                }
                ToggleRow("Draw a lens over it", draft.showFauxCamera) {
                    draft = draft.copy(showFauxCamera = it)
                }
            } else {
                CalibrationSlider("Width", draft.collapsedWidth, 48..320) {
                    draft = draft.copy(collapsedWidth = it)
                }
                CalibrationSlider("Height", draft.collapsedHeight, 16..72) {
                    draft = draft.copy(collapsedHeight = it, cornerRadius = minOf(draft.cornerRadius, it / 2))
                }
                CalibrationSlider("Corner", draft.cornerRadius, 0..40) {
                    draft = draft.copy(cornerRadius = it)
                }
                CalibrationSlider("Left / right", draft.offsetX, -180..180) {
                    draft = draft.copy(offsetX = it)
                }
                CalibrationSlider("Down", draft.offsetY, 0..120) {
                    draft = draft.copy(offsetY = it)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { lightBackground = !lightBackground }, modifier = Modifier.weight(1f)) {
                    Text(if (lightBackground) "Dark" else "Light")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    // Over-the-status-bar keeps offsets exactly as measured here and hangs a
                    // touch strip below, so a calibrated island stays tappable.
                    onClick = { onSave(draft.copy(positionMode = PositionMode.OVERLAP_STATUS_BAR)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CalibrationSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp))
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f)
        )
        Text("$value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
    }
}
