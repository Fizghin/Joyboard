package com.joyboard.notchisland.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import com.joyboard.notchisland.util.dp
import kotlin.math.sin
import kotlin.random.Random

/** The four dancing bars iOS shows on the right of the island while music is playing. */
class WaveformView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val bars = 4
    private val phases = FloatArray(bars) { Random.nextFloat() * 6.28f }
    private val speeds = FloatArray(bars) { 0.9f + Random.nextFloat() * 0.8f }
    private var phase = 0f
    private var animator: ValueAnimator? = null

    var barColor: Int = 0xFF3B82F6.toInt()
        set(value) { field = value; invalidate() }

    var playing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) start() else stop()
        }

    init {
        layoutParams = android.widget.LinearLayout.LayoutParams(18.dp, 14.dp)
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 6.28f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        paint.color = barColor
        val barWidth = w / (bars * 2f - 1f)
        for (i in 0 until bars) {
            val amplitude = if (playing) {
                (0.45f + 0.55f * ((sin(phase * speeds[i] + phases[i]) + 1f) / 2f))
            } else {
                0.28f
            }
            val barHeight = h * amplitude
            val left = i * barWidth * 2f
            rect.set(left, (h - barHeight) / 2f, left + barWidth, (h + barHeight) / 2f)
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
