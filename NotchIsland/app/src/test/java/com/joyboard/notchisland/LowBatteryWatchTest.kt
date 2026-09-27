package com.joyboard.notchisland

import com.joyboard.notchisland.island.LowBatteryWatch
import org.junit.Assert.assertEquals
import org.junit.Test

class LowBatteryWatchTest {

    private fun run(vararg readings: Pair<Int, Boolean>): List<Boolean> {
        val watch = LowBatteryWatch()
        return readings.map { (level, plugged) -> watch.update(level, plugged) }
    }

    @Test
    fun `warns at 15, 10 and 5, not on every reading between`() {
        val warned = run(16 to false, 15 to false, 15 to false, 14 to false, 10 to false, 9 to false, 5 to false, 4 to false)
        assertEquals(listOf(false, true, false, false, true, false, true, false), warned)
    }

    @Test
    fun `a sudden drop warns once, at the step it lands on`() {
        assertEquals(listOf(false, true, false), run(20 to false, 8 to false, 7 to false))
    }

    @Test
    fun `plugging in or climbing back starts over`() {
        assertEquals(
            listOf(true, false, true, false, true),
            run(12 to false, 12 to true, 12 to false, 16 to false, 15 to false),
        )
    }
}
