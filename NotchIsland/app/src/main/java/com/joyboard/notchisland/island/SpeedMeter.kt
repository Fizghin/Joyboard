package com.joyboard.notchisland.island

import java.util.Locale

/** Transfer rates from one sample to the next, and whether they count as a fast transfer. */
data class SpeedReading(val downBps: Long, val upBps: Long, val active: Boolean)

/**
 * Turns the phone's running byte counters into rates. A transfer has to stay fast for a couple of
 * samples before it is shown and slow for a few before it goes, so a burst of page loading does
 * not flash the island and a download that stutters does not flicker it. Pure, so tested.
 */
class SpeedMeter(
    private val thresholdBps: Long = THRESHOLD_BPS,
    private val startSamples: Int = 2,
    private val stopSamples: Int = 3,
) {
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = -1L
    private var fastRun = 0
    private var slowRun = 0
    private var active = false

    fun reset() {
        lastRx = -1L
        lastTx = -1L
        lastAt = -1L
        fastRun = 0
        slowRun = 0
        active = false
    }

    /**
     * Feeds the counters as read at [atMs]. Null for the first sample, which has nothing to
     * compare with, and whenever the counters are unavailable or start over.
     */
    fun feed(rxBytes: Long, txBytes: Long, atMs: Long): SpeedReading? {
        if (rxBytes < 0 || txBytes < 0) return null
        val restart = lastAt < 0 || atMs <= lastAt || rxBytes < lastRx || txBytes < lastTx
        val elapsed = atMs - lastAt
        val down = if (restart) 0L else (rxBytes - lastRx) * 1000 / elapsed
        val up = if (restart) 0L else (txBytes - lastTx) * 1000 / elapsed
        lastRx = rxBytes
        lastTx = txBytes
        lastAt = atMs
        if (restart) return null

        val peak = maxOf(down, up)
        when {
            peak >= thresholdBps -> { fastRun++; slowRun = 0 }
            peak < thresholdBps / 2 -> { slowRun++; fastRun = 0 }
            // In between keeps whatever it was doing.
            else -> { fastRun = 0; slowRun = 0 }
        }
        if (!active && fastRun >= startSamples) active = true
        if (active && slowRun >= stopSamples) active = false
        return SpeedReading(down, up, active)
    }

    companion object {
        /** A quarter of a megabyte a second: a real download, not a web page. */
        const val THRESHOLD_BPS = 256L * 1024

        fun format(bytesPerSecond: Long, locale: Locale = Locale.getDefault()): String = when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format(locale, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }
}
