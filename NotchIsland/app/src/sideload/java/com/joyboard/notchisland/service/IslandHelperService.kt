package com.joyboard.notchisland.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * An optional helper that tells the island two things and nothing else: which app is in front,
 * and whether a keyboard is open. With those it can stay out of chosen apps and out of the way
 * while someone types.
 *
 * It never reads what is on screen — no text, no views, nothing is stored or sent anywhere. It
 * only exists in the sideloaded build: Google Play reserves accessibility services for tools
 * that help people with disabilities, which this is not.
 */
class IslandHelperService : AccessibilityService() {

    override fun onServiceConnected() {
        IslandBus.helperConnected = true
        updateKeyboard()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                val cls = event.className?.toString() ?: return
                // Dialogs, toasts and the keyboard also raise this event; only a real activity
                // coming forward means a different app is in front.
                if (isActivity(pkg, cls)) IslandBus.postForegroundApp(pkg)
                updateKeyboard()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> updateKeyboard()
            // Only the two event types above are subscribed to.
            else -> Unit
        }
    }

    private fun updateKeyboard() {
        val open = runCatching {
            windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }.getOrDefault(false)
        IslandBus.postKeyboard(open)
    }

    private fun isActivity(pkg: String, cls: String): Boolean = runCatching {
        packageManager.getActivityInfo(ComponentName(pkg, cls), PackageManager.GET_META_DATA)
        true
    }.getOrDefault(false)

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        IslandBus.clearHelperState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        IslandBus.clearHelperState()
        super.onDestroy()
    }
}
