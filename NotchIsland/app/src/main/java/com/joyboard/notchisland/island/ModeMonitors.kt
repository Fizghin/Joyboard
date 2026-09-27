package com.joyboard.notchisland.island

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/** Which audio outputs count as headphones, and how a burst of device events becomes one. */
object Headphones {

    private val types = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        // Compared as plain numbers, so the newer ones are safe on every release.
        23, // TYPE_HEARING_AID (API 28)
        26, // TYPE_BLE_HEADSET (API 31)
        27, // TYPE_BLE_SPEAKER (API 31)
    )

    fun isHeadphones(type: Int): Boolean = type in types

    /**
     * One pair of earbuds shows up as several outputs at once — media and call audio — so the
     * same name within a couple of seconds is the same arrival.
     */
    fun isRepeat(name: String, lastName: String?, msSinceLast: Long): Boolean =
        name == lastName && msSinceLast < 2_000
}

/**
 * Headphones plugged in or paired, with their name. AudioManager says so without any
 * permission; only devices that arrive after it starts are announced, not those already there.
 */
class HeadphoneMonitor(
    private val context: Context,
    private val onChange: (name: String, connected: Boolean) -> Unit,
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val known = mutableSetOf<Int>()
    private var started = false
    private var lastName: String? = null
    private var lastConnected = false
    private var lastAt = 0L

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = handle(added, true)
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = handle(removed, false)
    }

    fun start() {
        if (started) return
        started = true
        known.clear()
        // Registering reports every current device as "added"; those are not news.
        audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.forEach { known += it.id }
        runCatching { audio?.registerAudioDeviceCallback(callback, handler) }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { audio?.unregisterAudioDeviceCallback(callback) }
    }

    private fun handle(devices: Array<out AudioDeviceInfo>, connected: Boolean) {
        for (device in devices) {
            if (!device.isSink || !Headphones.isHeadphones(device.type)) continue
            if (connected && !known.add(device.id)) continue
            if (!connected) known.remove(device.id)
            val name = device.productName?.toString()?.trim().orEmpty()
            val now = SystemClock.elapsedRealtime()
            val repeat = connected == lastConnected && Headphones.isRepeat(name, lastName, now - lastAt)
            lastName = name
            lastConnected = connected
            lastAt = now
            if (!repeat) onChange(name, connected)
        }
    }
}

/** Do Not Disturb switching on or off, from whatever switched it. Needs no permission to read. */
class FocusMonitor(
    private val context: Context,
    private val onChange: (on: Boolean) -> Unit,
) {
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var registered = false
    private var last = isOn()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = isOn()
            if (now != last) {
                last = now
                onChange(now)
            }
        }
    }

    private fun isOn(): Boolean {
        val filter = notifications?.currentInterruptionFilter ?: return false
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun start() {
        if (registered) return
        registered = true
        last = isOn()
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }
}
