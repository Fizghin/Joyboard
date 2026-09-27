package com.joyboard.notchisland.robo

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.ActivityKind
import com.joyboard.notchisland.island.BatteryState
import com.joyboard.notchisland.island.IslandController
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.service.IslandBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Regression tests for the 2.9 fixes; each failed against the code before it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class ControllerFixesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var controller: IslandController

    private val settings = IslandSettings(
        enabled = true,
        compactRestSeconds = 10,
        hapticsEnabled = false,
        featureMedia = false,
        featurePrivacy = false,
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

    @Test
    fun `a low battery is not announced again on every battery broadcast`() {
        val low = BatteryState(12, plugged = false, fast = false, full = false, temperatureC = 30f)
        controller.batteryChanged(low, plugChanged = false)
        idle(50)
        assertEquals(ActivityKind.BATTERY_LOW, controller.topKind)
        idle(6_000)
        // Android sends another broadcast whenever the temperature or voltage moves.
        controller.batteryChanged(low.copy(temperatureC = 30.5f), plugChanged = false)
        idle(50)
        assertNull(controller.topKind)
        controller.batteryChanged(low.copy(level = 10), plugChanged = false)
        idle(50)
        assertEquals("the next step down is worth saying", ActivityKind.BATTERY_LOW, controller.topKind)
    }

    @Test
    fun `volume keys move the open panel's slider instead of replacing the panel`() {
        controller.onGesture(GestureAction.SHOW_QUICK_PANEL)
        idle(100)
        assertEquals(IslandMode.EXPANDED, controller.currentMode)
        controller.volumeChanged(AudioManager.STREAM_MUSIC, 3, 15)
        idle(50)
        assertNull("the resting panel stays", controller.topKind)
        assertEquals(IslandMode.EXPANDED, controller.currentMode)
    }

    @Test
    fun `the island's own slider is not announced back to it`() {
        controller.onVolumeChange(7)
        controller.volumeChanged(AudioManager.STREAM_MUSIC, 7, 15)
        idle(50)
        assertNull(controller.topKind)
    }

    @Test
    fun `volume keys with the island closed still show the change`() {
        controller.volumeChanged(AudioManager.STREAM_MUSIC, 7, 15)
        idle(50)
        assertEquals(ActivityKind.VOLUME, controller.topKind)
    }

    @Test
    fun `a stopwatch running behind a resting island does not keep redrawing it`() {
        controller.startStopwatch()
        idle(100)
        controller.onGesture(GestureAction.COLLAPSE)
        idle(settings.compactRestSeconds * 1000L + 500)
        assertEquals(IslandMode.PILL, controller.currentMode)
        val before = controller.renders
        idle(3_000)
        assertTrue("rendered ${controller.renders - before} times in 3 s", controller.renders - before <= 2)
    }

    @Test
    fun `a running stopwatch still ticks in the compact island`() {
        controller.startStopwatch()
        controller.onRequestMode(IslandMode.COMPACT)
        idle(1_000)
        assertEquals(ActivityKind.STOPWATCH, controller.topKind)
        assertEquals(IslandMode.COMPACT, controller.currentMode)
    }
}
