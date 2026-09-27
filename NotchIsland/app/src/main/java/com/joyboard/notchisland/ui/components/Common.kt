package com.joyboard.notchisland.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.joyboard.notchisland.util.DynamicColors
import kotlin.math.roundToInt
import com.joyboard.notchisland.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.joyboard.notchisland.ui.theme.AppSurfaces
import com.joyboard.notchisland.ui.theme.Tint

/** A rounded, coloured tile with a white symbol — the mark beside each setting. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 34.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.92f), tint))),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
    }
}

/**
 * A group of settings: its heading and a line of explanation sit above a rounded card, the way
 * system settings are laid out, so the card itself holds nothing but the rows.
 */
@Composable
fun SectionCard(
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = if (subtitle == null) 8.dp else 2.dp)
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp)
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = AppSurfaces.card,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

/** The shared shape of every row: an optional tile, a title and detail, and whatever ends it. */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    tint: Color,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconTile(icon, tint)
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tint: Color = Tint.Blue,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(title, subtitle, icon, tint, enabled, onClick = { onCheckedChange(!checked) }) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else null,
        )
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: String = value.roundToInt().toString(),
    icon: ImageVector? = null,
    tint: Color = Tint.Blue,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconTile(icon, tint)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // The value sits in a small pill, so it reads as the setting's answer.
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(start = if (icon != null) 48.dp else 0.dp)
        )
    }
}

@Composable
fun NavRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = Tint.Gray,
    onClick: () -> Unit,
) {
    SettingRow(title, subtitle, icon, tint, onClick = onClick) {
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun <T> DropdownRow(
    title: String,
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    icon: ImageVector? = null,
    tint: Color = Tint.Blue,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingRow(title, null, icon, tint, onClick = { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label(selected),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(max = 170.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Rounded.UnfoldMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        }
    }
}

private val palette = listOf(
    0xFF000000, 0xFF0B0B10, 0xFF1C1C1E, 0xFF2C2C2E,
    0xFF3B82F6, 0xFF34C759, 0xFFFF9F0A, 0xFFFF375F,
    0xFFAF52DE, 0xFF5AC8FA, 0xFFFFFFFF,
)

@Composable
fun ColorRow(
    title: String,
    selected: Int,
    icon: ImageVector? = null,
    tint: Color = Tint.Purple,
    onSelected: (Int) -> Unit,
) {
    val context = LocalContext.current
    // Wallpaper colours lead, so the easiest choice is the one that already matches.
    val swatches = remember {
        (DynamicColors.swatches(context) + palette.map { it.toInt() }).distinct()
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            if (icon != null) {
                IconTile(icon, tint)
                Spacer(Modifier.width(14.dp))
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        if (DynamicColors.supported) {
            Text(
                stringResource(R.string.from_wallpaper_first_then_presets),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp)
        ) {
            items(swatches.size) { index ->
                val color = swatches[index]
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(color), CircleShape)
                        .border(
                            width = if (color == selected) 3.dp else 1.dp,
                            color = if (color == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onSelected(color) }
                )
            }
        }
    }
}

@Composable
fun ActionRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 10.dp)) {
        Text(label)
    }
}

@Composable
fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    )
}
