package com.joyboard.notchisland.island

/**
 * Whether the island should step off the screen. Two kinds of reason are kept apart:
 *
 * - hard reasons — screen off, locked out, quiet hours, landscape, hidden for a moment — always
 *   win, because the person asked for them in so many words;
 * - context reasons — a full-screen video, the keyboard, an app on the hide list — are about
 *   staying out of the way, and give way to a call, which is never something to hide.
 */
object VisibilityPolicy {

    data class Inputs(
        val screenOn: Boolean = true,
        val lockedOut: Boolean = false,
        val temporarilyHidden: Boolean = false,
        val quiet: Boolean = false,
        val landscape: Boolean = false,
        val hideInLandscape: Boolean = false,
        val fullscreen: Boolean = false,
        val hideInFullscreen: Boolean = false,
        val keyboardVisible: Boolean = false,
        val hideWhileTyping: Boolean = false,
        /** The island's own reply box is what raised the keyboard. */
        val replying: Boolean = false,
        val foregroundPackage: String? = null,
        val hiddenInPackages: Set<String> = emptySet(),
        /** Something that must be seen right now, such as a ringing call. */
        val urgent: Boolean = false,
    )

    fun shouldHide(i: Inputs): Boolean {
        val hard = !i.screenOn || i.lockedOut || i.temporarilyHidden || i.quiet ||
            (i.landscape && i.hideInLandscape)
        if (hard) return true
        if (i.urgent) return false
        val fullscreen = i.fullscreen && i.hideInFullscreen
        val typing = i.keyboardVisible && i.hideWhileTyping && !i.replying
        val app = i.foregroundPackage != null && i.foregroundPackage in i.hiddenInPackages
        return fullscreen || typing || app
    }
}
