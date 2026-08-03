package com.example.gamepaddock

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

class CursorOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var cursorView: View? = null
    private var imageView: ImageView? = null

    var x: Float = 0f
    var y: Float = 0f

    private var screenWidth = 0
    private var screenHeight = 0

    private var baseSize = 48 // Base cursor size in px

    init {
        val displayMetrics = context.resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        x = screenWidth / 2f
        y = screenHeight / 2f
    }

    fun show() {
        if (cursorView != null) return

        cursorView = LayoutInflater.from(context).inflate(R.layout.view_cursor, null)
        imageView = cursorView?.findViewById(R.id.cursor_icon)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(cursorView, params)
        updatePosition()
    }

    fun hide() {
        cursorView?.let { windowManager.removeView(it) }
        cursorView = null
        imageView = null
    }

    fun updatePosition(newX: Float = x, newY: Float = y) {
        x = newX.coerceIn(0f, screenWidth.toFloat())
        y = newY.coerceIn(0f, screenHeight.toFloat())

        imageView?.translationX = x
        imageView?.translationY = y
    }

    fun setSizeMultiplier(multiplier: Float) {
        val size = (baseSize * multiplier).toInt()
        imageView?.layoutParams?.width = size
        imageView?.layoutParams?.height = size
        imageView?.requestLayout()
    }

    fun isShowing() = cursorView != null
}
