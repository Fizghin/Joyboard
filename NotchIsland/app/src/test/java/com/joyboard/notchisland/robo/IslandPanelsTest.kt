package com.joyboard.notchisland.robo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.BatteryState
import com.joyboard.notchisland.island.IslandColors
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.IslandPanels
import com.joyboard.notchisland.island.IslandView
import com.joyboard.notchisland.island.NotificationItem
import com.joyboard.notchisland.island.Presentation
import com.joyboard.notchisland.island.Presentations
import com.joyboard.notchisland.island.RingProgressView
import com.joyboard.notchisland.island.StopwatchCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/** What the open island says, and that it says each thing once. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandPanelsTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val settings = IslandSettings()
    private val presentations = Presentations(activity, accent = { Color.BLUE }, settings = { settings })
    private val opened = mutableListOf<Unit>()
    private val stopwatch = mutableListOf<StopwatchCommand>()
    private val island = IslandView(activity, object : IslandView.Listener by FakeListener() {
        override fun onOpenPresentationTarget() { opened += Unit }
        override fun onStopwatchCommand(command: StopwatchCommand) { stopwatch += command }
    }).also {
        activity.setContentView(FrameLayout(activity).apply { addView(it, FrameLayout.LayoutParams(1, 1)) })
        it.applySettings(settings)
    }

    private fun show(p: Presentation, mode: IslandMode) {
        island.setPresentation(p)
        island.snapToMode(mode)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Every piece of text a person can see on the island right now. */
    private fun visibleTexts(): List<String> {
        val out = mutableListOf<String>()
        fun walk(v: View) {
            if (!v.isShown) return
            if (v is TextView && v.text.isNotBlank()) out += v.text.toString()
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(island)
        return out
    }

    private fun visibleWithDescription(label: String): View? {
        var found: View? = null
        fun walk(v: View) {
            if (!v.isShown || found != null) return
            if (v.contentDescription?.toString() == label) found = v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(island)
        return found
    }

    private fun item(title: String, text: String) = NotificationItem(
        key = title, packageName = "com.example", appLabel = "Messages", title = title, text = text,
        smallIcon = null, largeIcon = null, appIcon = null, accent = Color.GREEN,
        whenMs = 0L, contentIntent = null, actions = emptyList(),
    )

    @Test
    fun `a ring asked for the default green is drawn in it, not left black`() {
        val ring = RingProgressView(activity).apply {
            ringColor = 0xFF34C759.toInt()
            progress = 1f
            layout(0, 0, 100, 100)
        }
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        ring.draw(Canvas(bitmap))
        // The top of the ring, where a full ring's stroke always passes.
        val pixel = bitmap.getPixel(50, (ring.strokeWidth / 2 + 1).toInt())
        assertTrue("expected green, got ${Integer.toHexString(pixel)}", Color.green(pixel) > 150 && Color.red(pixel) < 120)
    }

    @Test
    fun `an open notification says its message once and names the app above it`() {
        val message = "Are we still on for 7?"
        show(presentations.notification(item("Mia", message)), IslandMode.EXPANDED)
        val texts = visibleTexts()
        assertEquals(texts.toString(), 1, texts.count { it == message })
        assertTrue(texts.toString(), "Messages" in texts)
    }

    @Test
    fun `the smaller card previews the message in its header`() {
        val message = "Are we still on for 7?"
        show(presentations.notification(item("Mia", message)), IslandMode.MEDIUM)
        assertTrue(visibleTexts().toString(), message in visibleTexts())
    }

    @Test
    fun `an activity with nothing more to say opens to its header alone`() {
        show(presentations.unlocked(), IslandMode.EXPANDED)
        assertEquals(listOf(activity.getString(R.string.unlocked)), visibleTexts())
    }

    @Test
    fun `the timer stands on its own, without a header repeating the time`() {
        show(presentations.timer(272_000, 300_000, running = true), IslandMode.EXPANDED)
        val texts = visibleTexts()
        assertEquals(texts.toString(), 1, texts.count { it == "04:32" })
        assertEquals(texts.toString(), 1, texts.count { it == activity.getString(R.string.timer) })
    }

    @Test
    fun `the header comes back when the timer shrinks to the smaller card`() {
        show(presentations.timer(272_000, 300_000, running = true), IslandMode.EXPANDED)
        show(presentations.timer(271_000, 300_000, running = true), IslandMode.MEDIUM)
        assertTrue(visibleTexts().toString(), "04:31" in visibleTexts())
    }

    @Test
    fun `laps read newest first, each as its own length`() {
        show(presentations.stopwatch(90_000, running = true, laps = listOf(41_200, 79_900)), IslandMode.EXPANDED)
        val texts = visibleTexts()
        val lap2 = texts.indexOf(activity.getString(R.string.lap_n, 2))
        val lap1 = texts.indexOf(activity.getString(R.string.lap_n, 1))
        assertTrue(texts.toString(), lap2 in 0 until lap1)
        assertEquals("00:38.70", texts[lap2 + 1])
        assertEquals("00:41.20", texts[lap1 + 1])
    }

    @Test
    fun `the stopwatch takes laps while running and resets once stopped`() {
        show(presentations.stopwatch(10_000, running = true, laps = emptyList()), IslandMode.EXPANDED)
        visibleWithDescription(activity.getString(R.string.lap_2))!!.performClick()
        show(presentations.stopwatch(10_000, running = false, laps = emptyList()), IslandMode.EXPANDED)
        visibleWithDescription(activity.getString(R.string.reset))!!.performClick()
        assertEquals(listOf(StopwatchCommand.LAP, StopwatchCommand.RESET), stopwatch)
    }

    @Test
    fun `low battery offers Battery Saver, and plugging in does not`() {
        show(presentations.batteryLow(BatteryState(12, plugged = false, fast = false, full = false)), IslandMode.EXPANDED)
        val saver = activity.getString(R.string.battery_saver)
        assertTrue(saver in visibleTexts())
        assertTrue(presentations.batteryLow(BatteryState(12, false, false, false)).tapIntent != null)
        show(presentations.charging(BatteryState(12, plugged = true, fast = false, full = false)), IslandMode.EXPANDED)
        assertFalse(saver in visibleTexts())
    }

    @Test
    fun `charging says when it will be full, when the phone knows`() {
        val state = BatteryState(60, plugged = true, fast = true, full = false, fullInMs = 65 * 60_000L)
        show(presentations.charging(state), IslandMode.EXPANDED)
        val span = activity.getString(R.string.duration_hours_minutes, 1, 5)
        assertTrue(visibleTexts().toString(), activity.getString(R.string.full_in, span) in visibleTexts())
    }

    @Test
    fun `colours that mean something hold whatever the accent`() {
        assertEquals(IslandColors.RED, presentations.batteryLow(BatteryState(9, false, false, false)).fixedAccent)
        assertEquals(IslandColors.GREEN, presentations.charging(BatteryState(50, true, false, false)).fixedAccent)
        assertEquals(IslandColors.ORANGE, presentations.timer(1_000, 2_000, true).fixedAccent)
        assertEquals(IslandColors.ORANGE, presentations.privacy(R.drawable.ic_mic, "Mic").fixedAccent)
        assertEquals(IslandColors.GREEN, presentations.privacy(R.drawable.ic_camera, "Camera").fixedAccent)
        assertEquals(null, presentations.volume(3, 5, 0.3f).fixedAccent)
    }

    @Test
    fun `the resting panel offers timers, and each starts its own length`() {
        val started = mutableListOf<Int>()
        val panelIsland = IslandView(activity, object : IslandView.Listener by FakeListener() {
            override fun onStartTimer(minutes: Int) { started += minutes }
        }).also {
            activity.setContentView(FrameLayout(activity).apply { addView(it, FrameLayout.LayoutParams(1, 1)) })
            it.applySettings(settings)
        }
        panelIsland.setPresentation(presentations.idle())
        panelIsland.snapToMode(IslandMode.EXPANDED)
        shadowOf(Looper.getMainLooper()).idle()
        for (minutes in listOf(1, 5, 10, 25)) {
            findByDescription(panelIsland, activity.getString(R.string.start_timer_minutes, minutes))!!.performClick()
        }
        assertEquals(listOf(1, 5, 10, 25), started)
    }

    @Test
    fun `no timer buttons when they are switched off`() {
        val off = IslandSettings(quickTimers = false)
        island.applySettings(off)
        show(Presentations(activity, accent = { Color.BLUE }, settings = { off }).idle(), IslandMode.EXPANDED)
        assertEquals(null, visibleWithDescription(activity.getString(R.string.start_timer_minutes, 5)))
    }

    @Test
    fun `directions show the distance, the turn and the arrival, once each`() {
        val route = item("250 m", "Turn left onto High St").copy(
            category = "navigation", ongoing = true, subText = "10:42 ETA",
        )
        show(presentations.navigation(route), IslandMode.EXPANDED)
        val texts = visibleTexts()
        assertEquals(texts.toString(), 1, texts.count { it == "250 m" })
        assertEquals(texts.toString(), 1, texts.count { it == "Turn left onto High St" })
        assertEquals(texts.toString(), 1, texts.count { it == "10:42 ETA" })
    }

    @Test
    fun `charging shows the battery's temperature when heat is on`() {
        val state = BatteryState(60, plugged = true, fast = false, full = false, temperatureC = 31.4f)
        show(presentations.charging(state), IslandMode.EXPANDED)
        val degrees = activity.getString(R.string.celsius, 31)
        assertTrue(visibleTexts().toString(), visibleTexts().any { degrees in it })
    }

    private fun findByDescription(root: View, label: String): View? {
        if (!root.isShown) return null
        if (root.contentDescription?.toString() == label) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findByDescription(root.getChildAt(i), label)?.let { return it }
        return null
    }

    @Test
    fun `call buttons are told apart by what they say`() {
        assertEquals(IslandPanels.CallAction.END, IslandPanels.callActionStyle("Decline"))
        assertEquals(IslandPanels.CallAction.END, IslandPanels.callActionStyle("Hang up"))
        assertEquals(IslandPanels.CallAction.ANSWER, IslandPanels.callActionStyle("Answer"))
        assertEquals(IslandPanels.CallAction.OTHER, IslandPanels.callActionStyle("Speaker"))
    }
}
