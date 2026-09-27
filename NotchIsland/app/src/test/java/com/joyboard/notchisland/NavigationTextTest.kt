package com.joyboard.notchisland

import com.joyboard.notchisland.island.NavigationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTextTest {

    @Test
    fun `distance in the title, the turn in the text, the arrival in the sub-text`() {
        val info = NavigationText.parse("250 m", "Turn left onto High St", "8 min · 2.1 km · 10:42 ETA")
        assertEquals("250 m", info.distance)
        assertEquals("Turn left onto High St", info.instruction)
        assertEquals("8 min · 2.1 km · 10:42 ETA", info.eta)
    }

    @Test
    fun `the turn in the title and the distance after it`() {
        val info = NavigationText.parse("Turn right onto Oak Ave", "in 0.3 mi", "")
        assertEquals("0.3 mi", info.distance)
        assertEquals("Turn right onto Oak Ave", info.instruction)
        assertNull("a bare distance is not an arrival time", info.eta)
    }

    @Test
    fun `a decimal comma and a distance inside the instruction`() {
        val info = NavigationText.parse("In 1,5 km take exit 12", "toward Airport", "")
        assertEquals("1,5 km", info.distance)
        assertEquals("In 1,5 km take exit 12", info.instruction)
        assertEquals("toward Airport", info.eta)
    }

    @Test
    fun `minutes are not metres`() {
        val info = NavigationText.parse("Head north", "12 min to destination", "")
        assertNull(info.distance)
        assertEquals("Head north", info.instruction)
    }

    @Test
    fun `no title leaves the text as the turn`() {
        val info = NavigationText.parse("", "Continue straight", "")
        assertEquals("Continue straight", info.instruction)
        assertNull(info.distance)
    }

    @Test
    fun `navigation by category, or by a maps app while ongoing`() {
        assertTrue(NavigationText.isNavigation("navigation", "com.example", ongoing = false))
        assertTrue(NavigationText.isNavigation(null, "com.waze", ongoing = true))
        assertFalse("a maps app's passing notification is not a route", NavigationText.isNavigation(null, "com.waze", ongoing = false))
        assertFalse(NavigationText.isNavigation("transport", "com.example", ongoing = true))
    }
}
