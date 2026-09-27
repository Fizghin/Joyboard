package com.joyboard.notchisland

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.joyboard.notchisland.util.CrashReporter

class NotchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
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

    companion object {
        const val CHANNEL_SERVICE = "island_service"
    }
}
