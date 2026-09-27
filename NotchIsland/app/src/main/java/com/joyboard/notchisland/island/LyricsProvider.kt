package com.joyboard.notchisland.island

import android.os.Handler
import android.os.Looper
import com.joyboard.notchisland.util.Http
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.abs
import android.os.SystemClock

/**
 * Finds synced lyrics for the song that is playing, from LRCLIB — a free, public lyrics
 * database that needs no account. Only the title, artist, album and length are sent, and only
 * while the lyrics setting is on. Answers are cached per song, misses included, so a track is
 * looked up once however often it is played.
 */
class LyricsProvider(
    private val fetch: (String) -> Pair<Int, String?> = Http::get,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    /** Song key to its lines; an empty list means "looked, there are none". */
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>) = size > CACHE_SIZE
    }
    private val inFlight = mutableSetOf<String>()
    /** When a lookup last failed, so being offline does not mean a request per state change. */
    private val failedAt = HashMap<String, Long>()

    /** The lines for a song already looked up, empty when it has none; null when not yet known. */
    fun cached(song: Song): List<LyricLine>? = cache[song.key]

    /** Looks the song up once; [onReady] runs on the main thread with whatever was found. */
    fun request(song: Song, onReady: (Song) -> Unit) {
        if (cache.containsKey(song.key)) return
        val failed = failedAt[song.key]
        if (failed != null && SystemClock.elapsedRealtime() - failed < RETRY_AFTER_MS) return
        if (!inFlight.add(song.key)) return
        executor.execute {
            // A network failure is not an answer, so it is not cached and the next play retries.
            val lines = runCatching { lookUp(song) }.getOrNull()
            handler.post {
                inFlight.remove(song.key)
                if (lines != null) {
                    failedAt.remove(song.key)
                    cache[song.key] = lines
                    onReady(song)
                } else {
                    failedAt[song.key] = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    fun release() = executor.shutdownNow()

    private fun lookUp(song: Song): List<LyricLine> {
        if (song.album != null && song.durationMs > 0) {
            val (code, body) = fetch(getUrl(song))
            if (code == 200 && body != null) return fromRecord(JSONObject(body))
            if (code != 404) error("lyrics lookup failed: $code")
        }
        // Without an album or a length, or when the exact match misses, search instead.
        val (code, body) = fetch(searchUrl(song))
        if (code != 200 || body == null) error("lyrics search failed: $code")
        return fromSearch(JSONArray(body), song.durationMs)
    }

    /** What identifies a song for lyrics. */
    data class Song(val title: String, val artist: String, val album: String?, val durationMs: Long) {
        val key: String get() = "${artist.lowercase()}|${title.lowercase()}|${durationMs / 1000}"

        companion object {
            fun of(media: MediaSnapshot): Song? {
                if (media.title.isBlank() || media.artist.isBlank()) return null
                return Song(media.title, media.artist, media.album?.takeIf { it.isNotBlank() }, media.durationMs)
            }
        }
    }

    companion object {
        private const val CACHE_SIZE = 40
        private const val RETRY_AFTER_MS = 5 * 60_000L
        private const val BASE = "https://lrclib.net/api"

        fun getUrl(song: Song): String =
            "$BASE/get?track_name=${Http.encode(song.title)}&artist_name=${Http.encode(song.artist)}" +
                "&album_name=${Http.encode(song.album.orEmpty())}&duration=${song.durationMs / 1000}"

        fun searchUrl(song: Song): String =
            "$BASE/search?track_name=${Http.encode(song.title)}&artist_name=${Http.encode(song.artist)}"

        /** One LRCLIB record: its synced lines, or none for an instrumental or unsynced one. */
        fun fromRecord(record: JSONObject): List<LyricLine> {
            if (record.optBoolean("instrumental")) return emptyList()
            val synced = record.optString("syncedLyrics").takeIf { !record.isNull("syncedLyrics") && it.isNotBlank() }
                ?: return emptyList()
            return Lrc.parse(synced)
        }

        /** The first search result with synced lines, within a few seconds of the song's length. */
        fun fromSearch(results: JSONArray, durationMs: Long): List<LyricLine> {
            for (i in 0 until results.length()) {
                val record = results.optJSONObject(i) ?: continue
                val seconds = record.optDouble("duration", Double.NaN)
                if (durationMs > 0 && !seconds.isNaN() && abs(seconds * 1000 - durationMs) > 3_000) continue
                val lines = fromRecord(record)
                if (lines.isNotEmpty()) return lines
            }
            return emptyList()
        }
    }
}
