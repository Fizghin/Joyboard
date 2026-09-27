package com.joyboard.notchisland

import com.joyboard.notchisland.island.VisibilityPolicy
import com.joyboard.notchisland.island.VisibilityPolicy.Inputs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityPolicyTest {

    private fun hides(inputs: Inputs) = VisibilityPolicy.shouldHide(inputs)

    @Test
    fun `nothing going on means the island shows`() {
        assertFalse(hides(Inputs()))
    }

    @Test
    fun `full screen hides only when asked to`() {
        assertTrue(hides(Inputs(fullscreen = true, hideInFullscreen = true)))
        assertFalse(hides(Inputs(fullscreen = true, hideInFullscreen = false)))
    }

    @Test
    fun `the keyboard hides it unless the island raised the keyboard itself`() {
        assertTrue(hides(Inputs(keyboardVisible = true, hideWhileTyping = true)))
        assertFalse(hides(Inputs(keyboardVisible = true, hideWhileTyping = true, replying = true)))
        assertFalse(hides(Inputs(keyboardVisible = true, hideWhileTyping = false)))
    }

    @Test
    fun `an app on the hide list hides it, any other app does not`() {
        val list = setOf("com.game")
        assertTrue(hides(Inputs(foregroundPackage = "com.game", hiddenInPackages = list)))
        assertFalse(hides(Inputs(foregroundPackage = "com.chat", hiddenInPackages = list)))
        assertFalse(hides(Inputs(foregroundPackage = null, hiddenInPackages = list)))
    }

    @Test
    fun `a call breaks through the staying-out-of-the-way reasons`() {
        assertFalse(
            hides(
                Inputs(
                    fullscreen = true, hideInFullscreen = true,
                    foregroundPackage = "com.game", hiddenInPackages = setOf("com.game"),
                    keyboardVisible = true, hideWhileTyping = true,
                    urgent = true,
                )
            )
        )
    }

    @Test
    fun `a call does not override what the person set in so many words`() {
        assertTrue(hides(Inputs(screenOn = false, urgent = true)))
        assertTrue(hides(Inputs(quiet = true, urgent = true)))
        assertTrue(hides(Inputs(lockedOut = true, urgent = true)))
        assertTrue(hides(Inputs(temporarilyHidden = true, urgent = true)))
        assertTrue(hides(Inputs(landscape = true, hideInLandscape = true, urgent = true)))
    }
}
