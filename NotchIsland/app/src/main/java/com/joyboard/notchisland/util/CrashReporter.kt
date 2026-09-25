package com.joyboard.notchisland.util

import android.content.Context
import android.os.Build
import com.joyboard.notchisland.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes uncaught exceptions to a file on the device so a problem in the field can be reported
 * without a debugger attached. Nothing is sent anywhere — the log stays in the app's own storage
 * until the person chooses to share it.
 */
object CrashReporter {

    private const val LOG_NAME = "crash-log.txt"
    private const val MAX_BYTES = 64 * 1024

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // Never swallow the crash: the system still needs to see it.
            previous?.uncaughtException(thread, error)
        }
    }

    fun log(context: Context): String? {
        val file = File(context.filesDir, LOG_NAME)
        return if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, LOG_NAME).delete() }
    }

    /** Device and build details worth having beside any stack trace. */
    fun diagnostics(context: Context): String = buildString {
        appendLine("Notch Island ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Flavour: ${BuildConfig.FLAVOR}, build type: ${BuildConfig.BUILD_TYPE}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        val metrics = context.resources.displayMetrics
        appendLine("Screen: ${metrics.widthPixels}x${metrics.heightPixels} @${metrics.density}x")
        CutoutDetector.detect(context)?.let {
            appendLine("Cutout: ${it.widthDp}x${it.heightDp} dp, offset ${it.centerOffsetDp} dp, top ${it.topDp} dp")
        } ?: appendLine("Cutout: none reported")
        appendLine("Overlay permission: ${context.canDrawOverlays()}")
        appendLine("Notification access: ${context.hasNotificationAccess()}")
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val file = File(context.filesDir, LOG_NAME)
        if (file.exists() && file.length() > MAX_BYTES) file.delete()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        file.appendText(
            buildString {
                appendLine("──────── $stamp ────────")
                appendLine(diagnostics(context))
                appendLine("Thread: ${thread.name}")
                appendLine(trace.toString())
                appendLine()
            }
        )
    }
}
