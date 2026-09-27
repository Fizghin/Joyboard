package com.joyboard.notchisland

import com.joyboard.notchisland.island.LyricsProvider
import com.joyboard.notchisland.island.LyricsProvider.Song
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProviderTest {

    private val song = Song("Midnight City", "M83", "Hurry Up, We're Dreaming", 243_000)

    @Test
    fun `the exact lookup carries title, artist, album and length, encoded`() {
        val url = LyricsProvider.getUrl(song)
        assertTrue(url.startsWith("https://lrclib.net/api/get?"))
        assertTrue(url.contains("track_name=Midnight+City"))
        assertTrue(url.contains("album_name=Hurry+Up%2C+We%27re+Dreaming"))
        assertTrue(url.endsWith("duration=243"))
    }

    @Test
    fun `a record gives its synced lines`() {
        val record = JSONObject(
            """{"trackName":"Midnight City","instrumental":false,"plainLyrics":"x",
               "syncedLyrics":"[00:01.00] Waiting in a car\n[00:05.00] Waiting for a ride"}"""
        )
        assertEquals(listOf("Waiting in a car", "Waiting for a ride"), LyricsProvider.fromRecord(record).map { it.text })
    }

    @Test
    fun `instrumental and unsynced records give nothing to show`() {
        assertTrue(LyricsProvider.fromRecord(JSONObject("""{"instrumental":true,"syncedLyrics":null}""")).isEmpty())
        assertTrue(LyricsProvider.fromRecord(JSONObject("""{"instrumental":false,"syncedLyrics":null,"plainLyrics":"words"}""")).isEmpty())
    }

    @Test
    fun `search skips results of the wrong length and unsynced ones`() {
        val results = JSONArray(
            """[
              {"duration":180.0,"syncedLyrics":"[00:01.00] Wrong version"},
              {"duration":243.0,"syncedLyrics":null,"plainLyrics":"unsynced"},
              {"duration":242.0,"syncedLyrics":"[00:01.00] Right one"}
            ]"""
        )
        assertEquals("Right one", LyricsProvider.fromSearch(results, 243_000).single().text)
    }

    @Test
    fun `the same song is the same key whatever the case`() {
        assertEquals(song.key, song.copy(title = "MIDNIGHT CITY", artist = "m83", album = null).key)
    }
}
