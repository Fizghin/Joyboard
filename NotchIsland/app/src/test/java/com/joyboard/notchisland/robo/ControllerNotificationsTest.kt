package com.joyboard.notchisland.robo

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.ActivityKind
import com.joyboard.notchisland.island.IslandController
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.NotificationItem
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

/** Which notifications reach the island, and how often. Each of these was wrong before 2.9. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class ControllerNotificationsTest {

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

    private fun item(key: String, text: String = "Body", ongoing: Boolean = false) = NotificationItem(
        key = key, packageName = "com.example.app", appLabel = "Example", title = "Title", text = text,
        smallIcon = null, largeIcon = null, appIcon = null, accent = 0xFF3B82F6.toInt(),
        whenMs = System.currentTimeMillis(), contentIntent = null, actions = emptyList(), ongoing = ongoing,
    )

    @Test
    fun `a music player's own notification leaves now playing to the media session`() {
        controller.onNotification(item("player", ongoing = true).copy(media = true, chronometer = true))
        idle(50)
        assertNull(controller.topKind)
    }

    @Test
    fun `an app saying it is running does not take the island`() {
        controller.onNotification(item("vpn", "Connected", ongoing = true))
        idle(50)
        assertNull(controller.topKind)
    }

    @Test
    fun `live ongoing notifications still do`() {
        controller.onNotification(item("recording", "00:12", ongoing = true).copy(chronometer = true))
        idle(50)
        assertEquals(ActivityKind.ONGOING, controller.topKind)
    }

    @Test
    fun `android 16 live updates count as live`() {
        controller.onNotification(item("delivery", "Arriving at 7:40", ongoing = true).copy(promoted = true))
        idle(50)
        assertEquals(ActivityKind.ONGOING, controller.topKind)
    }

    @Test
    fun `a finished download leaves when its notification stops being ongoing`() {
        controller.onNotification(item("dl", "40%", ongoing = true).copy(progress = 40, progressMax = 100))
        idle(50)
        assertEquals(ActivityKind.ONGOING, controller.topKind)
        controller.onNotification(item("dl", "Download complete"))
        idle(settings.notificationDurationMs + 200L)
        assertNull(controller.topKind)
    }

    @Test
    fun `a notification updated in place is announced once`() {
        controller.onNotification(item("chat", "Hi"))
        controller.onNotification(item("chat", "Hi"))
        controller.onNotification(item("sync", "1 of 10").copy(alertOnce = true))
        controller.onNotification(item("sync", "2 of 10").copy(alertOnce = true))
        idle(50)
        assertEquals(2, controller.arrivals)
        controller.onNotification(item("chat", "Are you there?"))
        assertEquals("new words are news", 3, controller.arrivals)
    }

    @Test
    fun `a ringing call opens the island, once`() {
        val call = item("call", "Incoming call", ongoing = true).copy(category = "call")
        controller.onNotification(call)
        idle(50)
        assertEquals(IslandMode.EXPANDED, controller.currentMode)
        controller.onNotification(call)
        controller.onNotification(call)
        assertEquals(1, controller.arrivals)
    }
}
