package com.joyboard.notchisland

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.joyboard.notchisland.data.SettingsRepository
import com.joyboard.notchisland.service.NotchOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Launcher shortcuts can only start activities, so this one starts, does the thing and closes
 * without ever drawing.
 */
class ShortcutRouterActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        val context = applicationContext

        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, R.string.needs_overlay_permission, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val repository = SettingsRepository.get(context)
            when (action) {
                ACTION_TOGGLE -> {
                    val enabled = !repository.settings.first().enabled
                    repository.setEnabled(enabled)
                    if (enabled) NotchOverlayService.start(context)
                    else NotchOverlayService.stop(context)
                }
                ACTION_TIMER -> {
                    ensureRunning(repository)
                    NotchOverlayService.send(context, NotchOverlayService.ACTION_START_TIMER) {
                        putExtra(
                            NotchOverlayService.EXTRA_TIMER_MINUTES,
                            intent.getIntExtra(NotchOverlayService.EXTRA_TIMER_MINUTES, 5)
                        )
                    }
                }
                ACTION_STOPWATCH -> {
                    ensureRunning(repository)
                    NotchOverlayService.send(context, NotchOverlayService.ACTION_START_STOPWATCH)
                }
                ACTION_HISTORY -> {
                    ensureRunning(repository)
                    NotchOverlayService.send(context, NotchOverlayService.ACTION_SHOW_HISTORY)
                }
            }
            finish()
        }
    }

    private suspend fun ensureRunning(repository: SettingsRepository) {
        if (!repository.settings.first().enabled) {
            repository.setEnabled(true)
        }
        NotchOverlayService.start(applicationContext)
    }

    companion object {
        const val ACTION_TOGGLE = "com.joyboard.notchisland.shortcut.TOGGLE"
        const val ACTION_TIMER = "com.joyboard.notchisland.shortcut.TIMER"
        const val ACTION_STOPWATCH = "com.joyboard.notchisland.shortcut.STOPWATCH"
        const val ACTION_HISTORY = "com.joyboard.notchisland.shortcut.HISTORY"
    }
}
