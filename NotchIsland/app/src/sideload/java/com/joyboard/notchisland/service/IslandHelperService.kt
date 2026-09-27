package com.joyboard.notchisland.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.joyboard.notchisland.island.ShadeDetector

/**
 * An optional helper that gives the island two kinds of help.
 *
 * It says which app is in front, whether a keyboard is open and whether the notification shade
 * is down, so the island can stay out of chosen apps, out of the way while someone types, and
 * out of the shade.
 *
 * And it lends the island an accessibility overlay window, the one kind of window Android layers
 * above the status bar, so notification icons go under the island instead of being drawn across
 * it.
 *
 * It never reads what is on screen, no text and no views, and nothing is stored or sent. It only
 * exists in the sideloaded build: Google Play reserves accessibility services for tools that help
 * people with disabilities, which this is not.
 */
class IslandHelperService : AccessibilityService() {

    override fun onServiceConnected() {
        IslandBus.helperConnected = true
        // This service's WindowManager carries its token, which is what lets a window added
        // through it be an accessibility overlay.
        IslandBus.setHelperWindowManager(getSystemService(WindowManager::class.java))
        updateWindows()
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
                updateWindows()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> updateWindows()
            // Only the two event types above are subscribed to.
            else -> Unit
        }
    }

    private fun updateWindows() {
        val current = runCatching { windows }.getOrNull().orEmpty()
        IslandBus.postKeyboard(current.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD })
        IslandBus.postShade(shadeOpen(current))
    }

    private fun shadeOpen(windows: List<AccessibilityWindowInfo>): Boolean {
        val bounds = Rect()
        return ShadeDetector.isOpen(
            windows = windows.map {
                it.getBoundsInScreen(bounds)
                ShadeDetector.Window(
                    system = it.type == AccessibilityWindowInfo.TYPE_SYSTEM,
                    heightPx = bounds.height(),
                    focused = it.isFocused,
                )
            },
            screenHeightPx = resources.displayMetrics.heightPixels,
            locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
        )
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
