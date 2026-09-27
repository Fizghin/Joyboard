package com.joyboard.notchisland.util

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Sends another app's PendingIntent from the island.
 *
 * Since Android 14 the *sender* of a PendingIntent has to opt in before its privilege to start
 * activities is lent to the intent. The island is a visible window when someone taps it, so it
 * has that privilege — but a plain send() does not pass it on, and the notification's app, being
 * in the background, has none of its own. The result was a tap on "Open" that quietly did
 * nothing. This passes the privilege on explicitly, using the narrowest mode each release offers.
 */
object PendingIntents {

    fun send(context: Context, pendingIntent: PendingIntent, fillIn: Intent? = null): Boolean =
        runCatching {
            pendingIntent.send(context, 0, fillIn, null, null, null, options())
            true
        }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun options(): Bundle? = when {
        // Only while our window is actually on screen, which is exactly when a tap happens.
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            ).toBundle()
        Build.VERSION.SDK_INT >= 34 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            ).toBundle()
        Build.VERSION.SDK_INT == 33 -> ActivityOptions.makeBasic()
            .apply { setPendingIntentBackgroundActivityLaunchAllowed(true) }
            .toBundle()
        else -> null
    }
}
