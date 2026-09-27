package com.joyboard.notchisland.robo

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.ActivityKind
import com.joyboard.notchisland.island.IslandController
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.TimerCommand
import com.joyboard.notchisland.service.IslandBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.Duration

/** The 2.9 features through the whole controller: the timer alarm, connection, sleep timer, gestures. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class Controller29Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var controller: IslandController

    private val settings = IslandSettings(
        enabled = true,
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

    // ------------------------------------------------------------------ timer alarm

    @Test
    fun `a finished timer rings, opens the island, and stays until stopped`() {
        controller.startTimer(3_000)
        idle(3_500)
        assertEquals(ActivityKind.ALARM, controller.topKind)
        assertTrue(controller.alarmRinging)
        assertEquals(IslandMode.EXPANDED, controller.currentMode)
        idle(20_000)
        assertEquals("still there after twenty seconds", ActivityKind.ALARM, controller.topKind)
        controller.onTimerCommand(TimerCommand.STOP_ALARM)
        idle(50)
        assertNull(controller.topKind)
        assertFalse(controller.alarmRinging)
    }

    @Test
    fun `repeat runs the same length again`() {
        controller.startTimer(3_000)
        idle(3_500)
        controller.onTimerCommand(TimerCommand.REPEAT)
        idle(1_000)
        assertEquals(ActivityKind.TIMER, controller.topKind)
        assertFalse(controller.alarmRinging)
        idle(3_000)
        assertEquals("and rings again when it runs out", ActivityKind.ALARM, controller.topKind)
    }

    @Test
    fun `nobody stopping it, it gives up after a minute`() {
        controller.startTimer(1_000)
        idle(1_500)
        idle(61_000)
        assertNull(controller.topKind)
        assertFalse(controller.alarmRinging)
    }

    @Test
    fun `dismissing the island silences it too`() {
        controller.startTimer(1_000)
        idle(1_500)
        controller.onDismissCurrent()
        idle(50)
        assertFalse(controller.alarmRinging)
    }

    @Test
    fun `with ringing off a finished timer just passes`() {
        controller.applySettings(settings.copy(timerAlarm = false))
        controller.startTimer(1_000)
        idle(1_500)
        assertEquals(ActivityKind.NOTIFICATION, controller.topKind)
        assertFalse(controller.alarmRinging)
        idle(5_000)
        assertNull(controller.topKind)
    }

    // ------------------------------------------------------------------ connectivity

    @Test
    fun `a moment offline is not news`() {
        controller.connectivityChanged(online = true, wifi = true)
        controller.connectivityChanged(online = false, wifi = false)
        idle(2_000)
        controller.connectivityChanged(online = true, wifi = false)
        idle(6_000)
        assertNull(controller.topKind)
    }

    @Test
    fun `staying offline is said, and so is coming back`() {
        controller.connectivityChanged(online = true, wifi = true)
        controller.connectivityChanged(online = false, wifi = false)
        idle(5_500)
        assertEquals(ActivityKind.CONNECTIVITY, controller.topKind)
        idle(5_000)
        controller.connectivityChanged(online = true, wifi = true)
        idle(50)
        assertEquals(ActivityKind.CONNECTIVITY, controller.topKind)
    }

    @Test
    fun `airplane mode is announced, and explains going offline`() {
        controller.connectivityChanged(online = true, wifi = true)
        controller.airplaneChanged(true)
        idle(50)
        assertEquals(ActivityKind.CONNECTIVITY, controller.topKind)
        idle(3_000)
        controller.connectivityChanged(online = false, wifi = false)
        controller.airplaneChanged(true)
        idle(7_000)
        assertNull(controller.topKind)
    }

    @Test
    fun `with connection alerts off nothing is said`() {
        controller.applySettings(settings.copy(featureConnectivity = false))
        controller.connectivityChanged(online = true, wifi = true)
        controller.connectivityChanged(online = false, wifi = false)
        controller.airplaneChanged(true)
        idle(6_000)
        assertNull(controller.topKind)
    }

    // ------------------------------------------------------------------ sleep timer

    @Test
    fun `the sleep timer steps through its lengths and back off`() {
        val seen = mutableListOf<Long?>()
        repeat(5) {
            controller.onSleepTimer()
            seen += controller.sleepEndsAt()?.let { (it - System.currentTimeMillis() + 30_000) / 60_000 }
        }
        assertEquals(listOf<Long?>(15, 30, 45, 60, null), seen)
    }

    @Test
    fun `the sleep timer switches itself off when it runs out`() {
        controller.onSleepTimer()
        assertNotNull(controller.sleepEndsAt())
        idle(15 * 60_000L + 1_000)
        assertNull(controller.sleepEndsAt())
    }

    // ------------------------------------------------------------------ gestures

    @Test
    fun `helper gestures go through the helper when it is there`() {
        val performed = mutableListOf<Int>()
        IslandBus.globalAction = { performed += it; true }
        controller.onGesture(GestureAction.LOCK_SCREEN)
        controller.onGesture(GestureAction.OPEN_NOTIFICATIONS)
        controller.onGesture(GestureAction.TAKE_SCREENSHOT)
        idle(500)
        assertEquals(
            listOf(
                AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
            ),
            performed,
        )
    }

    @Test
    fun `without the helper they say so instead of doing nothing`() {
        IslandBus.globalAction = null
        controller.onGesture(GestureAction.LOCK_SCREEN)
        idle(50)
        assertNotNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `open the camera asks for a camera app`() {
        controller.onGesture(GestureAction.OPEN_CAMERA)
        idle(50)
        val started = shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>())
            .nextStartedActivity
        assertEquals(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, started?.action)
    }
}
