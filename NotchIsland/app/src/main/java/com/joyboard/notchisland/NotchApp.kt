package com.joyboard.notchisland

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.joyboard.notchisland.util.CrashReporter

class NotchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = getString(R.string.service_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "island_service"
    }
}
