package com.joyboard.notchisland

import com.joyboard.notchisland.island.HeatWatch
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatWatchTest {

    @Test
    fun `warns once crossing into hot, and again only after cooling off`() {
        val watch = HeatWatch(hotC = 45f, coolC = 42f)
        val readings = listOf(40f, 44.9f, 45f, 46f, 44f, 45.5f, 41.5f, 45f, null)
        val warned = readings.map { watch.update(it) }
        assertEquals(listOf(false, false, true, false, false, false, false, true, false), warned)
    }
}
