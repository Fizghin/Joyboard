package com.joyboard.notchisland.island

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.palette.graphics.Palette
import com.joyboard.notchisland.service.NotchNotificationListener
import com.joyboard.notchisland.util.dp

/**
 * Watches every active media session on the device and folds the most relevant one into a
 * [MediaSnapshot]. Requires notification access, which is also what powers the media controls
 * in the system's own quick settings.
 */
class MediaMonitor(
    private val context: Context,
    private val onUpdate: (MediaSnapshot?) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, NotchNotificationListener::class.java)
    private var controller: MediaController? = null
    private var started = false
    private val artCache = HashMap<String, Pair<Bitmap, Int>>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bind(controllers.orEmpty())
        }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
            refresh()
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            manager?.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            bind(manager?.getActiveSessions(component).orEmpty())
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        artCache.clear()
    }

    fun refresh() {
        runCatching { bind(manager?.getActiveSessions(component).orEmpty()) }
    }

    fun hasSession(): Boolean = controller != null

    fun command(command: MediaCommand) {
        val transport = controller?.transportControls ?: return
        when (command) {
            MediaCommand.PLAY_PAUSE ->
                if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) transport.pause()
                else transport.play()
            MediaCommand.NEXT -> transport.skipToNext()
            MediaCommand.PREVIOUS -> transport.skipToPrevious()
        }
        handler.postDelayed({ publish() }, 180)
    }

    fun seekTo(positionMs: Long) {
        controller?.transportControls?.seekTo(positionMs)
        handler.postDelayed({ publish() }, 180)
    }

    fun position(): Long {
        val state = controller?.playbackState ?: return 0
        if (state.state != PlaybackState.STATE_PLAYING) return state.position
        val delta = android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (delta * state.playbackSpeed).toLong()
    }

    fun snapshot(): MediaSnapshot? {
        val c = controller ?: return null
        val metadata = c.metadata
        val state = c.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: "Playing"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: appLabel(c.packageName)
        val raw = metadata?.let {
            it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }
        val cacheKey = "${c.packageName}:$title:$artist"
        val artAndAccent = raw?.let { bitmap ->
            artCache[cacheKey] ?: run {
                val scaled = scale(bitmap)
                val accent = dominantColor(scaled)
                (scaled to accent).also {
                    if (artCache.size > 8) artCache.clear()
                    artCache[cacheKey] = it
                }
            }
        }
        val actions = state?.actions ?: 0L
        return MediaSnapshot(
            packageName = c.packageName,
            appLabel = appLabel(c.packageName),
            title = title,
            artist = artist,
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            artwork = artAndAccent?.first,
            appIcon = runCatching { context.packageManager.getApplicationIcon(c.packageName) }.getOrNull(),
            playing = state?.state == PlaybackState.STATE_PLAYING,
            positionMs = position(),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            accent = artAndAccent?.second ?: 0xFF3B82F6.toInt(),
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L || actions == 0L,
            canSkipPrev = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L || actions == 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
        )
    }

    private fun bind(controllers: List<MediaController>) {
        val best = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull {
                val s = it.playbackState?.state
                s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING
            }
        if (best?.sessionToken == controller?.sessionToken) {
            publish()
            return
        }
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        onUpdate(snapshot())
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun scale(bitmap: Bitmap): Bitmap {
        val max = 160.dp
        if (bitmap.width <= max && bitmap.height <= max) return bitmap
        val ratio = bitmap.width.toFloat() / bitmap.height
        val width = if (ratio >= 1f) max else (max * ratio).toInt()
        val height = if (ratio >= 1f) (max / ratio).toInt() else max
        return Bitmap.createScaledBitmap(bitmap, width.coerceAtLeast(1), height.coerceAtLeast(1), true)
    }

    private fun dominantColor(bitmap: Bitmap): Int = runCatching {
        val palette = Palette.from(bitmap).clearFilters().generate()
        palette.getVibrantColor(
            palette.getLightVibrantColor(
                palette.getDominantColor(0xFF3B82F6.toInt())
            )
        )
    }.getOrDefault(0xFF3B82F6.toInt())
}
