package com.example.gamepaddock

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class KeyboardOverlay(private val context: Context, private val onKeyClicked: (String) -> Unit) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null

    // UI References
    private var keysMap = mutableMapOf<View, String>()

    // QWERTY layout
    private val layoutLetters = arrayOf(
        arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        arrayOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        arrayOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        arrayOf("SHIFT", "Z", "X", "C", "V", "B", "N", "M", "DEL"),
        arrayOf("?123", "SPACE", "ENTER")
    )

    private val layoutSymbols = arrayOf(
        arrayOf("~", "`", "|", "•", "√", "π", "÷", "×", "{", "}"),
        arrayOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
        arrayOf("*", "\"", "'", ":", ";", "!", "?", "%", "[", "]"),
        arrayOf("SHIFT", "<", ">", "=", "£", "¢", "€", "¥", "DEL"),
        arrayOf("ABC", "SPACE", "ENTER")
    )

    private var isShifted = false
    private var keyboardMode = 0 // 0=Letters, 1=Numbers/Symbols

    fun show() {
        if (view != null) return

        view = LayoutInflater.from(context).inflate(R.layout.view_keyboard, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        rebuildKeyboard()
        windowManager.addView(view, params)
    }

    fun hide() {
        view?.let { windowManager.removeView(it) }
        view = null
        keysMap.clear()
    }

    fun isShowing() = view != null

    fun getHoveredKey(x: Float, y: Float): View? {
        if (!isShowing()) return null
        for ((keyView, _) in keysMap) {
            val location = IntArray(2)
            keyView.getLocationOnScreen(location)
            val rect = android.graphics.Rect(
                location[0],
                location[1],
                location[0] + keyView.width,
                location[1] + keyView.height
            )
            if (rect.contains(x.toInt(), y.toInt())) {
                return keyView
            }
        }
        return null
    }

    // Called when the user clicks 'A' while the cursor is active
    // We check if the cursor coordinates intersect any key
    fun handleClickAt(x: Float, y: Float): Boolean {
        if (!isShowing()) return false

        val keyView = getHoveredKey(x, y)
        if (keyView != null) {
            val keyValue = keysMap[keyView] ?: return false
            handleKeyPress(keyValue)

            // Visual feedback
            keyView.isPressed = true
            keyView.postDelayed({ keyView.isPressed = false }, 100)
            return true
        }
        return false
    }

    private fun handleKeyPress(key: String) {
        when (key) {
            "SHIFT" -> {
                isShifted = !isShifted
                rebuildKeyboard()
            }
            "?123" -> {
                keyboardMode = 1
                isShifted = false
                rebuildKeyboard()
            }
            "ABC" -> {
                keyboardMode = 0
                isShifted = false
                rebuildKeyboard()
            }
            else -> {
                val outChar = if (isShifted && key.length == 1) key.uppercase() else key.lowercase()
                onKeyClicked(if (key.length > 1) key else outChar) // Keep DEL, SPACE, ENTER as-is
                if (isShifted) {
                    isShifted = false
                    rebuildKeyboard()
                }
            }
        }
    }

    private fun rebuildKeyboard() {
        val layout = if (keyboardMode == 0) layoutLetters else layoutSymbols
        buildKeys(layout)
    }

    private fun buildKeys(layout: Array<Array<String>>) {
        keysMap.clear()
        val container = view?.findViewById<LinearLayout>(R.id.keyboard_container) ?: return
        container.removeAllViews()

        for (row in layout) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 4) }
            }

            for (key in row) {
                val keyView = TextView(context).apply {
                    text = if (isShifted && key.length == 1) key.uppercase() else if (key.length == 1 && keyboardMode == 0) key.lowercase() else key
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setPadding(16, 24, 16, 24)
                    background = context.getDrawable(R.drawable.key_background)

                    val weight = if (key == "SPACE") 4f else if (key.length > 1) 1.5f else 1f

                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                        setMargins(4, 0, 4, 0)
                    }

                    // Allow normal touch testing if run in standard activity, but mainly we use the coordinates
                    setOnClickListener { handleKeyPress(key) }
                }

                keysMap[keyView] = key
                rowLayout.addView(keyView)
            }
            container.addView(rowLayout)
        }
    }
}
