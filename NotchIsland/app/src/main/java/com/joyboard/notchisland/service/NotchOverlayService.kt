package com.joyboard.notchisland.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.joyboard.notchisland.MainActivity
import com.joyboard.notchisland.NotchApp
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.SettingsRepository
import com.joyboard.notchisland.island.IslandController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Hosts the overlay for as long as the island is switched on. */
class NotchOverlayService : LifecycleService() {

    private var controller: IslandController? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        promoteToForeground()
        lifecycleScope.launch {
            val repository = SettingsRepository.get(this@NotchOverlayService)
            val initial = repository.settings.first()
            if (!Settings.canDrawOverlays(this@NotchOverlayService)) {
                stopSelf()
                return@launch
            }
            val created = IslandController(this@NotchOverlayService)
            controller = created
            IslandBus.controller = created
            IslandBus.serviceRunning = true
            created.start(initial)
            started = true
            repository.settings.collect { settings ->
                if (!settings.enabled) {
                    stopSelf()
                    return@collect
                }
                created.applySettings(settings)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_EXPAND -> controller?.onGesture(com.joyboard.notchisland.data.GestureAction.EXPAND)
            ACTION_COLLAPSE -> controller?.onGesture(com.joyboard.notchisland.data.GestureAction.COLLAPSE)
            ACTION_START_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_TIMER_MINUTES, 1)
                controller?.startTimer(minutes * 60_000L)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged(newConfig)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        IslandBus.controller = null
        IslandBus.serviceRunning = false
        controller?.stop()
        controller = null
        started = false
        super.onDestroy()
    }

    private fun promoteToForeground() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, NotchOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, NotchApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_island)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(getString(R.string.service_running_desc))
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val ACTION_EXPAND = "com.joyboard.notchisland.EXPAND"
        const val ACTION_COLLAPSE = "com.joyboard.notchisland.COLLAPSE"
        const val ACTION_START_TIMER = "com.joyboard.notchisland.START_TIMER"
        const val ACTION_STOP = "com.joyboard.notchisland.STOP"
        const val EXTRA_TIMER_MINUTES = "timer_minutes"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, NotchOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotchOverlayService::class.java))
        }

        fun send(context: Context, action: String, configure: Intent.() -> Unit = {}) {
            if (!IslandBus.serviceRunning) return
            val intent = Intent(context, NotchOverlayService::class.java).setAction(action)
            intent.configure()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
