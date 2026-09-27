package com.joyboard.notchisland.island

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Watches the calendar for the next timed event and reports the one that has earned the island,
 * or null. Reads happen off the main thread; the countdown is re-checked on the minute it
 * changes rather than on a fixed poll, and the calendar is re-read when it changes or hourly.
 */
class CalendarMonitor(
    private val context: Context,
    private val onUpdate: (CalendarEvent?) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var started = false
    private var leadMs = 15 * 60_000L
    private var events: List<CalendarEvent> = emptyList()
    private var shown: CalendarEvent? = null

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = reload()
    }
    private val tick = Runnable { publish() }
    private val hourly = Runnable { reload() }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    fun start(leadMinutes: Int) {
        leadMs = leadMinutes.coerceIn(1, 120) * 60_000L
        if (started) {
            publish()
            return
        }
        if (!hasPermission()) return
        started = true
        runCatching {
            context.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI, true, observer
            )
        }
        reload()
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(hourly)
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        events = emptyList()
        if (shown != null) {
            shown = null
            onUpdate(null)
        }
    }

    /** Stops watching for good and lets the reader thread go. */
    fun release() {
        stop()
        executor.shutdown()
    }

    private fun reload() {
        if (!started) return
        handler.removeCallbacks(hourly)
        val now = System.currentTimeMillis()
        // Anything that could be on the island within the next hour and a bit.
        val from = now - CalendarPicker.GRACE_MS
        val to = now + maxOf(leadMs, HOUR) + HOUR
        executor.execute {
            val loaded = runCatching { query(from, to) }.getOrDefault(emptyList())
            handler.post {
                if (!started) return@post
                events = loaded
                publish()
                handler.postDelayed(hourly, HOUR)
            }
        }
    }

    private fun publish() {
        handler.removeCallbacks(tick)
        if (!started) return
        val now = System.currentTimeMillis()
        val event = CalendarPicker.pick(events, now, leadMs)
        shown = event
        onUpdate(event)
        val next = if (event != null) {
            CalendarPicker.millisUntilNextChange(event, now)
        } else {
            // The next event to come into its window, if any is loaded.
            events.filter { !it.allDay && it.beginMs - leadMs > now }
                .minOfOrNull { it.beginMs - leadMs - now }
        }
        next?.let { handler.postDelayed(tick, it.coerceAtLeast(1_000L)) }
    }

    private fun query(from: Long, to: Long): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        val result = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(6) == CalendarContract.Events.STATUS_CANCELED) continue
                if (c.getInt(7) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                result += CalendarEvent(
                    id = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    beginMs = c.getLong(2),
                    endMs = c.getLong(3),
                    location = c.getString(4)?.takeIf { it.isNotBlank() },
                    allDay = c.getInt(5) != 0,
                )
            }
        }
        return result
    }

    private companion object {
        const val HOUR = 60 * 60_000L
    }
}
