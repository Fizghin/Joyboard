package com.joyboard.notchisland.island

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.joyboard.notchisland.util.Http
import java.util.concurrent.Executors

/**
 * Fetches the forecast for the saved area every half hour while it runs, off the main thread.
 * Only the rounded area and the temperature unit are sent. A failed fetch keeps the last
 * report and tries again sooner.
 */
class WeatherMonitor(
    private val onReport: (WeatherReport) -> Unit,
    private val fetch: (String) -> Pair<Int, String?> = Http::get,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var area: String? = null
    private var started = false
    private var lastSuccessAt = 0L
    private val tick = Runnable { refresh() }

    fun start(area: String) {
        val changed = area != this.area
        this.area = area
        started = true
        handler.removeCallbacks(tick)
        val age = SystemClock.elapsedRealtime() - lastSuccessAt
        if (changed || lastSuccessAt == 0L || age >= INTERVAL_MS) refresh()
        else handler.postDelayed(tick, INTERVAL_MS - age)
    }

    fun stop() {
        started = false
        handler.removeCallbacks(tick)
    }

    fun release() {
        stop()
        executor.shutdownNow()
    }

    private fun refresh() {
        if (!started) return
        val requested = area ?: return
        val fahrenheit = Weather.usesFahrenheit()
        val url = Weather.url(requested, fahrenheit) ?: return
        executor.execute {
            val report = runCatching {
                val (code, body) = fetch(url)
                if (code == 200 && body != null) Weather.parse(body, fahrenheit) else null
            }.getOrNull()
            handler.post {
                if (!started || requested != area) return@post
                if (report != null) {
                    lastSuccessAt = SystemClock.elapsedRealtime()
                    onReport(report)
                }
                handler.postDelayed(tick, if (report != null) INTERVAL_MS else RETRY_MS)
            }
        }
    }

    private companion object {
        const val INTERVAL_MS = 30 * 60_000L
        const val RETRY_MS = 5 * 60_000L
    }
}
