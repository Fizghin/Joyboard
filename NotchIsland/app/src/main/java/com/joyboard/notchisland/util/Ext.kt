package com.joyboard.notchisland.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils

val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()

val Float.dp: Float
    get() = this * android.content.res.Resources.getSystem().displayMetrics.density

fun Context.spToPx(sp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

fun View.visible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

/** Blend [color] toward white/black so it stays readable on the island's dark body. */
fun readableAccent(color: Int): Int {
    var c = color
    var lum = ColorUtils.calculateLuminance(c)
    var guard = 0
    while (lum < 0.35 && guard++ < 12) {
        c = ColorUtils.blendARGB(c, Color.WHITE, 0.15f)
        lum = ColorUtils.calculateLuminance(c)
    }
    return c
}

fun Int.withAlpha(alpha: Float): Int =
    Color.argb((255 * alpha.coerceIn(0f, 1f)).toInt(), Color.red(this), Color.green(this), Color.blue(this))

fun Context.canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

fun Context.hasNotificationAccess(): Boolean {
    val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any { it.contains(packageName) }
}

fun Context.canWriteSettings(): Boolean = Settings.System.canWrite(this)

fun Context.openOverlaySettings() = startActivity(
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

fun Context.openNotificationAccessSettings() = startActivity(
    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

fun Context.openWriteSettings() = startActivity(
    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

fun Context.openDndAccessSettings() = startActivity(
    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

fun Context.openBatteryOptimisationSettings() = runCatching {
    startActivity(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
