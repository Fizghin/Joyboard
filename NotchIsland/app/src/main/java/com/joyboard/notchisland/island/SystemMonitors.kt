package com.joyboard.notchisland.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Charging state, level and whether the charger is a fast one. */
data class BatteryState(
    val level: Int,
    val plugged: Boolean,
    val fast: Boolean,
    val full: Boolean,
)

class BatteryMonitor(
    private val context: Context,
    private val onChange: (BatteryState, changedPlug: Boolean) -> Unit,
) {
    private var lastPlugged: Boolean? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = read(intent) ?: return
            val plugChanged = lastPlugged != null && lastPlugged != state.plugged
            lastPlugged = state.plugged
            onChange(state, plugChanged)
        }
    }

    fun start() {
        if (registered) return
        registered = true
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sticky?.let {
            read(it)?.let { state ->
                lastPlugged = state.plugged
                onChange(state, false)
            }
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun current(): BatteryState? {
        val intent = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return intent?.let { read(it) }
    }

    private fun read(intent: Intent): BatteryState? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0) return null
        val plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val percent = (level * 100f / scale.coerceAtLeast(1)).toInt()
        return BatteryState(
            level = percent,
            plugged = plug != 0,
            fast = plug == BatteryManager.BATTERY_PLUGGED_AC,
            full = status == BatteryManager.BATTERY_STATUS_FULL || percent >= 100,
        )
    }
}

class VolumeMonitor(
    private val context: Context,
    private val onChange: (stream: Int, level: Int, max: Int) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var lastLevels = HashMap<Int, Int>()
    private var registered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION)
                .forEach { stream ->
                    val level = audio?.getStreamVolume(stream) ?: return@forEach
                    if (lastLevels[stream] != level) {
                        lastLevels[stream] = level
                        onChange(stream, level, audio.getStreamMaxVolume(stream))
                    }
                }
        }
    }

    fun start() {
        if (registered) return
        registered = true
        listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION)
            .forEach { lastLevels[it] = audio?.getStreamVolume(it) ?: 0 }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, observer
        )
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun musicVolume(): Pair<Int, Int> {
        val a = audio ?: return 0 to 15
        return a.getStreamVolume(AudioManager.STREAM_MUSIC) to a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    fun setMusicVolume(value: Int) {
        runCatching {
            audio?.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        }
    }
}

class RingerMonitor(
    private val context: Context,
    private val onChange: (mode: Int) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var lastMode = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val mode = audio?.ringerMode ?: return
            if (mode != lastMode) {
                lastMode = mode
                onChange(mode)
            }
        }
    }

    fun start() {
        if (registered) return
        registered = true
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun mode(): Int = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

    fun cycle() {
        val a = audio ?: return
        val next = when (a.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        runCatching { a.ringerMode = next }
    }
}

class ScreenMonitor(
    private val context: Context,
    private val onUnlock: () -> Unit,
    private val onScreenOff: () -> Unit,
    private val onScreenOn: () -> Unit,
) {
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> onUnlock()
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    fun start() {
        if (registered) return
        registered = true
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }
}

/** Mirrors Android's own privacy dots: tells the island when the mic or camera goes live. */
class PrivacyMonitor(
    private val context: Context,
    private val onChange: (micActive: Boolean, cameraActive: Boolean) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var micActive = false
    private var cameraActive = false
    private var started = false

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            val active = !configs.isNullOrEmpty()
            if (active != micActive) {
                micActive = active
                onChange(micActive, cameraActive)
            }
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            if (cameraActive) {
                cameraActive = false
                onChange(micActive, cameraActive)
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            if (!cameraActive) {
                cameraActive = true
                onChange(micActive, cameraActive)
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        runCatching { audio?.registerAudioRecordingCallback(recordingCallback, handler) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { cameras?.registerAvailabilityCallback(cameraCallback, handler) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { audio?.unregisterAudioRecordingCallback(recordingCallback) }
        runCatching { cameras?.unregisterAvailabilityCallback(cameraCallback) }
    }
}
