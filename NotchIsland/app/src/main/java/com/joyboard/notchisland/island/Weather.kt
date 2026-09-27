package com.joyboard.notchisland.island

import com.joyboard.notchisland.R
import com.joyboard.notchisland.util.Http
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/** What the sky is doing, grouped the way the island draws it. */
enum class Sky(val labelRes: Int, val iconRes: Int) {
    CLEAR(R.string.weather_clear, R.drawable.ic_weather_clear),
    PARTLY_CLOUDY(R.string.weather_partly_cloudy, R.drawable.ic_weather_cloud),
    CLOUDY(R.string.weather_cloudy, R.drawable.ic_weather_cloud),
    FOG(R.string.weather_fog, R.drawable.ic_weather_cloud),
    DRIZZLE(R.string.weather_drizzle, R.drawable.ic_weather_rain),
    RAIN(R.string.weather_rain, R.drawable.ic_weather_rain),
    SNOW(R.string.weather_snow, R.drawable.ic_weather_snow),
    STORM(R.string.weather_storm, R.drawable.ic_weather_storm);

    companion object {
        /** WMO weather interpretation codes, as Open-Meteo reports them. */
        fun of(code: Int): Sky = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}

/** The part of a forecast the island uses. */
data class WeatherReport(
    val temperature: Float,
    val fahrenheit: Boolean,
    val sky: Sky,
    val precipitationNow: Float,
    /** Upcoming 15-minute slots: start in epoch ms, and precipitation in mm. */
    val upcoming: List<Pair<Long, Float>>,
) {
    val degrees: String get() = "${temperature.roundToInt()}°"
}

object Weather {

    /** Enough rain to notice; anything less is a trace the forecast may not mean. */
    const val RAIN_MM = 0.1f
    const val RAIN_WINDOW_MS = 60 * 60_000L

    /** Places in the world that read temperatures in Fahrenheit. */
    private val fahrenheitCountries = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW", "FM", "MH")

    fun usesFahrenheit(locale: Locale = Locale.getDefault()) = locale.country in fahrenheitCountries

    /** "lat,lon" rounded to one decimal — about 10 km, plenty for weather and no more precise. */
    fun roundedArea(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.1f,%.1f", latitude, longitude)

    fun parseArea(area: String): Pair<Double, Double>? {
        val parts = area.split(',')
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    fun url(area: String, fahrenheit: Boolean): String? {
        val (lat, lon) = parseArea(area) ?: return null
        return "https://api.open-meteo.com/v1/forecast?latitude=${Http.encode(lat.toString())}" +
            "&longitude=${Http.encode(lon.toString())}" +
            "&current=temperature_2m,weather_code,precipitation" +
            "&minutely_15=precipitation&forecast_minutely_15=8&timeformat=unixtime" +
            if (fahrenheit) "&temperature_unit=fahrenheit" else ""
    }

    fun parse(json: String, fahrenheit: Boolean): WeatherReport {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        val slots = mutableListOf<Pair<Long, Float>>()
        root.optJSONObject("minutely_15")?.let { m ->
            val times = m.optJSONArray("time")
            val rain = m.optJSONArray("precipitation")
            if (times != null && rain != null) {
                for (i in 0 until minOf(times.length(), rain.length())) {
                    if (rain.isNull(i)) continue
                    slots += times.getLong(i) * 1000 to rain.getDouble(i).toFloat()
                }
            }
        }
        return WeatherReport(
            temperature = current.getDouble("temperature_2m").toFloat(),
            fahrenheit = fahrenheit,
            sky = Sky.of(current.optInt("weather_code", 3)),
            precipitationNow = current.optDouble("precipitation", 0.0).toFloat(),
            upcoming = slots,
        )
    }

    /**
     * When rain is due to start within the hour, or null. Only a dry spell turning wet counts —
     * rain that is already falling is not news.
     */
    fun rainStart(report: WeatherReport, nowMs: Long): Long? {
        if (report.precipitationNow >= RAIN_MM) return null
        return report.upcoming
            .firstOrNull { (start, mm) -> mm >= RAIN_MM && start + 15 * 60_000L > nowMs && start <= nowMs + RAIN_WINDOW_MS }
            ?.first
            ?.coerceAtLeast(nowMs)
    }
}
