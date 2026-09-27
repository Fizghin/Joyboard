package com.joyboard.notchisland

import com.joyboard.notchisland.island.SpeedMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpeedMeterTest {

    private val meter = SpeedMeter(thresholdBps = 1_000, startSamples = 2, stopSamples = 3)
    private var rx = 0L
    private var at = 0L

    /** Advances one second having received [bytes]. */
    private fun second(bytes: Long): com.joyboard.notchisland.island.SpeedReading? {
        rx += bytes
        at += 1_000
        return meter.feed(rx, 0, at)
    }

    @Test
    fun `the first sample only sets the baseline`() {
        assertNull(meter.feed(0, 0, 0))
    }

    @Test
    fun `rates come out in bytes per second`() {
        meter.feed(0, 0, 0)
        val reading = meter.feed(3_000, 500, 1_500)!!
        assertEquals(2_000, reading.downBps)
        assertEquals(333, reading.upBps)
    }

    @Test
    fun `a transfer shows after staying fast and goes after staying slow`() {
        meter.feed(0, 0, 0)
        assertFalse("one fast second is a blip", second(5_000)!!.active)
        assertTrue(second(5_000)!!.active)
        assertTrue("a middling second keeps it", second(700)!!.active)
        assertTrue(second(0)!!.active)
        assertTrue(second(0)!!.active)
        assertFalse(second(0)!!.active)
    }

    @Test
    fun `counters that start over are a new baseline, not a negative rate`() {
        meter.feed(10_000, 10_000, 0)
        assertNull(meter.feed(100, 100, 1_000))
        assertEquals(900, meter.feed(1_000, 100, 2_000)!!.downBps)
    }

    @Test
    fun `unavailable counters give nothing`() {
        assertNull(meter.feed(-1, -1, 0))
    }

    @Test
    fun `formatting picks the unit`() {
        assertEquals("512 B/s", SpeedMeter.format(512, Locale.US))
        assertEquals("300 KB/s", SpeedMeter.format(300 * 1024, Locale.US))
        assertEquals("2.5 MB/s", SpeedMeter.format((2.5 * 1024 * 1024).toLong(), Locale.US))
    }
}
