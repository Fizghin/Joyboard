package com.joyboard.notchisland.island

import kotlin.math.abs

/**
 * Which camera hole applies to the screen in use. A foldable has two screens with a camera each,
 * in different places; a hole placed on one says nothing about the other.
 */
object HoleResolver {

    /** Screens within this fraction of each other's width are taken to be the same screen. */
    const val SAME_SCREEN_TOLERANCE = 0.12f

    /**
     * @param stored the hole in settings, or null when none is set
     * @param storedScreenDp the screen width it was placed on; zero for unknown
     * @param currentScreenDp the screen width now
     * @param detect this screen's own cutout report, asked only when the screens differ
     */
    fun resolve(
        stored: Hole?,
        storedScreenDp: Float,
        currentScreenDp: Float,
        detect: () -> Hole?,
    ): Hole? {
        if (stored == null) return null
        if (storedScreenDp <= 0f || currentScreenDp <= 0f) return stored
        if (abs(currentScreenDp - storedScreenDp) / storedScreenDp <= SAME_SCREEN_TOLERANCE) return stored
        // The other screen: trust what this one reports, or keep clear of nothing rather than
        // steer around a camera that is not there.
        return detect()
    }
}
