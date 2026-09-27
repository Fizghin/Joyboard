package com.joyboard.notchisland.util

import com.joyboard.notchisland.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A plain GET for the opt-in online features. Blocking: call it off the main thread. */
object Http {

    /** Public APIs ask to be told who is calling; this says the app and where it lives. */
    val USER_AGENT = "NotchIsland/${BuildConfig.VERSION_NAME} (+https://github.com/Fizghin/Joyboard)"

    /** The status code, and the body when the request succeeded. */
    fun get(url: String): Pair<Int, String?> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = connection.responseCode
            code to if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            connection.disconnect()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
