package com.joyboard.notchisland.service

import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Turns the island on and off from the quick settings shade. */
class IslandTileService : TileService() {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            val settings = SettingsRepository.get(applicationContext).settings.first()
            render(settings.enabled)
        }
    }

    override fun onStopListening() {
        scope.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext
        if (!Settings.canDrawOverlays(context)) {
            // Nothing can be drawn without the permission, so send them where it is granted.
            runCatching { startActivityAndCollapse(MainActivityPendingIntent.of(context)) }
            return
        }
        scope.launch {
            val repository = SettingsRepository.get(context)
            val enabled = !repository.settings.first().enabled
            repository.setEnabled(enabled)
            if (enabled) NotchOverlayService.start(context) else NotchOverlayService.stop(context)
            render(enabled)
        }
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_island)
        tile.contentDescription = getString(
            if (enabled) R.string.tile_on else R.string.tile_off
        )
        tile.updateTile()
    }
}
