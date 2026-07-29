package com.example.gamepadmouse

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView

class GamepadAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var cursorView: View? = null
    private var keyboardView: View? = null
    private var joystickOverlay: View? = null

    // Modes
    private var isKeyboardMode = false

    // Cursor position
    private var cursorX = 0f
    private var cursorY = 0f
    private val CURSOR_SPEED = 20f
    private val ANALOG_SPEED = 35f

    // Screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0

    // Keyboard state
    private val keysLayout = arrayOf(
        arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        arrayOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        arrayOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        arrayOf("Z", "X", "C", "V", "B", "N", "M", "DEL"),
        arrayOf("SPACE", "ENTER")
    )
    private var keyboardViews: Array<Array<TextView?>> = Array(5) { Array(10) { null } }
    private var currentKeyRow = 0
    private var currentKeyCol = 0

    // Controller Mapping
    private val buttonMappings = mutableMapOf(
        KeyEvent.KEYCODE_BUTTON_A to "CLICK",
        KeyEvent.KEYCODE_BUTTON_B to "BACK",
        KeyEvent.KEYCODE_BUTTON_X to "NONE",
        KeyEvent.KEYCODE_BUTTON_Y to "TOGGLE_KEYBOARD",
        KeyEvent.KEYCODE_BUTTON_SELECT to "TOGGLE_KEYBOARD",
        KeyEvent.KEYCODE_BUTTON_START to "HOME",
        KeyEvent.KEYCODE_DPAD_UP to "UP",
        KeyEvent.KEYCODE_DPAD_DOWN to "DOWN",
        KeyEvent.KEYCODE_DPAD_LEFT to "LEFT",
        KeyEvent.KEYCODE_DPAD_RIGHT to "RIGHT"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var analogX = 0f
    private var analogY = 0f
    private val analogRunnable = object : Runnable {
        override fun run() {
            if (!isKeyboardMode && (Math.abs(analogX) > 0.1f || Math.abs(analogY) > 0.1f)) {
                cursorX += analogX * ANALOG_SPEED
                cursorY += analogY * ANALOG_SPEED
                clampCursor()
                updateCursorPosition()
            }
            handler.postDelayed(this, 16)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f

        setupCursorOverlay()
        setupKeyboardOverlay()
        setupJoystickOverlay()

        handler.post(analogRunnable)
    }

    private fun setupCursorOverlay() {
        val inflater = LayoutInflater.from(this)
        cursorView = inflater.inflate(R.layout.overlay_cursor, null)

        val cursorLayoutParams = WindowManager.LayoutParams(
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

        windowManager.addView(cursorView, cursorLayoutParams)
        updateCursorPosition()
    }

    private fun setupKeyboardOverlay() {
        val inflater = LayoutInflater.from(this)
        keyboardView = inflater.inflate(R.layout.overlay_keyboard, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        populateKeyboard()
        keyboardView?.visibility = View.GONE
        windowManager.addView(keyboardView, params)
    }

    private fun setupJoystickOverlay() {
        joystickOverlay = View(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_MOVE && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
                    analogX = event.getAxisValue(MotionEvent.AXIS_X)
                    analogY = event.getAxisValue(MotionEvent.AXIS_Y)
                    true
                } else {
                    false
                }
            }
        }
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(joystickOverlay, params)
        // Request focus periodically or on events if needed, to keep joystick input active
        joystickOverlay?.requestFocus()
    }

    private fun populateKeyboard() {
        val rows = arrayOf(
            keyboardView?.findViewById<LinearLayout>(R.id.row_numbers),
            keyboardView?.findViewById<LinearLayout>(R.id.row_1),
            keyboardView?.findViewById<LinearLayout>(R.id.row_2),
            keyboardView?.findViewById<LinearLayout>(R.id.row_3),
            keyboardView?.findViewById<LinearLayout>(R.id.row_4)
        )

        for (i in keysLayout.indices) {
            val rowLayout = rows[i]
            for (j in keysLayout[i].indices) {
                val keyText = keysLayout[i][j]
                val textView = TextView(this).apply {
                    text = keyText
                    textSize = 20f
                    setPadding(24, 24, 24, 24)
                    background = getDrawable(R.drawable.key_background)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(4, 4, 4, 4)
                    }
                    layoutParams = lp
                }
                rowLayout?.addView(textView)
                keyboardViews[i][j] = textView
            }
        }
    }

    private fun clampCursor() {
        if (cursorX < 0) cursorX = 0f
        if (cursorX > screenWidth) cursorX = screenWidth.toFloat()
        if (cursorY < 0) cursorY = 0f
        if (cursorY > screenHeight) cursorY = screenHeight.toFloat()
    }

    private fun updateCursorPosition() {
        cursorView?.findViewById<View>(R.id.cursor_image)?.apply {
            translationX = cursorX
            translationY = cursorY
        }
    }

    private fun toggleKeyboardMode() {
        isKeyboardMode = !isKeyboardMode
        if (isKeyboardMode) {
            keyboardView?.visibility = View.VISIBLE
            cursorView?.visibility = View.GONE
            updateKeyboardFocus()
        } else {
            keyboardView?.visibility = View.GONE
            cursorView?.visibility = View.VISIBLE
            joystickOverlay?.requestFocus()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        val mappedAction = buttonMappings[event.keyCode]

        if (mappedAction == "TOGGLE_KEYBOARD") {
            toggleKeyboardMode()
            return true
        } else if (mappedAction == "HOME") {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        } else if (mappedAction == "BACK" && !isKeyboardMode) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        if (isKeyboardMode) {
            return handleKeyboardModeInput(mappedAction)
        } else {
            return handlePointerModeInput(mappedAction)
        }
    }

    private fun handlePointerModeInput(action: String?): Boolean {
        var handled = false
        when (action) {
            "UP" -> { cursorY -= CURSOR_SPEED; handled = true }
            "DOWN" -> { cursorY += CURSOR_SPEED; handled = true }
            "LEFT" -> { cursorX -= CURSOR_SPEED; handled = true }
            "RIGHT" -> { cursorX += CURSOR_SPEED; handled = true }
            "CLICK" -> {
                performClickAtCursor()
                joystickOverlay?.requestFocus()
                handled = true
            }
        }

        if (handled) {
            clampCursor()
            updateCursorPosition()
            return true
        }
        return false
    }

    private fun handleKeyboardModeInput(action: String?): Boolean {
        when (action) {
            "UP" -> {
                if (currentKeyRow > 0) currentKeyRow--
                clampKeyCol()
                updateKeyboardFocus()
                return true
            }
            "DOWN" -> {
                if (currentKeyRow < keysLayout.size - 1) currentKeyRow++
                clampKeyCol()
                updateKeyboardFocus()
                return true
            }
            "LEFT" -> {
                if (currentKeyCol > 0) currentKeyCol--
                updateKeyboardFocus()
                return true
            }
            "RIGHT" -> {
                if (currentKeyCol < keysLayout[currentKeyRow].size - 1) currentKeyCol++
                updateKeyboardFocus()
                return true
            }
            "CLICK" -> {
                typeCurrentKey()
                return true
            }
            "BACK" -> {
                toggleKeyboardMode()
                return true
            }
        }
        return false
    }

    private fun clampKeyCol() {
        val maxCol = keysLayout[currentKeyRow].size - 1
        if (currentKeyCol > maxCol) {
            currentKeyCol = maxCol
        }
    }

    private fun updateKeyboardFocus() {
        for (i in keyboardViews.indices) {
            for (j in keyboardViews[i].indices) {
                val view = keyboardViews[i][j]
                if (view != null) {
                    if (i == currentKeyRow && j == currentKeyCol) {
                        view.isPressed = true
                        view.setBackgroundColor(0xFF4488FF.toInt())
                    } else {
                        view.isPressed = false
                        view.background = getDrawable(R.drawable.key_background)
                    }
                }
            }
        }
    }

    private fun findInputNode(): AccessibilityNodeInfo? {
        // Since our joystick overlay might have focus, rootInActiveWindow might be our overlay.
        // We should check all windows to find the focused input node.
        val windows = windows
        for (window in windows) {
            if (window.type != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
                val node = window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (node != null) return node
            }
        }
        // Fallback to active window
        return rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    private fun typeCurrentKey() {
        val keyText = keysLayout[currentKeyRow][currentKeyCol]
        val activeNode = findInputNode()

        if (activeNode != null) {
            val arguments = Bundle()
            val currentText = activeNode.text?.toString() ?: ""

            if (keyText == "DEL") {
                if (currentText.isNotEmpty()) {
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        currentText.substring(0, currentText.length - 1)
                    )
                    activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            } else if (keyText == "SPACE") {
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "$currentText "
                )
                activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } else if (keyText == "ENTER") {
                 arguments.putCharSequence(
                     AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                     "$currentText\n"
                 )
                 activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } else {
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    currentText + keyText
                )
                activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            activeNode.recycle()
        }
    }

    private fun performClickAtCursor() {
        val path = Path()
        path.moveTo(cursorX, cursorY)
        // Add a tiny lineTo to avoid empty path crash on some Android versions
        path.lineTo(cursorX + 0.1f, cursorY + 0.1f)
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Request focus back to joystick overlay if a window changed and we are in pointer mode
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isKeyboardMode) {
            joystickOverlay?.requestFocus()
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(analogRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(analogRunnable)
        cursorView?.let { windowManager.removeView(it) }
        keyboardView?.let { windowManager.removeView(it) }
        joystickOverlay?.let { windowManager.removeView(it) }
    }
}
