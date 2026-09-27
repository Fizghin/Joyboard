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
        /** The notification shade is pulled down. */
        val shadeOpen: Boolean = false,
        /** The island is layered above the status bar — and so above the shade too. */
        val aboveStatusBar: Boolean = false,
    )

    fun shouldHide(i: Inputs): Boolean = reason(i) != null

    /** Why the island is hidden, or null when it is not — for diagnostics. */
    fun reason(i: Inputs): String? = when {
        !i.screenOn -> "screen off"
        i.lockedOut -> "lock screen"
        i.temporarilyHidden -> "hidden for a moment"
        i.quiet -> "quiet hours"
        i.landscape && i.hideInLandscape -> "landscape"
        // An island above the status bar would sit on top of the pulled-down shade, so it goes
        // while the shade is open; an ordinary overlay is simply covered by the shade.
        i.shadeOpen && i.aboveStatusBar -> "notification shade open"
        i.urgent -> null
        i.fullscreen && i.hideInFullscreen -> "full-screen app"
        i.keyboardVisible && i.hideWhileTyping && !i.replying -> "keyboard open"
        i.foregroundPackage != null && i.foregroundPackage in i.hiddenInPackages ->
            "hidden in ${i.foregroundPackage}"
        else -> null
    }
}
