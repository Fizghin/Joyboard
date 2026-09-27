package com.joyboard.notchisland.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.joyboard.notchisland.util.dp
import com.joyboard.notchisland.util.withAlpha

/** A drawn battery — outline, terminal, fill to the level and a bolt while charging. */
class BatteryView(context: Context) : View(context) {

    var level: Int = 0
        set(value) { field = value.coerceIn(0, 100); invalidate() }

    var color: Int = IslandColors.GREEN
        set(value) { field = value; invalidate() }

    var charging: Boolean = false
        set(value) { field = value; invalidate() }

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bolt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = 0xFF000000.toInt()
    }
    private val body = RectF()
    private val inner = RectF()
    private val nub = RectF()
    private val boltPath = Path()

    init {
        contentDescription = null
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = (height * 0.07f).coerceAtLeast(1.5f.dp)
        val nubWidth = height * 0.12f
        val radius = height * 0.26f
        body.set(stroke / 2f, stroke / 2f, width - nubWidth - stroke, height - stroke / 2f)
        outline.strokeWidth = stroke
        outline.color = IslandColors.WHITE.withAlpha(0.45f)
        canvas.drawRoundRect(body, radius, radius, outline)

        nub.set(body.right + stroke * 0.6f, height * 0.33f, width.toFloat(), height * 0.67f)
        fill.color = IslandColors.WHITE.withAlpha(0.45f)
        canvas.drawRoundRect(nub, nubWidth, nubWidth, fill)

        val gap = stroke * 1.4f
        inner.set(body.left + gap, body.top + gap, body.right - gap, body.bottom - gap)
        // Even a nearly empty battery shows a sliver, as the real indicator does.
        val shown = (level / 100f).coerceAtLeast(if (level > 0) 0.06f else 0f)
        inner.right = inner.left + inner.width() * shown
        fill.color = color
        canvas.drawRoundRect(inner, radius - gap / 2f, radius - gap / 2f, fill)

        if (charging) drawBolt(canvas, stroke)
    }

    private fun drawBolt(canvas: Canvas, stroke: Float) {
        val h = body.height() * 0.78f
        val w = h * 0.62f
        val left = body.centerX() - w / 2f
        val top = body.centerY() - h / 2f
        boltPath.reset()
        boltPath.moveTo(left + w * 0.62f, top)
        boltPath.lineTo(left, top + h * 0.57f)
        boltPath.lineTo(left + w * 0.45f, top + h * 0.57f)
        boltPath.lineTo(left + w * 0.36f, top + h)
        boltPath.lineTo(left + w, top + h * 0.40f)
        boltPath.lineTo(left + w * 0.55f, top + h * 0.40f)
        boltPath.close()
        boltEdge.strokeWidth = stroke
        canvas.drawPath(boltPath, boltEdge)
        bolt.color = IslandColors.WHITE
        canvas.drawPath(boltPath, bolt)
    }
}
