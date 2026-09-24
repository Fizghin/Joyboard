package com.joyboard.notchisland.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.joyboard.notchisland.data.ColorSource
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.util.DynamicColors

private val STATUS_BAR_BAND = 26.dp

/** A faithful, tappable mock of the overlay so settings can be judged without leaving the app. */
@Composable
fun IslandPreview(
    settings: IslandSettings,
    mode: IslandMode,
    onModeChange: (IslandMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = Color(
        when (settings.accentSource) {
            ColorSource.MANUAL -> settings.accentColor
            // The preview cannot know the album art, so it stands in with the wallpaper accent.
            else -> DynamicColors.accent(context, dark = true)
        }
    )
    val bodyColor = when (settings.backgroundSource) {
        ColorSource.MATERIAL_YOU -> DynamicColors.surface(context, dark = true)
        else -> settings.backgroundColor
    }
    val width by animateDpAsState(
        targetValue = when (mode) {
            IslandMode.EXPANDED -> settings.expandedWidth.dp
            IslandMode.MEDIUM -> settings.mediumWidth.dp
            IslandMode.COMPACT -> settings.compactWidth.dp
            else -> settings.collapsedWidth.dp
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "width"
    )
    val height by animateDpAsState(
        targetValue = when (mode) {
            IslandMode.EXPANDED -> 168.dp
            IslandMode.MEDIUM -> 74.dp
            IslandMode.COMPACT -> (settings.collapsedHeight + 4).dp
            else -> settings.collapsedHeight.dp
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "height"
    )
    val radius by animateDpAsState(
        targetValue = when (mode) {
            IslandMode.EXPANDED -> if (settings.iosMode) 44.dp else 28.dp
            IslandMode.MEDIUM -> if (settings.iosMode) 32.dp else 24.dp
            else -> settings.cornerRadius.dp
        },
        label = "radius"
    )
    val background by animateColorAsState(
        targetValue = Color(bodyColor).copy(alpha = settings.opacity),
        label = "bg"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1B2A5B), Color(0xFF6C2C86), Color(0xFFB4452F))
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                Text(
                    when (settings.positionMode) {
                        PositionMode.OVERLAP_STATUS_BAR -> "Over the status bar"
                        PositionMode.CUSTOM -> "Custom offset"
                        else -> "Below the status bar"
                    },
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (settings.positionMode == PositionMode.OVERLAP_STATUS_BAR)
                        "Taps land on the strip under the pill"
                    else "The whole island takes taps",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // A stand-in status bar, so the anchor choice is visible rather than described.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STATUS_BAR_BAND)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "9:41",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.weight(1f))
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(5.dp)
                            .background(Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                }
            }

            val islandTop = when (settings.positionMode) {
                PositionMode.OVERLAP_STATUS_BAR -> settings.offsetY.dp
                else -> STATUS_BAR_BAND + settings.offsetY.dp
            }

            if (settings.positionMode == PositionMode.OVERLAP_STATUS_BAR &&
                settings.showTouchHint
            ) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = settings.offsetX.dp,
                            y = STATUS_BAR_BAND + settings.touchStripHeight.dp / 2
                        )
                        .width(26.dp)
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.35f), CircleShape)
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = settings.offsetX.dp, y = islandTop)
                    .width(width)
                    .height(height)
                    .clip(RoundedCornerShape(radius))
                    .background(background)
                    .then(
                        if (settings.borderEnabled) Modifier.border(
                            settings.borderWidth.dp,
                            Color(settings.borderColor),
                            RoundedCornerShape(radius)
                        ) else Modifier
                    )
                    .clickable {
                        // Mirrors the real stepping, so the preview teaches the gesture.
                        onModeChange(
                            when (mode) {
                                IslandMode.PILL -> IslandMode.COMPACT
                                IslandMode.COMPACT -> IslandMode.MEDIUM
                                IslandMode.MEDIUM -> IslandMode.EXPANDED
                                else -> IslandMode.PILL
                            }
                        )
                    }
            ) {
                if (settings.showFauxCamera && mode != IslandMode.EXPANDED &&
                    mode != IslandMode.MEDIUM
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 13.dp)
                            .size(11.dp)
                            .background(Color(0xFF0A0A0C), CircleShape)
                    )
                }
                when (mode) {
                    IslandMode.COMPACT -> CompactContent(accent)
                    IslandMode.MEDIUM -> MediumContent(accent)
                    IslandMode.EXPANDED -> ExpandedContent(accent)
                    else -> Unit
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                IslandMode.PILL to "Idle",
                IslandMode.COMPACT to "Preview",
                IslandMode.MEDIUM to "Small",
                IslandMode.EXPANDED to "Full",
            ).forEach { (value, label) ->
                val selected = mode == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onModeChange(value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactContent(accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(accent.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Bars(accent)
    }
}

@Composable
private fun Bars(accent: Color) {
    Canvas(modifier = Modifier.size(width = 18.dp, height = 12.dp)) {
        val barWidth = size.width / 7f
        val heights = listOf(0.5f, 0.95f, 0.7f, 1f)
        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                color = accent,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = index * barWidth * 2f,
                    y = (size.height - barHeight) / 2f
                ),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
    }
}

/** The halfway card: the header alone, which is what a second tap opens. */
@Composable
private fun MediumContent(accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Black)
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                "Midnight City",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "M83 · Music",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Bars(accent)
    }
}

@Composable
private fun ExpandedContent(accent: Color) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Black)
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    "Midnight City",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "M83 · Music",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Bars(accent)
        }
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White)
            Box(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            }
            Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White)
        }
    }
}
