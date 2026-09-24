package com.joyboard.notchisland.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.joyboard.notchisland.MainActivity

/** One place to build the "open the app" intent that several entry points need. */
object MainActivityPendingIntent {
    fun of(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
