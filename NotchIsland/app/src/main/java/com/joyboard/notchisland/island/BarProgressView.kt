package com.joyboard.notchisland.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** A rounded, flat progress bar — the island's answer to a download or a delivery. */
class BarProgressView(context: Context) : View(context) {

    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    var color: Int = IslandColors.WHITE
        set(value) { field = value; fill.color = value; invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = IslandColors.FILL }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = IslandColors.WHITE }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, track)
        if (progress <= 0f) return
        // Never narrower than it is tall, so the smallest amount still reads as a rounded bar.
        rect.right = (width * progress).coerceAtLeast(height.toFloat())
        canvas.drawRoundRect(rect, r, r, fill)
    }
}
