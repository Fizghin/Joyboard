package com.joyboard.notchisland

import com.joyboard.notchisland.island.ShadeDetector
import com.joyboard.notchisland.island.ShadeDetector.Window
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeDetectorTest {

    private val screen = 2400
    private val statusBar = Window(system = true, heightPx = 110, focused = false)
    private val navBar = Window(system = true, heightPx = 60, focused = false)

    @Test
    fun `the pulled-down shade is tall, a system window, and focused`() {
        val shade = Window(system = true, heightPx = 2400, focused = true)
        assertTrue(ShadeDetector.isOpen(listOf(statusBar, shade, navBar), screen, locked = false))
    }

    @Test
    fun `a tall system window that never takes focus is not the shade`() {
        // Screen decorations, edge panels and other apps' overlays sit there unfocused.
        val decoration = Window(system = true, heightPx = 2400, focused = false)
        assertFalse(ShadeDetector.isOpen(listOf(statusBar, decoration, navBar), screen, locked = false))
    }

    @Test
    fun `a focused app, or a short system window, is not the shade`() {
        assertFalse(ShadeDetector.isOpen(listOf(Window(system = false, heightPx = 2400, focused = true)), screen, false))
        assertFalse(ShadeDetector.isOpen(listOf(Window(system = true, heightPx = 900, focused = true)), screen, false))
    }

    @Test
    fun `while locked it cannot tell, so it says closed`() {
        val lockScreen = Window(system = true, heightPx = 2400, focused = true)
        assertFalse(ShadeDetector.isOpen(listOf(lockScreen), screen, locked = true))
    }
}
