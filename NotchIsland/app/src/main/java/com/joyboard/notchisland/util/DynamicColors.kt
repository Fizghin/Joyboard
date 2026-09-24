package com.joyboard.notchisland.util

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The wallpaper-derived Material You palette. Android exposes it as a fixed set of system
 * colour resources from API 31; below that we fall back to a fixed palette so every screen
 * still has something sensible to show.
 */
object DynamicColors {

    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val fallbackAccents = listOf(
        0xFF3B82F6.toInt(), 0xFF34C759.toInt(), 0xFFFF9F0A.toInt(),
        0xFFFF375F.toInt(), 0xFFAF52DE.toInt(), 0xFF5AC8FA.toInt(),
    )

    /** The accent the island should use, bright enough to read on its dark body. */
    fun accent(context: Context, dark: Boolean = true): Int =
        system(context, if (dark) ACCENT1_200 else ACCENT1_600) ?: fallbackAccents.first()

    /** A neutral surface that matches the wallpaper, for the island body. */
    fun surface(context: Context, dark: Boolean = true): Int =
        system(context, if (dark) NEUTRAL1_900 else NEUTRAL1_50) ?: 0xFF000000.toInt()

    /** Swatches for the picker: three accent families plus two neutrals. */
    fun swatches(context: Context): List<Int> {
        if (!supported) return fallbackAccents
        val resources = listOf(
            ACCENT1_100, ACCENT1_200, ACCENT1_400, ACCENT1_600,
            ACCENT2_200, ACCENT2_400,
            ACCENT3_200, ACCENT3_400,
            NEUTRAL1_900, NEUTRAL1_50,
        )
        return resources.mapNotNull { system(context, it) }.distinct().ifEmpty { fallbackAccents }
    }

    private fun system(context: Context, name: String): Int? {
        if (!supported) return null
        return runCatching {
            val id = context.resources.getIdentifier(name, "color", "android")
            if (id == 0) null else ContextCompat.getColor(context, id)
        }.getOrNull()
    }

    private const val ACCENT1_100 = "system_accent1_100"
    private const val ACCENT1_200 = "system_accent1_200"
    private const val ACCENT1_400 = "system_accent1_400"
    private const val ACCENT1_600 = "system_accent1_600"
    private const val ACCENT2_200 = "system_accent2_200"
    private const val ACCENT2_400 = "system_accent2_400"
    private const val ACCENT3_200 = "system_accent3_200"
    private const val ACCENT3_400 = "system_accent3_400"
    private const val NEUTRAL1_50 = "system_neutral1_50"
    private const val NEUTRAL1_900 = "system_neutral1_900"
}
