package com.joyboard.notchisland

import com.joyboard.notchisland.island.CalendarEvent
import com.joyboard.notchisland.island.CalendarPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarPickerTest {

    private val min = 60_000L
    private val now = 1_000_000_000L
    private val lead = 15 * min

    private fun event(id: Long, startsIn: Long, lasts: Long = 30 * min, allDay: Boolean = false) =
        CalendarEvent(id, "Event $id", now + startsIn, now + startsIn + lasts, allDay = allDay)

    @Test
    fun `an event inside the lead window is picked`() {
        assertEquals(1L, CalendarPicker.pick(listOf(event(1, 10 * min)), now, lead)?.id)
    }

    @Test
    fun `an event further out than the lead is not picked yet`() {
        assertNull(CalendarPicker.pick(listOf(event(1, 20 * min)), now, lead))
    }

    @Test
    fun `the soonest of several wins`() {
        val picked = CalendarPicker.pick(listOf(event(1, 12 * min), event(2, 3 * min)), now, lead)
        assertEquals(2L, picked?.id)
    }

    @Test
    fun `an event stays up briefly after it starts, then goes`() {
        assertEquals(1L, CalendarPicker.pick(listOf(event(1, -4 * min)), now, lead)?.id)
        assertNull(CalendarPicker.pick(listOf(event(1, -6 * min)), now, lead))
    }

    @Test
    fun `a short event that has already ended is not shown`() {
        assertNull(CalendarPicker.pick(listOf(event(1, -3 * min, lasts = 2 * min)), now, lead))
    }

    @Test
    fun `all-day events never take the island`() {
        assertNull(CalendarPicker.pick(listOf(event(1, 5 * min, allDay = true)), now, lead))
    }

    @Test
    fun `minutes are rounded up and bottom out at zero`() {
        assertEquals(10, CalendarPicker.minutesUntil(event(1, 10 * min), now))
        assertEquals(10, CalendarPicker.minutesUntil(event(1, 9 * min + 1), now))
        assertEquals(1, CalendarPicker.minutesUntil(event(1, 1), now))
        assertEquals(0, CalendarPicker.minutesUntil(event(1, -1), now))
    }

    @Test
    fun `the refresh lands on the minute the label changes`() {
        // 9 min 30 s out reads "in 10 min" until 9 min out: 30 s from now.
        assertEquals(30_000L, CalendarPicker.millisUntilNextChange(event(1, 9 * min + 30_000), now))
        // Once started, the next change is the event leaving the island.
        assertEquals(3 * min, CalendarPicker.millisUntilNextChange(event(1, -2 * min), now))
    }
}
