package com.joyboard.notchisland.service

import android.os.Handler
import android.os.Looper
import com.joyboard.notchisland.island.IslandController
import java.lang.ref.WeakReference
import com.joyboard.notchisland.island.NotificationItem

/** Lets the notification listener talk to the overlay service without binding to it. */
object IslandBus {

    private val handler = Handler(Looper.getMainLooper())

    // Weak, because this object lives as long as the process and the controller holds the
    // service's Context. The service clears it on destroy, but a crash in between must not pin
    // a dead service's window and views in memory.
    @Volatile
    private var controllerRef: WeakReference<IslandController>? = null

    var controller: IslandController?
        get() = controllerRef?.get()
        set(value) {
            controllerRef = value?.let(::WeakReference)
        }

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
