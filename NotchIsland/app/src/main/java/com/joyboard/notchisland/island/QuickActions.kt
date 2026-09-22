package com.joyboard.notchisland.island

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.joyboard.notchisland.MainActivity

/**
 * The quick toggles inside the expanded island. Android reserves direct control of some radios
 * for the system, so those open the matching settings panel instead of flipping silently.
 */
class QuickActions(private val context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    var torchOn: Boolean = false
        private set

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            torchOn = enabled
        }
    }

    init {
        runCatching { cameraManager?.registerTorchCallback(torchCallback, null) }
    }

    fun release() {
        runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
    }

    fun state(toggle: QuickToggle): Boolean = when (toggle) {
        QuickToggle.TORCH -> torchOn
        QuickToggle.WIFI -> wifiEnabled()
        QuickToggle.BLUETOOTH -> bluetoothEnabled()
        QuickToggle.DND -> dndEnabled()
        QuickToggle.ROTATION -> autoRotateEnabled()
        QuickToggle.RINGER -> ringerAudible()
        else -> false
    }

    /** Returns true when the toggle was handled in place (no settings screen was opened). */
    fun perform(toggle: QuickToggle): Boolean = when (toggle) {
        QuickToggle.TORCH -> toggleTorch()
        QuickToggle.DND -> toggleDnd()
        QuickToggle.ROTATION -> toggleRotation()
        QuickToggle.RINGER -> { RingerMonitor(context) {}.cycle(); true }
        QuickToggle.WIFI -> { openPanel(Settings.ACTION_WIFI_SETTINGS); false }
        QuickToggle.BLUETOOTH -> { openPanel(Settings.ACTION_BLUETOOTH_SETTINGS); false }
        QuickToggle.SETTINGS -> { openPanel(Settings.ACTION_SETTINGS); false }
        QuickToggle.APP_SETTINGS -> {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            false
        }
    }

    private fun toggleTorch(): Boolean {
        val manager = cameraManager ?: return false
        val id = runCatching {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return false
        return runCatching {
            manager.setTorchMode(id, !torchOn)
            torchOn = !torchOn
            true
        }.getOrDefault(false)
    }

    private fun toggleDnd(): Boolean {
        val nm = notificationManager ?: return false
        if (!nm.isNotificationPolicyAccessGranted) {
            openPanel(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return false
        }
        return runCatching {
            nm.setInterruptionFilter(
                if (dndEnabled()) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            true
        }.getOrDefault(false)
    }

    private fun toggleRotation(): Boolean {
        if (!Settings.System.canWrite(context)) {
            openPanel(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            return false
        }
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (autoRotateEnabled()) 0 else 1
            )
            true
        }.getOrDefault(false)
    }

    fun brightness(): Int = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)

    fun setBrightness(value: Int): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(1, 255)
            )
            true
        }.getOrDefault(false)
    }

    private fun wifiEnabled(): Boolean = runCatching {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    }.getOrDefault(false)

    private fun bluetoothEnabled(): Boolean = runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }.getOrDefault(false)

    private fun dndEnabled(): Boolean = runCatching {
        val filter = notificationManager?.currentInterruptionFilter
        filter != null && filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrDefault(false)

    private fun autoRotateEnabled(): Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
    }.getOrDefault(false)

    private fun ringerAudible(): Boolean = runCatching {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audio.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL
    }.getOrDefault(true)

    private fun openPanel(action: String) {
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                action == Settings.ACTION_WIFI_SETTINGS
            ) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(action)
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
