package com.joyboard.notchisland

import com.joyboard.notchisland.util.formatDuration
import com.joyboard.notchisland.util.formatRelative
import com.joyboard.notchisland.util.formatStopwatch
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `seconds only`() {
        assertEquals("0:07", formatDuration(7_000))
    }

    @Test
    fun `minutes and seconds`() {
        assertEquals("03:20", formatDuration(200_000))
    }

    @Test
    fun `hours appear once a track is long enough`() {
        assertEquals("1:01:05", formatDuration(3_665_000))
    }

    @Test
    fun `timers pad to minutes even below a minute`() {
        assertEquals("00:09", formatDuration(9_000, forceMinutes = true))
    }

    @Test
    fun `negative positions clamp to zero`() {
        assertEquals("0:00", formatDuration(-5_000))
    }

    @Test
    fun `stopwatch keeps hundredths`() {
        assertEquals("01:05.30", formatStopwatch(65_300))
    }

    @Test
    fun `relative stamps`() {
        val now = 10_000_000L
        assertEquals("just now", formatRelative(now, now - 5_000))
        assertEquals("4m ago", formatRelative(now, now - 4 * 60_000))
        assertEquals("3h ago", formatRelative(now, now - 3 * 3_600_000L))
        assertEquals("2d ago", formatRelative(now, now - 2 * 24 * 3_600_000L))
    }
}
