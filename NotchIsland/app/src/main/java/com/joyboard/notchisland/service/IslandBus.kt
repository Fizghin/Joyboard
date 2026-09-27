package com.joyboard.notchisland.service

import android.os.Handler
import android.os.Looper
import com.joyboard.notchisland.island.IslandController
import java.lang.ref.WeakReference
import com.joyboard.notchisland.island.NotificationItem
import android.view.WindowManager

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

    /** The app in front, as last reported by the helper service; null when it is not running. */
    @Volatile
    var foregroundPackage: String? = null
        private set

    @Volatile
    var keyboardVisible: Boolean = false
        private set

    /** Whether the helper service is connected right now. */
    @Volatile
    var helperConnected: Boolean = false

    /**
     * The helper's own WindowManager. Windows added through it can be accessibility overlays,
     * the one kind of window an app may place above the status bar. Weak, like the controller.
     */
    @Volatile
    private var helperWindowManagerRef: WeakReference<WindowManager>? = null

    val helperWindowManager: WindowManager? get() = helperWindowManagerRef?.get()

    /** The notification shade is pulled down, as far as the helper can tell. */
    @Volatile
    var shadeOpen: Boolean = false
        private set

    fun setHelperWindowManager(windowManager: WindowManager?) {
        helperWindowManagerRef = windowManager?.let(::WeakReference)
        val target = controller ?: return
        handler.post { target.onHelperChanged() }
    }

    fun postShade(open: Boolean) {
        if (open == shadeOpen) return
        shadeOpen = open
        val target = controller ?: return
        handler.post { target.onContextChanged() }
    }

    fun postForegroundApp(packageName: String?) {
        if (packageName == foregroundPackage) return
        foregroundPackage = packageName
        val target = controller ?: return
        handler.post { target.onContextChanged() }
    }

    fun postKeyboard(visible: Boolean) {
        if (visible == keyboardVisible) return
        keyboardVisible = visible
        val target = controller ?: return
        handler.post { target.onContextChanged() }
    }

    /** The helper went away, so what it last said can no longer be trusted. */
    fun clearHelperState() {
        helperConnected = false
        postForegroundApp(null)
        postKeyboard(false)
        postShade(false)
        setHelperWindowManager(null)
    }

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
