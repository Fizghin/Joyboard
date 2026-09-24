package com.joyboard.notchisland.util

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.WindowManager

/** The camera cutout's real size, so the island can be matched to the hardware it imitates. */
data class CutoutInfo(val widthDp: Int, val heightDp: Int, val centerOffsetDp: Int, val topDp: Int)

object CutoutDetector {

    /** Null when the display has no cutout, or the platform will not tell us about it. */
    fun detect(context: Context): CutoutInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return null
        val bounds: List<Rect> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.windowInsets.displayCutout?.boundingRects
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.cutout?.boundingRects
            }
        }.getOrNull().orEmpty()

        val top = bounds.filter { it.top <= 0 || it.top < 120.dp }.ifEmpty { return null }
        val union = Rect(top.first())
        top.drop(1).forEach { union.union(it) }
        if (union.width() <= 0 || union.height() <= 0) return null

        val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            context.resources.displayMetrics.widthPixels
        }
        val density = context.resources.displayMetrics.density
        val cutoutCenter = union.centerX()
        return CutoutInfo(
            widthDp = (union.width() / density).toInt(),
            heightDp = (union.height() / density).toInt(),
            centerOffsetDp = ((cutoutCenter - screenWidth / 2) / density).toInt(),
            topDp = (union.top / density).toInt(),
        )
    }
}
