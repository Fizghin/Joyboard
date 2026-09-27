package com.joyboard.notchisland

import com.joyboard.notchisland.island.Sky
import com.joyboard.notchisland.island.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WeatherTest {

    private val now = 1_790_000_000_000L
    private val q = 15 * 60_000L

    private fun forecast(nowRain: Double, vararg slots: Double): String {
        val start = now / 1000
        val times = slots.indices.joinToString(",") { (start + it * 900).toString() }
        return """{"current":{"time":$start,"temperature_2m":14.6,"weather_code":61,"precipitation":$nowRain},
            "minutely_15":{"time":[$times],"precipitation":[${slots.joinToString(",")}]}}"""
    }

    @Test
    fun `a forecast is read into temperature, sky and slots`() {
        val report = Weather.parse(forecast(0.0, 0.0, 0.2), fahrenheit = false)
        assertEquals("15°", report.degrees)
        assertEquals(Sky.RAIN, report.sky)
        assertEquals(listOf(now to 0f, now + q to 0.2f), report.upcoming)
    }

    @Test
    fun `rain starting within the hour is flagged with its start`() {
        val report = Weather.parse(forecast(0.0, 0.0, 0.0, 0.4), fahrenheit = false)
        assertEquals(now + 2 * q, Weather.rainStart(report, now))
    }

    @Test
    fun `rain already falling, a trace, or rain after the hour is not flagged`() {
        assertNull(Weather.rainStart(Weather.parse(forecast(0.5, 0.5, 0.5), false), now))
        assertNull(Weather.rainStart(Weather.parse(forecast(0.0, 0.0, 0.05), false), now))
        assertNull(Weather.rainStart(Weather.parse(forecast(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.3), false), now))
    }

    @Test
    fun `codes group into what the island draws`() {
        assertEquals(Sky.CLEAR, Sky.of(0))
        assertEquals(Sky.FOG, Sky.of(48))
        assertEquals(Sky.DRIZZLE, Sky.of(53))
        assertEquals(Sky.RAIN, Sky.of(81))
        assertEquals(Sky.SNOW, Sky.of(86))
        assertEquals(Sky.STORM, Sky.of(96))
        assertEquals(Sky.CLOUDY, Sky.of(1234))
    }

    @Test
    fun `the area is rounded to about 10 km and read back`() {
        val area = Weather.roundedArea(52.52437, 13.41053)
        assertEquals("52.5,13.4", area)
        assertEquals(52.5 to 13.4, Weather.parseArea(area))
        assertNull(Weather.parseArea(""))
        assertNull(Weather.parseArea("95.0,10.0"))
    }

    @Test
    fun `the request asks for only what is shown, in the right unit`() {
        val url = Weather.url("52.5,13.4", fahrenheit = true)!!
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=52.5&longitude=13.4"))
        assertTrue(url.contains("temperature_unit=fahrenheit"))
        assertFalse(Weather.url("52.5,13.4", fahrenheit = false)!!.contains("fahrenheit"))
        assertNull(Weather.url("nowhere", false))
        assertTrue(Weather.usesFahrenheit(Locale.US))
        assertFalse(Weather.usesFahrenheit(Locale.GERMANY))
    }
}
