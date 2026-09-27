package com.joyboard.notchisland.service

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import com.joyboard.notchisland.MainActivity
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
            openApp()
            return
        }
        scope.launch {
            val repository = SettingsRepository.get(context)
            val enabled = !repository.settings.first().enabled
            repository.setEnabled(enabled)
            if (enabled) {
                // A tile is not a foreground surface, so Android may refuse the service here.
                // The app itself can always start it, so hand over rather than fail silently.
                if (!NotchOverlayService.start(context)) openApp()
            } else {
                NotchOverlayService.stop(context)
            }
            render(enabled)
        }
    }

    /**
     * The PendingIntent overload only exists from Android 14, and the Intent one throws for apps
     * targeting 14 or later on those releases — so each release gets the one it accepts.
     */
    // The deprecated overload is used only below Android 14, where it is the only one there is.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openApp() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(MainActivityPendingIntent.of(applicationContext))
            } else {
                startActivityAndCollapse(
                    Intent(applicationContext, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
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
