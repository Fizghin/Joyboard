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
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.IslandMode

/** A faithful, tappable mock of the overlay so settings can be judged without leaving the app. */
@Composable
fun IslandPreview(
    settings: IslandSettings,
    mode: IslandMode,
    onModeChange: (IslandMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(settings.accentColor)
    val width by animateDpAsState(
        targetValue = when (mode) {
            IslandMode.EXPANDED -> settings.expandedWidth.dp
            IslandMode.COMPACT -> settings.compactWidth.dp
            else -> settings.collapsedWidth.dp
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "width"
    )
    val height by animateDpAsState(
        targetValue = when (mode) {
            IslandMode.EXPANDED -> 168.dp
            IslandMode.COMPACT -> (settings.collapsedHeight + 4).dp
            else -> settings.collapsedHeight.dp
        },
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "height"
    )
    val radius by animateDpAsState(
        targetValue = when (mode) {
            IslandMode.EXPANDED -> 28.dp
            else -> settings.cornerRadius.dp
        },
        label = "radius"
    )
    val background by animateColorAsState(
        targetValue = Color(settings.backgroundColor).copy(alpha = settings.opacity),
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
                    "9:41",
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Preview wallpaper",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = settings.offsetX.dp, y = (settings.offsetY + 10).dp)
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
                        onModeChange(
                            when (mode) {
                                IslandMode.PILL -> IslandMode.COMPACT
                                IslandMode.COMPACT -> IslandMode.EXPANDED
                                else -> IslandMode.PILL
                            }
                        )
                    }
            ) {
                when (mode) {
                    IslandMode.COMPACT -> CompactContent(accent)
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
                IslandMode.COMPACT to "Compact",
                IslandMode.EXPANDED to "Expanded",
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
