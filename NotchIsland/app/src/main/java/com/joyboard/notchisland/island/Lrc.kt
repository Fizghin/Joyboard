package com.joyboard.notchisland.island

/** One timed line of synced lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Reads LRC, the plain-text format synced lyrics come in: `[mm:ss.xx] words`. A line can carry
 * several timestamps when it repeats; `[offset:±ms]` shifts everything; other tags are ignored.
 */
object Lrc {

    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        // A positive offset means the lyrics come earlier.
        val offset = offsetTag.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val stamps = mutableListOf<Long>()
            var rest = raw.trim()
            while (true) {
                val match = stamp.matchAt(rest, 0) ?: break
                val (min, sec, frac) = match.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
                stamps += min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
                rest = rest.substring(match.range.last + 1)
            }
            val text = rest.trim()
            stamps.forEach { lines += LyricLine((it - offset).coerceAtLeast(0), text) }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** The index of the line being sung at [positionMs], or -1 before the first one starts. */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}
