package com.joyboard.notchisland.util

import android.content.Context
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * The wallpaper-derived Material You palette, which Android exposes as public system colour
 * resources from API 31. Below that a fixed palette stands in so every screen still has
 * something sensible to show.
 */
object DynamicColors {

    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val fallbackAccents = listOf(
        0xFF3B82F6.toInt(), 0xFF34C759.toInt(), 0xFFFF9F0A.toInt(),
        0xFFFF375F.toInt(), 0xFFAF52DE.toInt(), 0xFF5AC8FA.toInt(),
    )

    /** The accent the island should use, bright enough to read on its dark body. */
    fun accent(context: Context, dark: Boolean = true): Int =
        if (supported) {
            color(context, if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
        } else {
            fallbackAccents.first()
        }

    /** A neutral surface that matches the wallpaper, for the island body. */
    fun surface(context: Context, dark: Boolean = true): Int =
        if (supported) {
            color(context, if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50)
        } else {
            0xFF000000.toInt()
        }

    /** Swatches for the picker: three accent families plus two neutrals. */
    fun swatches(context: Context): List<Int> =
        if (supported) systemSwatches(context).distinct() else fallbackAccents

    @RequiresApi(Build.VERSION_CODES.S)
    private fun systemSwatches(context: Context): List<Int> = listOf(
        android.R.color.system_accent1_100,
        android.R.color.system_accent1_200,
        android.R.color.system_accent1_400,
        android.R.color.system_accent1_600,
        android.R.color.system_accent2_200,
        android.R.color.system_accent2_400,
        android.R.color.system_accent3_200,
        android.R.color.system_accent3_400,
        android.R.color.system_neutral1_900,
        android.R.color.system_neutral1_50,
    ).map { color(context, it) }

    private fun color(context: Context, @ColorRes id: Int): Int = ContextCompat.getColor(context, id)
}
