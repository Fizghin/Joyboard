package com.joyboard.notchisland.island

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Opens the system's own place for choosing where audio plays.
 *
 * MediaRouter2's output switcher only moves the calling app's audio, and the music here belongs
 * to someone else, so the volume panel is used instead: it carries the media output control on
 * every release that has it. Older phones get Bluetooth settings.
 */
object MediaOutput {

    fun show(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            launch(context, Intent(Settings.Panel.ACTION_VOLUME))
        ) {
            return true
        }
        return launch(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun launch(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
