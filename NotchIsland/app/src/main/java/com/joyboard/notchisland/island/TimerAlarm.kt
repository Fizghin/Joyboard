package com.joyboard.notchisland.island

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The sound and the buzz of a countdown reaching zero. It rings on the alarm stream — so it is
 * heard with the ringer on silent, as a kitchen timer should be — and keeps going until it is
 * stopped or a minute has passed, whichever is first.
 */
class TimerAlarm(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private var ringtone: Ringtone? = null
    private val timeout = Runnable { stop() }

    var ringing = false
        private set

    fun start() {
        stop()
        ringing = true
        ringtone = runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }.getOrNull()
        runCatching {
            val v = vibrator
            // On for half a second, off for most of one, round again until stopped.
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(PATTERN, 0))
        }
        handler.postDelayed(timeout, LIMIT_MS)
    }

    fun stop() {
        handler.removeCallbacks(timeout)
        if (!ringing) return
        ringing = false
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
    }

    companion object {
        /** A timer nobody stops rings for this long, then gives up. */
        const val LIMIT_MS = 60_000L
        private val PATTERN = longArrayOf(0, 500, 700)
    }
}
