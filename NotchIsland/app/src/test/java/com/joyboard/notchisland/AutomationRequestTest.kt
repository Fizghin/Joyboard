package com.joyboard.notchisland

import com.joyboard.notchisland.island.AutomationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRequestTest {

    private fun parse(vararg pairs: Pair<String, Any?>) = AutomationRequest.parse(mapOf(*pairs)::get)

    @Test
    fun `a title is required`() {
        assertNull(parse())
        assertNull(parse("title" to "   "))
        assertNull(parse("title" to 42))
    }

    @Test
    fun `defaults fill in what was not given`() {
        val r = parse("title" to "Laundry done")!!
        assertEquals("Laundry done", r.title)
        assertNull(r.text)
        assertEquals(5_000L, r.durationMs)
        assertNull(r.color)
        assertFalse(r.expand)
    }

    @Test
    fun `lengths and durations are kept within bounds`() {
        val r = parse("title" to "x".repeat(500), "text" to "y".repeat(1000), "duration" to 99_999)!!
        assertEquals(AutomationRequest.MAX_TITLE, r.title.length)
        assertEquals(AutomationRequest.MAX_TEXT, r.text!!.length)
        assertEquals(600_000L, r.durationMs)
        assertEquals(1_000L, parse("title" to "t", "duration" to -3)!!.durationMs)
    }

    @Test
    fun `numbers and flags may arrive as text, as Tasker sends them`() {
        val r = parse("title" to "t", "duration" to "12", "expand" to "TRUE")!!
        assertEquals(12_000L, r.durationMs)
        assertTrue(r.expand)
        assertEquals(5_000L, parse("title" to "t", "duration" to "soon")!!.durationMs)
    }

    @Test
    fun `colours come as ints or hex, always opaque`() {
        assertEquals(0xFFFF3B30.toInt(), AutomationRequest.parseColor("#FF3B30"))
        assertEquals(0xFF123456.toInt(), AutomationRequest.parseColor("80123456"))
        assertEquals(0xFF00FF00.toInt(), AutomationRequest.parseColor(0x0000FF00))
        assertNull(AutomationRequest.parseColor("red"))
        assertNull(AutomationRequest.parseColor("#12345"))
        assertNull(AutomationRequest.parseColor(null))
    }

    @Test
    fun `only named icons are accepted`() {
        val known = parse("title" to "t", "icon" to "Calendar")!!
        assertEquals(AutomationRequest.ICONS.getValue("calendar"), known.iconRes)
        val unknown = parse("title" to "t", "icon" to "../../secret")!!
        assertEquals(AutomationRequest.ICONS.getValue("bell"), unknown.iconRes)
    }
}
