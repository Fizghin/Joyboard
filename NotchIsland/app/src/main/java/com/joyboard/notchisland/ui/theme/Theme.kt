package com.joyboard.notchisland.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyboard.notchisland.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EB1FF),
    onPrimary = Color(0xFF00184C),
    primaryContainer = Color(0xFF223A7A),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF7ED6A5),
    tertiary = Color(0xFFD7A6FF),
    tertiaryContainer = Color(0xFF4B2A6B),
    background = Color(0xFF0B0B0F),
    surface = Color(0xFF0B0B0F),
    surfaceContainerLowest = Color(0xFF07070A),
    surfaceContainerLow = Color(0xFF131318),
    surfaceContainer = Color(0xFF17171D),
    surfaceContainerHigh = Color(0xFF1D1D24),
    surfaceContainerHighest = Color(0xFF26262E),
    surfaceVariant = Color(0xFF26262E),
    onSurfaceVariant = Color(0xFFB9B9C6),
    outline = Color(0xFF3A3A44),
    outlineVariant = Color(0xFF2A2A32),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF001848),
    secondary = Color(0xFF178A5A),
    tertiary = Color(0xFF8A3FD0),
    tertiaryContainer = Color(0xFFF1DBFF),
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFF2F2F7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFEFEFF4),
    surfaceContainerHigh = Color(0xFFE8E8EE),
    surfaceContainerHighest = Color(0xFFE1E1E8),
    surfaceVariant = Color(0xFFE6E6EC),
    onSurfaceVariant = Color(0xFF5A5A66),
    outline = Color(0xFFCFCFD8),
    outlineVariant = Color(0xFFE2E2E8),
)

/** Whether the app is drawing dark right now, whichever way that was decided. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * The page and the cards on it. Material You's own surface and background are the same colour,
 * so a card in the default "surface" disappears into the page; these two always separate.
 */
object AppSurfaces {
    val page: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerLowest
        else MaterialTheme.colorScheme.surfaceContainer

    val card: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLowest
}

/**
 * The colours of the icon tiles beside each setting — Apple's system palette, since the island
 * itself is modelled on the iPhone. Each group of settings keeps to one or two of them.
 */
object Tint {
    val Red = Color(0xFFFF3B30)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFB800)
    val Green = Color(0xFF34C759)
    val Mint = Color(0xFF00C7BE)
    val Teal = Color(0xFF30B0C7)
    val Cyan = Color(0xFF32ADE6)
    val Blue = Color(0xFF007AFF)
    val Indigo = Color(0xFF5856D6)
    val Purple = Color(0xFFAF52DE)
    val Pink = Color(0xFFFF2D55)
    val Brown = Color(0xFFA2845E)
    val Gray = Color(0xFF8E8E93)
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
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
    val colors: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
