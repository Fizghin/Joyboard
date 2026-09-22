package com.joyboard.notchisland.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.joyboard.notchisland.util.dp
import com.joyboard.notchisland.util.withAlpha

/** Circular progress used for charging level and timer countdowns. */
class RingProgressView(context: Context) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val oval = RectF()
    private var animator: ValueAnimator? = null
    private var shown = 0f

    var strokeWidth: Float = 3f.dp
        set(value) { field = value; trackPaint.strokeWidth = value; progressPaint.strokeWidth = value; invalidate() }

    var label: String? = null
        set(value) { field = value; invalidate() }

    var labelSizePx: Float = 9f.dp
        set(value) { field = value; textPaint.textSize = value; invalidate() }

    var ringColor: Int = 0xFF34C759.toInt()
        set(value) {
            field = value
            progressPaint.color = value
            trackPaint.color = value.withAlpha(0.22f)
            invalidate()
        }

    var progress: Float = 0f
        set(value) {
            val target = value.coerceIn(0f, 1f)
            field = target
            animator?.cancel()
            animator = ValueAnimator.ofFloat(shown, target).apply {
                duration = 420
                addUpdateListener { a ->
                    shown = a.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

    init {
        strokeWidth = 3f.dp
        ringColor = 0xFF34C759.toInt()
        labelSizePx = 9f.dp
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = strokeWidth / 2f + 1f
        oval.set(pad, pad, width - pad, height - pad)
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)
        canvas.drawArc(oval, -90f, 360f * shown, false, progressPaint)
        label?.let {
            val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(it, width / 2f, baseline, textPaint)
        }
    }
}
