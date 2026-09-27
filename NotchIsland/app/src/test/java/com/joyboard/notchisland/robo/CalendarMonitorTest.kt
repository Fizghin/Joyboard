package com.joyboard.notchisland.robo

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.island.CalendarEvent
import com.joyboard.notchisland.island.CalendarMonitor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** The monitor against a stand-in calendar provider: what it reads, and what it leaves out. */
@RunWith(AndroidJUnit4::class)
class CalendarMonitorTest {

    class FakeCalendar : ContentProvider() {
        var rows: List<Array<Any?>> = emptyList()
        override fun onCreate() = true
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor = MatrixCursor(projection).apply { rows.forEach { addRow(it) } }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val min = 60_000L
    private var monitor: CalendarMonitor? = null

    @After
    fun tearDown() {
        monitor?.release()
    }

    private fun row(
        id: Long, title: String, startsIn: Long,
        status: Int = CalendarContract.Events.STATUS_CONFIRMED,
        attendee: Int = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
        allDay: Int = 0,
    ): Array<Any?> {
        val now = System.currentTimeMillis()
        return arrayOf(id, title, now + startsIn, now + startsIn + 30 * min, "Room 4", allDay, status, attendee)
    }

    /** Starts the monitor and waits for its background read to land on the main thread. */
    private fun firstReport(rows: List<Array<Any?>>): CalendarEvent? {
        val provider = Robolectric.buildContentProvider(FakeCalendar::class.java)
            .create(CalendarContract.AUTHORITY).get()
        provider.rows = rows
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
        var reported = false
        var event: CalendarEvent? = null
        monitor = CalendarMonitor(app) { reported = true; event = it }.also { it.start(15) }
        val deadline = System.currentTimeMillis() + 5_000
        while (!reported && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
        return event
    }

    @Test
    fun `the next timed event is read, with its place`() {
        val event = firstReport(listOf(row(1, "Standup", 10 * min)))
        assertEquals(1L, event?.id)
        assertEquals("Standup", event?.title)
        assertEquals("Room 4", event?.location)
    }

    @Test
    fun `cancelled, declined and all-day events are passed over`() {
        val event = firstReport(
            listOf(
                row(2, "Cancelled", 2 * min, status = CalendarContract.Events.STATUS_CANCELED),
                row(3, "Declined", 3 * min, attendee = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED),
                row(4, "Holiday", 4 * min, allDay = 1),
                row(5, "Review", 12 * min),
            )
        )
        assertEquals(5L, event?.id)
    }

    @Test
    fun `nothing within the lead time means nothing to show`() {
        assertNull(firstReport(listOf(row(6, "Later", 90 * min))))
    }
}
