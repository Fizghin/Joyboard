package com.example.gamepaddock

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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GamepadAccessibilityService : AccessibilityService() {

    private lateinit var settingsRepo: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var cursorOverlay: CursorOverlay? = null
    private var keyboardOverlay: KeyboardOverlay? = null

    private var windowManager: WindowManager? = null
    private var joystickCaptureView: View? = null

    // State
    private var buttonMappings = mapOf<Int, String>()
    private var sensitivity = 1.0f
    private var accelerationEnabled = true

    // Joystick polling
    private val handler = Handler(Looper.getMainLooper())
    private var analogX = 0f
    private var analogY = 0f
    private var dpadX = 0f
    private var dpadY = 0f
    private val BASE_SPEED = 15f
    private var currentSpeedMultiplier = 1f
    private var isDragging = false

    private val inputRunnable = object : Runnable {
        override fun run() {
            if (cursorOverlay?.isShowing() == true) {
                val dx = analogX + dpadX
                val dy = analogY + dpadY

                if (Math.abs(dx) > 0.1f || Math.abs(dy) > 0.1f) {
                    if (accelerationEnabled) {
                        currentSpeedMultiplier = (currentSpeedMultiplier + 0.05f).coerceAtMost(3f)
                    } else {
                        currentSpeedMultiplier = 1f
                    }

                    val moveX = dx * BASE_SPEED * sensitivity * currentSpeedMultiplier
                    val moveY = dy * BASE_SPEED * sensitivity * currentSpeedMultiplier

                    cursorOverlay?.let {
                        it.updatePosition(it.x + moveX, it.y + moveY)
                    }

                    if (isDragging) {
                        // In a real implementation, we would dispatch a continued gesture stroke here.
                        // For simplicity in this demo, we simulate swipe/scroll by dispatching a standard swipe
                        // when the drag button is released based on delta.
                    }
                } else {
                    currentSpeedMultiplier = 1f
                }
            }
            handler.postDelayed(this, 16) // ~60fps
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepo = SettingsRepository(this)

        loadSettings()

        cursorOverlay = CursorOverlay(this)
        keyboardOverlay = KeyboardOverlay(this) { key -> handleKeyboardTyping(key) }

        cursorOverlay?.show()
        setupJoystickCaptureOverlay()

        handler.post(inputRunnable)
    }

    private fun loadSettings() {
        serviceScope.launch {
            buttonMappings = settingsRepo.buttonMappingsFlow.first()
            sensitivity = settingsRepo.sensitivityFlow.first()
            accelerationEnabled = settingsRepo.accelerationEnabledFlow.first()

            val size = settingsRepo.cursorSizeFlow.first()
            cursorOverlay?.setSizeMultiplier(size)
        }
    }

    private fun setupJoystickCaptureOverlay() {
        // We use a transparent focusable overlay to capture generic motion events from the joystick.
        joystickCaptureView = View(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setOnGenericMotionListener { _, event ->
                if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
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

        windowManager?.addView(joystickCaptureView, params)
        joystickCaptureView?.requestFocus()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            val action = buttonMappings[event.keyCode]
            if (action == "DRAG") {
                isDragging = false
                return true
            }

            // Handle D-Pad release
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> dpadY = 0f
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> dpadX = 0f
            }
            return false
        }

        // Action Down
        val mappedAction = buttonMappings[event.keyCode]

        // Handle D-pad for fine movement if not mapped to something else
        if (mappedAction == null) {
             when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { dpadY = -0.5f; return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { dpadY = 0.5f; return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { dpadX = -0.5f; return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { dpadX = 0.5f; return true }
            }
            return false
        }

        var handled = false
        when (mappedAction) {
            "CLICK" -> {
                performCursorClick()
                handled = true
            }
            "LONG_CLICK" -> {
                performCursorLongClick()
                handled = true
            }
            "BACK" -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                handled = true
            }
            "HOME" -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                handled = true
            }
            "RECENTS" -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                handled = true
            }
            "TOGGLE_KEYBOARD" -> {
                if (keyboardOverlay?.isShowing() == true) {
                    keyboardOverlay?.hide()
                } else {
                    keyboardOverlay?.show()
                }
                handled = true
            }
            "DRAG" -> {
                isDragging = true
                handled = true
            }
        }

        return handled
    }

    private fun performCursorClick() {
        val cx = cursorOverlay?.x ?: return
        val cy = cursorOverlay?.y ?: return

        // If keyboard is showing, check if we clicked a key first
        if (keyboardOverlay?.isShowing() == true) {
            if (keyboardOverlay?.handleClickAt(cx, cy) == true) {
                return // Handled by keyboard
            }
        }

        // Otherwise dispatch system click
        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + 0.1f, cy + 0.1f) // Avoid 0-length path crash
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, object: GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                // Ensure our capture view keeps focus after a click opens a new app
                joystickCaptureView?.requestFocus()
            }
        }, null)
    }

    private fun performCursorLongClick() {
        val cx = cursorOverlay?.x ?: return
        val cy = cursorOverlay?.y ?: return

        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + 0.1f, cy + 0.1f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 600)) // 600ms for long press
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun handleKeyboardTyping(key: String) {
        val activeNode = findFocusedEditableNode() ?: return

        val arguments = Bundle()
        val currentText = activeNode.text?.toString() ?: ""

        when (key) {
            "DEL" -> {
                if (currentText.isNotEmpty()) {
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        currentText.substring(0, currentText.length - 1)
                    )
                    activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }
            "SPACE" -> {
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "$currentText "
                )
                activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            "ENTER" -> {
                // Try ACTION_CLICK (often submits forms or hits 'search') or fallback to newline
                if (!activeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "$currentText\n"
                    )
                    activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }
            else -> {
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    currentText + key
                )
                activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        }
        activeNode.recycle()
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val windows = windows
        for (window in windows) {
            if (window.type != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
                val node = window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (node != null && node.isEditable) return node
            }
        }
        val rootNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return if (rootNode?.isEditable == true) rootNode else null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            joystickCaptureView?.requestFocus()

            // Reload settings in case we just came back from the settings app
            loadSettings()
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(inputRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(inputRunnable)
        cursorOverlay?.hide()
        keyboardOverlay?.hide()
        joystickCaptureView?.let { windowManager?.removeView(it) }
    }
}
