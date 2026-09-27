package com.joyboard.notchisland.robo

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.island.Sky
import com.joyboard.notchisland.island.WeatherMonitor
import com.joyboard.notchisland.island.WeatherReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.Collections

/** The monitor with the network swapped out: what it asks for and what it hands back. */
@RunWith(AndroidJUnit4::class)
class WeatherMonitorTest {

    private val forecast = """{"current":{"time":1790000000,"temperature_2m":21.4,"weather_code":0,"precipitation":0},
        "minutely_15":{"time":[1790000000],"precipitation":[0]}}"""

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `the saved area is fetched and the report delivered on the main thread`() {
        val asked = Collections.synchronizedList(mutableListOf<String>())
        var report: WeatherReport? = null
        var onMain = false
        val monitor = WeatherMonitor(
            onReport = { report = it; onMain = Looper.myLooper() == Looper.getMainLooper() },
            fetch = { url -> asked += url; 200 to forecast },
        )
        monitor.start("52.5,13.4")
        waitFor { report != null }
        monitor.release()

        assertEquals("21°", report?.degrees)
        assertEquals(Sky.CLEAR, report?.sky)
        assertTrue(onMain)
        assertEquals(1, asked.size)
        assertTrue(asked.single().contains("latitude=52.5&longitude=13.4"))
    }

    @Test
    fun `a failed fetch delivers nothing and does not crash`() {
        var report: WeatherReport? = null
        var calls = 0
        val monitor = WeatherMonitor(
            onReport = { report = it },
            fetch = { calls++; throw java.io.IOException("offline") },
        )
        monitor.start("52.5,13.4")
        waitFor { calls > 0 }
        shadowOf(Looper.getMainLooper()).idle()
        monitor.release()
        assertNull(report)
    }

    @Test
    fun `an unusable area is never sent anywhere`() {
        var calls = 0
        val monitor = WeatherMonitor(onReport = {}, fetch = { calls++; 200 to forecast })
        monitor.start("")
        Thread.sleep(100)
        shadowOf(Looper.getMainLooper()).idle()
        monitor.release()
        assertEquals(0, calls)
    }
}
