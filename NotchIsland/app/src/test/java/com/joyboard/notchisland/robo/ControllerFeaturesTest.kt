package com.joyboard.notchisland.robo

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.ActivityKind
import com.joyboard.notchisland.island.BatteryState
import com.joyboard.notchisland.island.IslandController
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.NotificationItem
import com.joyboard.notchisland.island.SpeedReading
import com.joyboard.notchisland.service.IslandBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The 2.8 activities, through the whole controller: two at once, directions, speed, heat, notes. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class ControllerFeaturesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var controller: IslandController

    private val settings = IslandSettings(
        enabled = true,
        compactRestSeconds = 10,
        stayCompactForActivities = false,
        hapticsEnabled = false,
        featureMedia = false,
        featurePrivacy = false,
    )

    @Before
    fun setUp() {
        controller = IslandController(context)
        controller.start(settings)
        idle(500)
    }

    @After
    fun tearDown() {
        IslandBus.clearHelperState()
        IslandBus.controller = null
        controller.stop()
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun item(
        key: String,
        title: String = "Title",
        text: String = "Body",
        ongoing: Boolean = false,
        category: String? = null,
        packageName: String = "com.example.app",
        subText: String = "",
    ) = NotificationItem(
        key = key, packageName = packageName, appLabel = "Example", title = title, text = text,
        smallIcon = null, largeIcon = null, appIcon = null, accent = 0xFF3B82F6.toInt(),
        whenMs = System.currentTimeMillis(), contentIntent = null, actions = emptyList(),
        ongoing = ongoing, category = category, progress = if (ongoing) 40 else -1,
        progressMax = if (ongoing) 100 else 0, subText = subText,
    )

    // ------------------------------------------------------------------ two at once

    @Test
    fun `a second long-lived activity waits in the bubble, and a tap brings it forward`() {
        controller.torchChanged(true)
        controller.onNotification(item("download", ongoing = true))
        idle(100)
        assertEquals(IslandMode.COMPACT, controller.currentMode)
        assertEquals(ActivityKind.FLASHLIGHT, controller.topKind)
        assertEquals(ActivityKind.ONGOING, controller.secondaryShown)

        controller.tapSecondary()
        idle(100)
        assertEquals(ActivityKind.ONGOING, controller.topKind)
        assertEquals(ActivityKind.FLASHLIGHT, controller.secondaryShown)
    }

    @Test
    fun `something passing through takes over, then the chosen one comes back`() {
        controller.torchChanged(true)
        controller.onNotification(item("download", ongoing = true))
        idle(100)
        controller.tapSecondary()
        controller.onNotification(item("message"))
        idle(100)
        assertEquals(ActivityKind.NOTIFICATION, controller.topKind)
        assertNull("no bubble beside a passing notification", controller.secondaryShown)
        idle(settings.notificationDurationMs + 200L)
        assertEquals(ActivityKind.ONGOING, controller.topKind)
    }

    @Test
    fun `a call wins over the choice`() {
        controller.torchChanged(true)
        controller.onNotification(item("download", ongoing = true))
        idle(100)
        controller.tapSecondary()
        controller.onNotification(item("call", ongoing = true, category = "call"))
        idle(100)
        assertEquals(ActivityKind.CALL, controller.topKind)
    }

    @Test
    fun `with the setting off there is no bubble`() {
        controller.applySettings(settings.copy(splitIsland = false))
        controller.torchChanged(true)
        controller.onNotification(item("download", ongoing = true))
        idle(100)
        assertEquals(IslandMode.COMPACT, controller.currentMode)
        assertNull(controller.secondaryShown)
    }

    @Test
    fun `the bubble goes when the island rests or opens`() {
        controller.torchChanged(true)
        controller.onNotification(item("download", ongoing = true))
        idle(100)
        controller.onRequestMode(IslandMode.EXPANDED)
        idle(100)
        assertNull(controller.secondaryShown)
        controller.onRequestMode(IslandMode.PILL)
        idle(settings.compactRestSeconds * 1000L + 500)
        assertEquals(IslandMode.PILL, controller.currentMode)
        assertNull(controller.secondaryShown)
    }

    // ------------------------------------------------------------------ navigation

    @Test
    fun `a maps notification becomes directions, and leaves with its notification`() {
        controller.onNotification(
            item("route", title = "250 m", text = "Turn left onto High St", ongoing = true,
                category = "navigation", subText = "10:42 ETA")
        )
        idle(100)
        assertEquals(ActivityKind.NAVIGATION, controller.topKind)
        controller.onNotificationRemoved("route")
        idle(100)
        assertNull(controller.topKind)
    }

    @Test
    fun `with directions off a route is an ordinary ongoing notification`() {
        controller.applySettings(settings.copy(featureNavigation = false))
        controller.onNotification(item("route", ongoing = true, category = "navigation"))
        idle(100)
        assertEquals(ActivityKind.ONGOING, controller.topKind)
    }

    // ------------------------------------------------------------------ network speed

    @Test
    fun `a fast transfer shows while it lasts, and only when switched on`() {
        controller.networkSpeed(SpeedReading(2_000_000, 10_000, active = true))
        idle(50)
        assertNull("off by default", controller.topKind)

        controller.applySettings(settings.copy(featureNetworkSpeed = true))
        controller.networkSpeed(SpeedReading(2_000_000, 10_000, active = true))
        idle(50)
        assertEquals(ActivityKind.NETWORK, controller.topKind)
        controller.networkSpeed(SpeedReading(1_000, 0, active = false))
        idle(50)
        assertNull(controller.topKind)
    }

    // ------------------------------------------------------------------ battery heat

    @Test
    fun `a hot battery warns once until it cools`() {
        val hot = BatteryState(60, plugged = true, fast = false, full = false, temperatureC = 46f)
        controller.batteryChanged(hot, plugChanged = false)
        idle(50)
        assertEquals(ActivityKind.BATTERY_HOT, controller.topKind)
        idle(7_000)
        controller.batteryChanged(hot.copy(temperatureC = 46.5f), plugChanged = false)
        idle(50)
        assertNull("still hot is not news", controller.topKind)
        controller.batteryChanged(hot.copy(temperatureC = 40f), plugChanged = false)
        controller.batteryChanged(hot.copy(temperatureC = 45.5f), plugChanged = false)
        idle(50)
        assertEquals(ActivityKind.BATTERY_HOT, controller.topKind)
    }

    // ------------------------------------------------------------------ pinned note

    @Test
    fun `a pinned note stays until ticked off, and stays off`() {
        val noted = settings.copy(pinnedNote = "Buy milk")
        controller.applySettings(noted)
        idle(50)
        assertEquals(ActivityKind.NOTE, controller.topKind)

        controller.onOpenPresentationTarget()
        idle(50)
        assertNull(controller.topKind)
        // The setting still holds it until the write lands; that must not bring it back.
        controller.applySettings(noted)
        idle(50)
        assertNull(controller.topKind)
        // A different note is a new one.
        controller.applySettings(settings.copy(pinnedNote = "Call Leo"))
        idle(50)
        assertEquals(ActivityKind.NOTE, controller.topKind)
    }

    @Test
    fun `music goes in front of a note, which waits beside it`() {
        controller.applySettings(settings.copy(pinnedNote = "Buy milk"))
        controller.onNotification(item("download", ongoing = true))
        idle(100)
        assertEquals(ActivityKind.ONGOING, controller.topKind)
        assertEquals(ActivityKind.NOTE, controller.secondaryShown)
    }

    // ------------------------------------------------------------------ quick timers

    @Test
    fun `the quick panel's timer buttons start a countdown`() {
        controller.onStartTimer(5)
        idle(1_100)
        assertEquals(ActivityKind.TIMER, controller.topKind)
    }
}
