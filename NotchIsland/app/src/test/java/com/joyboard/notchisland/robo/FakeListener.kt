package com.joyboard.notchisland.robo

import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.IslandView
import com.joyboard.notchisland.island.MediaCommand
import com.joyboard.notchisland.island.NotificationAction
import com.joyboard.notchisland.island.NotificationItem
import com.joyboard.notchisland.island.QuickToggle
import com.joyboard.notchisland.island.StopwatchCommand
import com.joyboard.notchisland.island.TimerCommand

/** Records gestures and answers the island's questions with fixed values. */
class FakeListener : IslandView.Listener {
    val gestures = mutableListOf<GestureAction>()
    override fun onRequestMode(mode: IslandMode) = Unit
    override fun onGesture(action: GestureAction) { gestures += action }
    override fun onMediaCommand(command: MediaCommand) = Unit
    override fun onMediaSeek(positionMs: Long) = Unit
    override fun onTimerCommand(command: TimerCommand) = Unit
    override fun onStopwatchCommand(command: StopwatchCommand) = Unit
    override fun onSendReply(item: NotificationItem, text: CharSequence) = Unit
    override fun onReplyFocusChanged(active: Boolean) = Unit
    override fun onCopyCode(code: String) = Unit
    override fun onHistoryTap(item: NotificationItem) = Unit
    override fun onQuickToggle(toggle: QuickToggle) = Unit
    override fun onVolumeChange(progress: Int) = Unit
    override fun onBrightnessChange(progress: Int) = Unit
    override fun onOpenPresentationTarget() = Unit
    override fun onNotificationAction(action: NotificationAction) = Unit
    override fun onDismissCurrent() = Unit
    override fun quickToggleState(toggle: QuickToggle) = toggle == QuickToggle.WIFI
    override fun currentVolume() = 9 to 15
    override fun currentBrightness() = 160
}
