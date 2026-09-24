package com.joyboard.notchisland.service

import android.os.Handler
import android.os.Looper
import com.joyboard.notchisland.island.IslandController
import com.joyboard.notchisland.island.NotificationItem

/** Lets the notification listener talk to the overlay service without binding to it. */
object IslandBus {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var controller: IslandController? = null

    @Volatile
    var serviceRunning: Boolean = false

    fun postNotification(item: NotificationItem) {
        val target = controller ?: return
        handler.post { target.onNotification(item) }
    }

    /** Ongoing and call activities retire when their notification is taken away. */
    fun dropNotification(key: String) {
        val target = controller ?: return
        handler.post { target.onNotificationRemoved(key) }
    }
}
