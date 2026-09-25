package com.joyboard.notchisland.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joyboard.notchisland.BuildConfig
import com.joyboard.notchisland.data.DEFAULT_UPDATE_MANIFEST_URL
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.IslandPreset
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.data.SettingsCodec
import com.joyboard.notchisland.data.SettingsRepository
import com.joyboard.notchisland.service.IslandBus
import com.joyboard.notchisland.service.NotchOverlayService
import com.joyboard.notchisland.util.CrashReporter
import com.joyboard.notchisland.util.CutoutDetector
import com.joyboard.notchisland.util.canDrawOverlays
import com.joyboard.notchisland.util.canWriteSettings
import com.joyboard.notchisland.update.UpdateService
import com.joyboard.notchisland.update.UpdateState
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

    private val updateService = UpdateService(app)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()


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

    // ------------------------------------------------------------------ updates

    /** Called on launch: quiet, throttled, and silent when there is nothing new. */
    val updaterEnabled: Boolean = BuildConfig.UPDATER_ENABLED

    fun maybeCheckForUpdates() {
        if (!updaterEnabled) return
        val current = settings.value
        if (!current.autoCheckUpdates) return
        if (_updateState.value !is UpdateState.Idle) return
        val elapsed = System.currentTimeMillis() - current.lastUpdateCheck
        if (elapsed in 0 until CHECK_INTERVAL_MS) return
        runCheck(announce = false)
    }

    /** Called from a button: always reports back, even when there is nothing to do. */
    fun checkForUpdatesNow() {
        if (!updaterEnabled) return
        if (_updateState.value is UpdateState.Checking) return
        runCheck(announce = true)
    }

    private fun runCheck(announce: Boolean) {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            val result = updateService.check(
                settings.value.updateManifestUrl,
                BuildConfig.VERSION_CODE,
                BuildConfig.VERSION_NAME,
            )
            repository.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            _updateState.value = when {
                result is UpdateState.Available &&
                    !announce && result.info.versionCode == settings.value.skippedVersion ->
                    UpdateState.Idle
                result is UpdateState.UpToDate && !announce -> UpdateState.Idle
                result is UpdateState.Failed && !announce -> UpdateState.Idle
                else -> result
            }
        }
    }

    /** Downloads the release if needed, then hands it to the system installer. */
    fun installUpdate() {
        val state = _updateState.value
        val info = when (state) {
            is UpdateState.Available -> state.info
            is UpdateState.Failed -> state.info
            is UpdateState.ReadyToInstall -> {
                launchInstaller(state)
                return
            }
            else -> null
        } ?: run {
            checkForUpdatesNow()
            return
        }

        if (!updateService.canInstall()) {
            updateService.requestInstallPermission()
            _updateState.value = UpdateState.Failed(
                "Allow Notch Island to install apps, then tap Try again.", info
            )
            return
        }

        _updateState.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            val result = updateService.download(info, BuildConfig.DEBUG) { progress ->
                _updateState.value = UpdateState.Downloading(info, progress)
            }
            result
                .onSuccess { file ->
                    val ready = UpdateState.ReadyToInstall(info, file)
                    _updateState.value = ready
                    launchInstaller(ready)
                }
                .onFailure { error ->
                    _updateState.value = UpdateState.Failed(
                        error.message ?: "The download failed", info
                    )
                }
        }
    }

    private fun launchInstaller(state: UpdateState.ReadyToInstall) {
        if (!updateService.install(state.file)) {
            _updateState.value = UpdateState.Failed(
                "Android would not open the installer", state.info
            )
        }
    }

    fun setUpdateManifestUrl(url: String) {
        val trimmed = url.trim()
        viewModelScope.launch {
            repository.update {
                it.copy(
                    updateManifestUrl = trimmed.ifBlank { DEFAULT_UPDATE_MANIFEST_URL },
                    lastUpdateCheck = 0L,
                    skippedVersion = 0,
                )
            }
        }
        _updateState.value = UpdateState.Idle
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun skipUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info
        if (info != null) {
            viewModelScope.launch { repository.update { it.copy(skippedVersion = info.versionCode) } }
        }
        _updateState.value = UpdateState.Idle
    }

    fun toggleBlocked(packageName: String) {
        viewModelScope.launch { repository.toggleBlocked(packageName) }
    }

    /** Shapes the island to a known device, and turns iOS motion on for the Apple ones. */
    fun applyPreset(preset: IslandPreset) {
        viewModelScope.launch {
            repository.update { current ->
                preset.applyTo(current).let {
                    if (preset.isApple) it.copy(iosMode = true, showFauxCamera = true) else it
                }
            }
            _status.value = "Shaped to ${preset.label}"
        }
    }

    fun toggleAutoExpand(packageName: String) {
        viewModelScope.launch { repository.toggleAutoExpand(packageName) }
    }

    // ------------------------------------------------------------------ cutout, backup, restore

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun clearStatus() {
        _status.value = null
    }

    /** Measures the real camera cutout and shapes the island to match it. */
    fun fitToCutout() {
        val cutout = CutoutDetector.detect(getApplication())
        if (cutout == null) {
            _status.value = "No camera cutout reported by this display"
            return
        }
        viewModelScope.launch {
            repository.update {
                it.copy(
                    collapsedWidth = (cutout.widthDp + 16).coerceIn(48, 300),
                    collapsedHeight = (cutout.heightDp + 6).coerceIn(18, 64),
                    cornerRadius = ((cutout.heightDp + 6) / 2).coerceIn(6, 40),
                    offsetX = cutout.centerOffsetDp.coerceIn(-140, 140),
                    offsetY = cutout.topDp.coerceIn(0, 40),
                    positionMode = PositionMode.OVERLAP_STATUS_BAR,
                )
            }
            _status.value =
                "Matched a ${cutout.widthDp}×${cutout.heightDp} dp cutout"
        }
    }

    /** Everything worth pasting into a bug report, including any crash since the last clear. */
    fun diagnosticsText(): String {
        val context = getApplication<Application>()
        val crash = CrashReporter.log(context)
        return buildString {
            appendLine(CrashReporter.diagnostics(context))
            appendLine("Anchor: ${settings.value.positionMode}")
            appendLine("Preset: ${settings.value.preset} (iOS mode ${settings.value.iosMode})")
            appendLine("Island: ${settings.value.collapsedWidth}x${settings.value.collapsedHeight} dp")
            appendLine("Service running: ${IslandBus.serviceRunning}")
            if (crash != null) {
                appendLine()
                appendLine("Recent crashes:")
                appendLine(crash)
            } else {
                appendLine("No crashes recorded.")
            }
        }
    }

    fun clearCrashLog() {
        CrashReporter.clear(getApplication())
        _status.value = "Crash log cleared"
    }

    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            val json = SettingsCodec.toJson(settings.value)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toByteArray())
                    } ?: error("could not open the file")
                }.isSuccess
            }
            _status.value = if (ok) "Settings exported" else "Could not write that file"
        }
    }

    fun importSettings(uri: Uri) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (json.isNullOrBlank()) {
                _status.value = "Could not read that file"
                return@launch
            }
            val restored = runCatching { SettingsCodec.fromJson(json, settings.value) }.getOrNull()
            if (restored == null) {
                _status.value = "That file is not a Notch Island backup"
                return@launch
            }
            repository.update { restored }
            restartOverlay()
            _status.value = "Settings restored"
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
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
