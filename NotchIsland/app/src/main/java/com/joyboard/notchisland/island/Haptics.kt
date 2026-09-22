package com.joyboard.notchisland.island

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short, iOS-flavoured taps used when the island changes shape. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var enabled: Boolean = true
    var strength: Int = 2

    fun tick() = buzz(when (strength) { 1 -> 8L; 3 -> 26L; else -> 16L })

    fun pop() = buzz(when (strength) { 1 -> 14L; 3 -> 40L; else -> 24L })

    private fun buzz(ms: Long) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            val amplitude = when (strength) {
                1 -> 60
                3 -> 255
                else -> 140
            }
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        }
    }
}
