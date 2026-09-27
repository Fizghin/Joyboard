package com.joyboard.notchisland

import com.joyboard.notchisland.island.Lrc
import com.joyboard.notchisland.island.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcTest {

    private val song = """
        [ar:M83]
        [ti:Midnight City]
        [00:01.50] Waiting in a car
        [00:05.20]Waiting for a ride in the dark
        [00:10.00]
        [01:02.345] The city is my church
    """.trimIndent()

    @Test
    fun `timed lines are read in order and tags are skipped`() {
        val lines = Lrc.parse(song)
        assertEquals(
            listOf(
                LyricLine(1_500, "Waiting in a car"),
                LyricLine(5_200, "Waiting for a ride in the dark"),
                LyricLine(10_000, ""),
                LyricLine(62_345, "The city is my church"),
            ),
            lines,
        )
    }

    @Test
    fun `a line with several stamps appears at each`() {
        val lines = Lrc.parse("[00:30.00][01:30.00]Chorus\n[01:00.00]Verse")
        assertEquals(listOf(30_000L, 60_000L, 90_000L), lines.map { it.timeMs })
        assertEquals("Chorus", lines.last().text)
    }

    @Test
    fun `an offset tag shifts every line and never below zero`() {
        val lines = Lrc.parse("[offset:+500]\n[00:00.20]First\n[00:02.00]Second")
        assertEquals(listOf(0L, 1_500L), lines.map { it.timeMs })
    }

    @Test
    fun `the current line is the last one that has started`() {
        val lines = Lrc.parse(song)
        assertEquals(-1, Lrc.indexAt(lines, 1_000))
        assertEquals(0, Lrc.indexAt(lines, 1_500))
        assertEquals(1, Lrc.indexAt(lines, 9_999))
        assertEquals(3, Lrc.indexAt(lines, 600_000))
    }

    @Test
    fun `plain text and junk produce no lines rather than errors`() {
        assertTrue(Lrc.parse("just words\nno stamps").isEmpty())
        assertTrue(Lrc.parse("").isEmpty())
        assertEquals(-1, Lrc.indexAt(emptyList(), 5_000))
    }
}
