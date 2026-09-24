package com.joyboard.notchisland.service

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.joyboard.notchisland.island.NotificationAction
import com.joyboard.notchisland.island.NotificationItem
import com.joyboard.notchisland.island.OtpExtractor
import com.joyboard.notchisland.island.ReplyAction

/**
 * Feeds notifications into the island and, just as importantly, is the component the system
 * checks before handing us the active media sessions.
 */
class NotchNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        instance = this
    }

    override fun onListenerDisconnected() {
        connected = false
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val item = convert(sbn) ?: return
        IslandBus.postNotification(item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        IslandBus.dropNotification(sbn.key)
    }

    /** Lets the island dismiss a notification it has dealt with. */
    fun dismiss(key: String) = runCatching { cancelNotification(key) }

    private fun convert(sbn: StatusBarNotification): NotificationItem? {
        if (sbn.packageName == packageName) return null
        val notification = sbn.notification ?: return null
        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val ongoing = flags and Notification.FLAG_ONGOING_EVENT != 0

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null

        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        val smallIcon: Drawable? = runCatching {
            notification.smallIcon?.loadDrawable(this)
        }.getOrNull()
        val largeIcon: Bitmap? = runCatching {
            notification.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96)
        }.getOrNull()
        val appIcon = runCatching { packageManager.getApplicationIcon(sbn.packageName) }.getOrNull()

        val allActions = notification.actions.orEmpty()
        val actions = allActions.mapNotNull { action ->
            val label = action.title?.toString() ?: return@mapNotNull null
            NotificationAction(label, action.actionIntent)
        }

        // The first action carrying a free-form RemoteInput is the app's own inline reply.
        val reply = allActions.firstNotNullOfOrNull { action ->
            val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }.orEmpty()
            val first = inputs.firstOrNull() ?: return@firstNotNullOfOrNull null
            ReplyAction(
                title = action.title?.toString() ?: "Reply",
                intent = action.actionIntent,
                resultKey = first.resultKey,
                remoteInputs = inputs.toTypedArray(),
            )
        }

        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = if (progressMax > 0) extras.getInt(Notification.EXTRA_PROGRESS, -1) else -1
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val isConversation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            notification.category == Notification.CATEGORY_MESSAGE

        return NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel,
            title = title.ifBlank { appLabel },
            text = text,
            smallIcon = smallIcon,
            largeIcon = largeIcon,
            appIcon = appIcon,
            accent = if (notification.color != 0) notification.color else 0xFF3B82F6.toInt(),
            whenMs = sbn.postTime,
            contentIntent = notification.contentIntent,
            actions = actions,
            isConversation = isConversation,
            ongoing = ongoing,
            category = notification.category,
            progress = progress,
            progressMax = progressMax,
            progressIndeterminate = indeterminate,
            otp = OtpExtractor.extract(title, text),
            reply = reply,
        )
    }

    companion object {
        @Volatile
        var instance: NotchNotificationListener? = null
            private set

        @Volatile
        var connected: Boolean = false
            private set
    }
}
