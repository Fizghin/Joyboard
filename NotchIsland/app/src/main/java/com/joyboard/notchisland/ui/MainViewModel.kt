package com.joyboard.notchisland.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.SettingsRepository
import com.joyboard.notchisland.service.IslandBus
import com.joyboard.notchisland.service.NotchOverlayService
import com.joyboard.notchisland.util.canDrawOverlays
import com.joyboard.notchisland.util.canWriteSettings
import com.joyboard.notchisland.util.hasNotificationAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionState(
    val overlay: Boolean = false,
    val notificationAccess: Boolean = false,
    val writeSettings: Boolean = false,
    val dndAccess: Boolean = false,
) {
    val essentialsGranted: Boolean get() = overlay
    val allGranted: Boolean get() = overlay && notificationAccess && writeSettings && dndAccess
}

data class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SettingsRepository.get(app)

    val settings: StateFlow<IslandSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, IslandSettings())

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    val serviceRunning: Boolean get() = IslandBus.serviceRunning

    init {
        refreshPermissions()
        loadApps()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        val nm = context.getSystemService(NotificationManager::class.java)
        _permissions.value = PermissionState(
            overlay = context.canDrawOverlays(),
            notificationAccess = context.hasNotificationAccess(),
            writeSettings = context.canWriteSettings(),
            dndAccess = nm?.isNotificationPolicyAccessGranted == true,
        )
    }

    fun update(transform: (IslandSettings) -> IslandSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun resetToDefaults() {
        viewModelScope.launch { repository.resetToDefaults() }
    }

    fun setEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            repository.setEnabled(enabled)
            if (enabled && context.canDrawOverlays()) {
                NotchOverlayService.start(context)
            } else if (!enabled) {
                NotchOverlayService.stop(context)
            }
        }
    }

    /** Brings the overlay back if the system killed it while the app was away. */
    fun ensureServiceRunning() {
        val context = getApplication<Application>()
        if (settings.value.enabled && context.canDrawOverlays() && !IslandBus.serviceRunning) {
            NotchOverlayService.start(context)
        }
    }

    /** Restarts the overlay so a fresh window picks up size or position changes cleanly. */
    fun restartOverlay() {
        val context = getApplication<Application>()
        if (!settings.value.enabled || !context.canDrawOverlays()) return
        NotchOverlayService.stop(context)
        viewModelScope.launch {
            kotlinx.coroutines.delay(260)
            NotchOverlayService.start(context)
        }
    }

    fun expandIsland() =
        NotchOverlayService.send(getApplication(), NotchOverlayService.ACTION_EXPAND)

    fun startTimer(minutes: Int) =
        NotchOverlayService.send(getApplication(), NotchOverlayService.ACTION_START_TIMER) {
            putExtra(NotchOverlayService.EXTRA_TIMER_MINUTES, minutes)
        }

    fun toggleBlocked(packageName: String) {
        viewModelScope.launch { repository.toggleBlocked(packageName) }
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val launchable = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0L)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
                resolved.mapNotNull { info ->
                    val appInfo: ApplicationInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                    AppEntry(
                        packageName = appInfo.packageName,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull(),
                    )
                }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
            _apps.value = launchable
        }
    }
}
