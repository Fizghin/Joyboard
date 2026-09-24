package com.joyboard.notchisland.island

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** A stopwatch the island can host, with laps. */
class StopwatchEngine(
    private val onTick: (elapsedMs: Long, running: Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var accumulated = 0L
    val laps = mutableListOf<Long>()

    var running: Boolean = false
        private set
    var active: Boolean = false
        private set

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            onTick(elapsed(), true)
            handler.postDelayed(this, 60)
        }
    }

    fun toggle() {
        if (running) pause() else start()
    }

    fun start() {
        if (running) return
        startedAt = SystemClock.elapsedRealtime()
        running = true
        active = true
        handler.post(ticker)
    }

    fun pause() {
        if (!running) return
        accumulated = elapsed()
        running = false
        handler.removeCallbacks(ticker)
        onTick(accumulated, false)
    }

    fun lap() {
        if (!active) return
        laps.add(elapsed())
        onTick(elapsed(), running)
    }

    fun reset() {
        running = false
        active = false
        accumulated = 0
        laps.clear()
        handler.removeCallbacks(ticker)
        onTick(0, false)
    }

    fun elapsed(): Long =
        if (running) accumulated + (SystemClock.elapsedRealtime() - startedAt) else accumulated
}
