package com.joyboard.notchisland.util

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.DisplayCutout
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.joyboard.notchisland.island.Hole

/** The camera cutout's size, for display in diagnostics. */
data class CutoutInfo(val widthDp: Int, val heightDp: Int, val centerOffsetDp: Int, val topDp: Int)

/** Why a detection did not produce a hole. */
enum class DetectFailure(val message: String) {
    UNSUPPORTED("This Android version does not report camera cutouts"),
    NOT_PORTRAIT("Hold the phone upright and try again"),
    NO_CUTOUT("This phone does not report a camera cutout at the top of the screen"),
}

/**
 * Reads the camera cutout from the phone itself. Android knows exactly where its own hardware
 * is — on Android 12 and later down to the precise outline — so this beats any preset.
 */
object CutoutDetector {

    fun detectHole(context: Context): Result<Hole> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(DetectionException(DetectFailure.UNSUPPORTED))
        }
        val windowManager = context.getSystemService(WindowManager::class.java)
            ?: return Result.failure(DetectionException(DetectFailure.UNSUPPORTED))
        if (rotation(context, windowManager) != Surface.ROTATION_0) {
            return Result.failure(DetectionException(DetectFailure.NOT_PORTRAIT))
        }
        val cutout = cutout(windowManager)
            ?: return Result.failure(DetectionException(DetectFailure.NO_CUTOUT))
        val bounds = outline(cutout)
            ?: return Result.failure(DetectionException(DetectFailure.NO_CUTOUT))

        val density = context.resources.displayMetrics.density
        val screenWidth = screenWidthPx(context, windowManager)
        return Result.success(
            Hole(
                centerX = (bounds.centerX() - screenWidth / 2f) / density,
                centerY = bounds.centerY() / density,
                width = bounds.width() / density,
                height = bounds.height() / density,
            )
        )
    }

    /** Kept for diagnostics, which only want the numbers. */
    fun detect(context: Context): CutoutInfo? = detectHole(context).getOrNull()?.let {
        CutoutInfo(it.width.toInt(), it.height.toInt(), it.centerX.toInt(), (it.centerY - it.height / 2).toInt())
    }

    fun screenWidthDp(context: Context): Float {
        val windowManager = context.getSystemService(WindowManager::class.java)
        return screenWidthPx(context, windowManager) / context.resources.displayMetrics.density
    }

    /**
     * The hole's outline. Android 12 exposes the real shape; before that there are only bounding
     * rectangles, which some makers stretch up to the screen edge — so for those the hole is
     * taken to hug the rectangle's bottom, which is where the lens actually is.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun outline(cutout: DisplayCutout): RectF? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cutout.cutoutPath?.let { path ->
                val bounds = RectF()
                @Suppress("DEPRECATION")
                path.computeBounds(bounds, true)
                if (bounds.width() > 0f && bounds.height() > 0f && bounds.top < TOP_BAND_PX) return bounds
            }
        }
        val top: Rect = cutout.boundingRectTop
        if (top.isEmpty) return null
        val width = top.width().toFloat()
        val height = top.height().toFloat()
        // A punch hole's rectangle is roughly square; one much taller than it is wide has been
        // stretched to the edge, so trim it back to a circle sitting on its bottom.
        return if (height > width * 1.3f) {
            RectF(top.left.toFloat(), top.bottom - width, top.right.toFloat(), top.bottom.toFloat())
        } else {
            RectF(top)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cutout(windowManager: WindowManager): DisplayCutout? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets.displayCutout
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.cutout
        }

    /**
     * Via DisplayManager, which works for any context. Context.getDisplay() throws for the
     * application context, and detection is run from there as well as from an activity.
     */
    private fun rotation(context: Context, windowManager: WindowManager): Int =
        context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation

    private fun screenWidthPx(context: Context, windowManager: WindowManager?): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            windowManager.currentWindowMetrics.bounds.width().toFloat()
        } else {
            context.resources.displayMetrics.widthPixels.toFloat()
        }

    /** Cutouts further down than this are not the front camera. */
    private const val TOP_BAND_PX = 400f
}

class DetectionException(val reason: DetectFailure) : Exception(reason.message)
