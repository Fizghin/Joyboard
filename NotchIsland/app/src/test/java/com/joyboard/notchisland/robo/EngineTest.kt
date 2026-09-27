package com.joyboard.notchisland.robo

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.island.StopwatchEngine
import com.joyboard.notchisland.island.TimerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** The timer and stopwatch, driven by Robolectric's clock rather than real time. */
@RunWith(AndroidJUnit4::class)
class EngineTest {

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun `a timer counts down and finishes once`() {
        var finished = 0
        var lastRemaining = -1L
        val timer = TimerEngine(
            onTick = { remaining, _, _ -> lastRemaining = remaining },
            onFinished = { finished++ },
        )
        timer.start(5_000)
        advance(2_000)
        assertTrue("expected about 3s left, was $lastRemaining", lastRemaining in 2_700..3_100)
        advance(3_500)
        assertEquals(1, finished)
        assertFalse(timer.active)
        advance(5_000)
        assertEquals("must not finish twice", 1, finished)
    }

    @Test
    fun `a paused timer does not lose time`() {
        val timer = TimerEngine(onTick = { _, _, _ -> }, onFinished = {})
        timer.start(10_000)
        advance(2_000)
        timer.pause()
        advance(60_000)
        assertEquals(8_000.0, timer.remaining().toDouble(), 250.0)
        timer.resume()
        advance(1_000)
        assertEquals(7_000.0, timer.remaining().toDouble(), 250.0)
    }

    @Test
    fun `adding a minute extends both the total and what is left`() {
        val timer = TimerEngine(onTick = { _, _, _ -> }, onFinished = {})
        timer.start(30_000)
        timer.addMinute()
        assertEquals(90_000L, timer.totalMs)
        assertEquals(90_000.0, timer.remaining().toDouble(), 250.0)
    }

    @Test
    fun `the stopwatch runs, pauses and records laps`() {
        val stopwatch = StopwatchEngine { _, _ -> }
        stopwatch.start()
        advance(1_500)
        stopwatch.lap()
        advance(1_000)
        stopwatch.pause()
        val atPause = stopwatch.elapsed()
        advance(10_000)
        assertEquals("paused time must not count", atPause, stopwatch.elapsed())
        assertEquals(2_500.0, atPause.toDouble(), 150.0)
        assertEquals(1, stopwatch.laps.size)
        assertEquals(1_500.0, stopwatch.laps.first().toDouble(), 150.0)
    }

    @Test
    fun `reset clears everything`() {
        val stopwatch = StopwatchEngine { _, _ -> }
        stopwatch.start()
        advance(800)
        stopwatch.lap()
        stopwatch.reset()
        assertEquals(0L, stopwatch.elapsed())
        assertTrue(stopwatch.laps.isEmpty())
        assertFalse(stopwatch.active)
    }
}
