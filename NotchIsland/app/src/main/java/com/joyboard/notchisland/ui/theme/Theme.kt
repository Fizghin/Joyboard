package com.joyboard.notchisland.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.joyboard.notchisland.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF00184C),
    secondary = Color(0xFF7ED6A5),
    background = Color(0xFF08080B),
    surface = Color(0xFF111117),
    surfaceVariant = Color(0xFF1B1B22),
    onSurfaceVariant = Color(0xFFC9C9D4),
    outline = Color(0xFF3A3A44),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color.White,
    secondary = Color(0xFF178A5A),
    background = Color(0xFFF6F6F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEDF3),
    onSurfaceVariant = Color(0xFF45454F),
    outline = Color(0xFFCFCFD8),
)

@Composable
fun NotchIslandTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
