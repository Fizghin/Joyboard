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
    private var longPressDuration = 600L

    // Joystick polling
    private val handler = Handler(Looper.getMainLooper())
    private var analogX = 0f
    private var analogY = 0f
    private var dpadX = 0f
    private var dpadY = 0f
    private val BASE_SPEED = 15f
    private var currentSpeedMultiplier = 1f

    // Gestures state
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f

    private var isScrolling = false
    private var scrollStartX = 0f
    private var scrollStartY = 0f

    // Track the last focused editable node since the transparent overlay steals actual window focus
    private var lastFocusedEditableNode: AccessibilityNodeInfo? = null

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
                        val isHovering = keyboardOverlay?.getHoveredKey(it.x, it.y) != null
                        it.setHoverState(isHovering)
                    }

                    // If scroll modifier is held, dispatch scroll gestures periodically based on distance
                    if (isScrolling) {
                        handleContinuousScroll(dx, dy)
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
            longPressDuration = settingsRepo.longPressDurationFlow.first()

            val size = settingsRepo.cursorSizeFlow.first()
            cursorOverlay?.setSizeMultiplier(size)
        }
    }

    private fun setupJoystickCaptureOverlay() {
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

    private fun regainFocus() {
        joystickCaptureView?.requestFocus()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            val action = buttonMappings[event.keyCode]
            if (action == "DRAG") {
                if (isDragging) {
                    isDragging = false
                    performDragRelease()
                }
                return true
            } else if (action == "SCROLL_MODIFIER") {
                isScrolling = false
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
            "NOTIFICATIONS" -> {
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                handled = true
            }
            "QUICK_SETTINGS" -> {
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
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
                if (!isDragging) {
                    isDragging = true
                    dragStartX = cursorOverlay?.x ?: 0f
                    dragStartY = cursorOverlay?.y ?: 0f
                }
                handled = true
            }
            "SCROLL_MODIFIER" -> {
                if (!isScrolling) {
                    isScrolling = true
                    scrollStartX = cursorOverlay?.x ?: 0f
                    scrollStartY = cursorOverlay?.y ?: 0f
                }
                handled = true
            }
        }

        return handled
    }

    private fun handleContinuousScroll(dx: Float, dy: Float) {
        val cx = cursorOverlay?.x ?: return
        val cy = cursorOverlay?.y ?: return

        // Only trigger scroll gesture if we've moved significantly from the start point
        if (Math.abs(cx - scrollStartX) > 100f || Math.abs(cy - scrollStartY) > 100f) {
            // Dispatch a quick swipe in the opposite direction of joystick push to scroll content
            val path = Path().apply {
                moveTo(scrollStartX, scrollStartY)
                lineTo(scrollStartX - (cx - scrollStartX), scrollStartY - (cy - scrollStartY))
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 150))
                .build()

            dispatchGesture(gesture, null, null)

            // Reset start position for the next scroll chunk
            scrollStartX = cx
            scrollStartY = cy
        }
    }

    private fun performDragRelease() {
        val cx = cursorOverlay?.x ?: return
        val cy = cursorOverlay?.y ?: return

        if (Math.abs(cx - dragStartX) < 10f && Math.abs(cy - dragStartY) < 10f) {
            regainFocus()
            return
        }

        val path = Path().apply {
            moveTo(dragStartX, dragStartY)
            lineTo(cx, cy)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, object: GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                regainFocus()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                regainFocus()
            }
        }, null)
    }

    private fun performCursorClick() {
        val cx = cursorOverlay?.x ?: return
        val cy = cursorOverlay?.y ?: return

        if (keyboardOverlay?.isShowing() == true) {
            if (keyboardOverlay?.handleClickAt(cx, cy) == true) {
                return
            }
        }

        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(cx + 0.1f, cy + 0.1f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, object: GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                regainFocus()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                regainFocus()
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
            .addStroke(GestureDescription.StrokeDescription(path, 0, longPressDuration))
            .build()

        dispatchGesture(gesture, object: GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                regainFocus()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                regainFocus()
            }
        }, null)
    }

    private fun handleKeyboardTyping(key: String) {
        // Try the node we explicitly tracked, or fallback to scanning the tree
        val activeNode = lastFocusedEditableNode ?: findFocusedEditableNodeFallback() ?: return

        val arguments = Bundle()
        val currentText = activeNode.text?.toString() ?: ""

        // Try to respect cursor selection/index if available, else append
        val selectionStart = activeNode.textSelectionStart.takeIf { it >= 0 } ?: currentText.length
        val selectionEnd = activeNode.textSelectionEnd.takeIf { it >= 0 } ?: currentText.length

        when (key) {
            "DEL" -> {
                if (currentText.isNotEmpty()) {
                    val newText = if (selectionStart == selectionEnd && selectionStart > 0) {
                        // Delete char before cursor
                        currentText.substring(0, selectionStart - 1) + currentText.substring(selectionEnd)
                    } else if (selectionStart != selectionEnd) {
                        // Delete selection
                        currentText.substring(0, selectionStart) + currentText.substring(selectionEnd)
                    } else {
                        // Fallback: delete last char
                        currentText.substring(0, currentText.length - 1)
                    }
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                    activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }
            "SPACE", "ENTER" -> {
                val insertStr = if (key == "SPACE") " " else "\n"

                if (key == "ENTER" && activeNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    // ACTION_CLICK often handles "Submit" for search/enter fields.
                    return
                }

                val newText = currentText.substring(0, selectionStart) + insertStr + currentText.substring(selectionEnd)
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            else -> {
                val newText = currentText.substring(0, selectionStart) + key + currentText.substring(selectionEnd)
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        }

        // Try to place the text caret at the end of the new insertion
        val newCaretPos = selectionStart + (if (key == "DEL" && selectionStart == selectionEnd) -1 else if (key == "DEL") 0 else 1)
        if (newCaretPos >= 0) {
            val selectionArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCaretPos)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCaretPos)
            }
            activeNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
        }
    }

    private fun findFocusedEditableNodeFallback(): AccessibilityNodeInfo? {
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
        if (event == null) return

        // Track the actively focused text field when the user clicks on it in the underlying app
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val source = event.source
            if (source?.isEditable == true) {
                lastFocusedEditableNode = source
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            regainFocus()
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
