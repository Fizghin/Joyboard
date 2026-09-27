package com.joyboard.notchisland.island

/**
 * Tells the pulled-down notification shade apart from the other system windows a phone keeps on
 * screen. Getting this wrong in the "open" direction hides the island for no reason, so it asks
 * for all three: a system window, covering most of the screen, holding focus — the shade takes
 * focus when it is pulled down, while decoration and edge-panel windows never do.
 */
object ShadeDetector {

    data class Window(val system: Boolean, val heightPx: Int, val focused: Boolean)

    fun isOpen(windows: List<Window>, screenHeightPx: Int, locked: Boolean): Boolean {
        // The lock screen lives in the shade's window, so while locked this cannot tell.
        if (locked || screenHeightPx <= 0) return false
        return windows.any { it.system && it.focused && it.heightPx >= screenHeightPx * 0.8f }
    }
}
