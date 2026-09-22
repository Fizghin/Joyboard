package com.joyboard.notchisland.island

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** A tiny countdown timer the island can host as a live activity. */
class TimerEngine(
    private val onTick: (remainingMs: Long, totalMs: Long, running: Boolean) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    var totalMs: Long = 0L
        private set
    private var endAt: Long = 0L
    private var pausedRemaining: Long = 0L
    var running: Boolean = false
        private set
    var active: Boolean = false
        private set

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val remaining = remaining()
            if (remaining <= 0L) {
                running = false
                active = false
                onTick(0, totalMs, false)
                onFinished()
                return
            }
            onTick(remaining, totalMs, true)
            handler.postDelayed(this, 200)
        }
    }

    fun start(durationMs: Long) {
        totalMs = durationMs
        endAt = SystemClock.elapsedRealtime() + durationMs
        running = true
        active = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    fun pause() {
        if (!running) return
        pausedRemaining = remaining()
        running = false
        handler.removeCallbacks(ticker)
        onTick(pausedRemaining, totalMs, false)
    }

    fun resume() {
        if (running || !active) return
        endAt = SystemClock.elapsedRealtime() + pausedRemaining
        running = true
        handler.post(ticker)
    }

    fun addMinute() {
        if (!active) return
        totalMs += 60_000
        if (running) endAt += 60_000 else pausedRemaining += 60_000
        onTick(remaining(), totalMs, running)
    }

    fun cancel() {
        running = false
        active = false
        handler.removeCallbacks(ticker)
        onTick(0, totalMs, false)
    }

    fun remaining(): Long =
        if (running) (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L) else pausedRemaining
}
