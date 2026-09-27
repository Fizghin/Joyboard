package com.joyboard.notchisland

import com.joyboard.notchisland.island.MediaQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaQueueTest {

    private val queue = listOf(10L to "Intro", 11L to "Midnight City", 12L to "Wait", 13L to "Raconte-moi")

    @Test
    fun `the item after the playing one is up next`() {
        assertEquals("Wait", MediaQueue.nextTitle(queue, activeId = 11L))
    }

    @Test
    fun `the last item has nothing after it`() {
        assertNull(MediaQueue.nextTitle(queue, activeId = 13L))
    }

    @Test
    fun `untitled entries are skipped rather than shown blank`() {
        val gappy = listOf(1L to "Now", 2L to null, 3L to " ", 4L to "Later")
        assertEquals("Later", MediaQueue.nextTitle(gappy, activeId = 1L))
    }

    @Test
    fun `no queue, or a playing item outside it, means no guess`() {
        assertNull(MediaQueue.nextTitle(null, activeId = 1L))
        assertNull(MediaQueue.nextTitle(emptyList(), activeId = 1L))
        assertNull(MediaQueue.nextTitle(queue, activeId = 99L))
    }
}
