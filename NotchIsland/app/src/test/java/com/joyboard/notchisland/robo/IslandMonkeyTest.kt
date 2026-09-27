package com.joyboard.notchisland.robo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.AutomationRequest
import com.joyboard.notchisland.island.BatteryState
import com.joyboard.notchisland.island.IslandController
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.NotificationAction
import com.joyboard.notchisland.island.NotificationItem
import com.joyboard.notchisland.island.SpeedReading
import com.joyboard.notchisland.island.StopwatchCommand
import com.joyboard.notchisland.island.TimerCommand
import com.joyboard.notchisland.service.IslandBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.joyboard.notchisland.island.ActivityKind
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Every kind of activity, through the real controller and window, at every size — and at the
 * open size every button pressed, one after another, re-reading the panel after each press since
 * a press can change it. Nothing may throw, and no render may fail quietly.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class IslandMonkeyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var controller: IslandController

    private val settings = IslandSettings(
        enabled = true,
        hapticsEnabled = false,
        featureMedia = false,
        featurePrivacy = false,
        externalApiEnabled = true,
        featureNetworkSpeed = true,
        autoCollapseSeconds = 0,
    )

    @Before
    fun setUp() {
        controller = IslandController(context)
        controller.start(settings)
        idle(300)
    }

    @After
    fun tearDown() {
        IslandBus.clearHelperState()
        IslandBus.controller = null
        controller.stop()
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Buttons pressed across the run, so a test that silently pressed nothing cannot pass. */
    private var presses = 0

    private val avatar: Bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)

    private fun item(
        key: String,
        title: String = "Mia",
        text: String = "Are we still on for 7?",
        ongoing: Boolean = false,
        category: String? = null,
        progress: Int = -1,
        actions: List<String> = emptyList(),
        otp: String? = null,
    ) = NotificationItem(
        key = key, packageName = "com.example.app", appLabel = "Example", title = title, text = text,
        smallIcon = null, largeIcon = avatar, appIcon = null, accent = 0xFF3B82F6.toInt(),
        whenMs = System.currentTimeMillis(), contentIntent = null,
        actions = actions.map { NotificationAction(it, null) },
        ongoing = ongoing, category = category, progress = progress,
        progressMax = if (progress >= 0) 100 else 0, otp = otp,
    )

    /** Everything on the island that can be pressed, in the order it is drawn. */
    private fun pressables(): List<View> {
        val out = mutableListOf<View>()
        fun walk(v: View) {
            if (!v.isShown) return
            // Typing is not a press; the reply box has its own tests.
            if (v.isClickable && v !is EditText) out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        controller.islandRootView?.let { root ->
            if (root is ViewGroup) for (i in 0 until root.childCount) walk(root.getChildAt(i))
        }
        return out
    }

    /**
     * Opens the island on whatever [show] puts there and presses the n-th button, for each n in
     * turn, bringing the activity back before each press since the last one may have ended it.
     */
    private fun exercise(name: String, show: () -> Unit) {
        show()
        idle(50)
        for (mode in listOf(IslandMode.COMPACT, IslandMode.MEDIUM, IslandMode.EXPANDED)) {
            controller.onRequestMode(mode)
            idle(50)
        }
        var index = 0
        var guard = 0
        while (guard++ < 40) {
            show()
            controller.onRequestMode(IslandMode.EXPANDED)
            idle(50)
            val buttons = pressables()
            if (index >= buttons.size) break
            buttons[index].performClick()
            presses++
            idle(300)
            index++
        }
        // Clocks left running would tick through every exercise after this one.
        controller.onStopwatchCommand(StopwatchCommand.RESET)
        controller.onTimerCommand(TimerCommand.CANCEL)
        controller.onGesture(GestureAction.COLLAPSE)
        idle(50)
        assertEquals("a render failed quietly during $name", 0, controller.renderFailures)
    }

    @Test
    fun `every activity at every size, every button pressed`() {
        exercise("notification") {
            controller.onNotification(item("m", actions = listOf("Reply", "Mark as read"), otp = "482915"))
        }
        exercise("call") { controller.onNotification(item("c", category = "call", ongoing = true, actions = listOf("Decline", "Answer"))) }
        exercise("call without actions") { controller.onNotification(item("c2", category = "call", ongoing = true)) }
        exercise("download") { controller.onNotification(item("d", title = "Downloading", ongoing = true, progress = 40, actions = listOf("Pause"))) }
        exercise("indeterminate") {
            controller.onNotification(item("i", ongoing = true).copy(progressIndeterminate = true))
        }
        exercise("navigation") {
            controller.onNotification(item("n", title = "250 m", text = "Turn left", ongoing = true, category = "navigation", actions = listOf("Exit")))
        }
        exercise("charging") { controller.batteryChanged(BatteryState(50, plugged = true, fast = true, full = false, fullInMs = 3_600_000, temperatureC = 30f), true) }
        exercise("unplugged") { controller.batteryChanged(BatteryState(50, plugged = false, fast = false, full = false), true) }
        exercise("low battery") { controller.batteryChanged(BatteryState(9, plugged = false, fast = false, full = false), false) }
        exercise("hot battery") {
            controller.batteryChanged(BatteryState(50, plugged = false, fast = false, full = false, temperatureC = 30f), false)
            controller.batteryChanged(BatteryState(50, plugged = false, fast = false, full = false, temperatureC = 47f), false)
        }
        exercise("timer") { controller.startTimer(120_000) }
        exercise("stopwatch") { controller.startStopwatch() }
        exercise("torch") { controller.torchChanged(true) }
        exercise("headphones") { controller.headphonesChanged("Buds", connected = true) }
        exercise("network") { controller.networkSpeed(SpeedReading(3_000_000, 1_000, active = true)) }
        exercise("note") { controller.applySettings(settings.copy(pinnedNote = "Buy milk")) }
        exercise("automation") {
            controller.onAutomation(
                Intent(AutomationRequest.ACTION_SHOW)
                    .putExtra(AutomationRequest.EXTRA_TITLE, "Laundry")
                    .putExtra(AutomationRequest.EXTRA_TEXT, "Done")
                    .putExtra(AutomationRequest.EXTRA_EXPAND, true)
            )
        }
        exercise("history") {
            controller.onNotification(item("h"))
            controller.onGesture(GestureAction.SHOW_HISTORY)
        }
        exercise("resting") { controller.onGesture(GestureAction.SHOW_QUICK_PANEL) }
        exercise("timer ringing") {
            controller.startTimer(200)
            idle(400)
        }
        exercise("offline") {
            controller.connectivityChanged(online = true, wifi = true)
            controller.connectivityChanged(online = false, wifi = false)
            idle(5_500)
        }
        exercise("airplane") { controller.airplaneChanged(true) }
        // About forty buttons across all of them; far fewer means the sweep stopped finding them.
        assertTrue("only $presses buttons pressed", presses >= 35)
    }

    @Test
    fun `several at once, the bubble pressed, and every gesture`() {
        controller.startTimer(300_000)
        controller.torchChanged(true)
        controller.onNotification(item("d", ongoing = true, progress = 10))
        controller.applySettings(settings.copy(pinnedNote = "Note"))
        val fronts = mutableSetOf<ActivityKind?>()
        repeat(6) {
            controller.onRequestMode(IslandMode.COMPACT)
            idle(50)
            controller.tapSecondary()
            idle(50)
            fronts += controller.topKind
        }
        assertTrue("the bubble swapped between ${fronts}", fronts.size >= 2)
        for (action in GestureAction.entries) {
            controller.onGesture(action)
            idle(100)
        }
        controller.onStopwatchCommand(StopwatchCommand.RESET)
        controller.onTimerCommand(TimerCommand.CANCEL)
        controller.onGesture(GestureAction.COLLAPSE)
        // Long enough for "hide for a moment" to run out and the island to come back.
        idle(35_000)
        assertEquals(0, controller.renderFailures)
    }
}
