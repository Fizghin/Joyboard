package com.joyboard.notchisland.island

import android.view.animation.Interpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The same damped-spring response SwiftUI's `.spring(response:dampingFraction:)` produces, which
 * is what drives the Dynamic Island on iOS. Below a damping fraction of 1 it overshoots and
 * settles back, which is the bounce the real thing has and an ease curve cannot fake.
 *
 * @param response the period of a single undamped oscillation, in seconds
 * @param dampingFraction 1 settles without overshoot, lower values bounce more
 */
class SpringInterpolator(
    private val response: Float = 0.45f,
    private val dampingFraction: Float = 0.72f,
) : Interpolator {

    private val omega0 = (2.0 * PI / response.coerceAtLeast(0.05f)).toFloat()
    private val zeta = dampingFraction.coerceIn(0.1f, 1.4f)

    /** How long to run the animator for so the spring has visibly come to rest. */
    val settleDurationMs: Long = (response * SETTLE_FACTOR * 1000).toLong().coerceIn(120L, 1400L)

    override fun getInterpolation(input: Float): Float {
        if (input <= 0f) return 0f
        if (input >= 1f) return 1f
        val t = input * settleDurationMs / 1000f
        val envelope = exp(-zeta * omega0 * t)
        return when {
            zeta < 1f -> {
                val omegaD = omega0 * sqrt(1f - zeta * zeta)
                1f - envelope * (cos(omegaD * t) + (zeta * omega0 / omegaD) * sin(omegaD * t))
            }
            else -> 1f - envelope * (1f + omega0 * t)
        }
    }

    companion object {
        private const val SETTLE_FACTOR = 1.9f

        /** What the island itself uses when iOS motion is switched on. */
        fun island() = SpringInterpolator(response = 0.45f, dampingFraction = 0.72f)

        /** A tighter one for small changes, where a big bounce would read as sloppy. */
        fun subtle() = SpringInterpolator(response = 0.32f, dampingFraction = 0.86f)
    }
}
