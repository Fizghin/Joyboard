package com.joyboard.notchisland.robo

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.TapExpansion
import com.joyboard.notchisland.island.IslandController
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.NotificationItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The whole controller — overlay window, activity queue, timers — under Robolectric's clock.
 * These pin down the "island never goes back to its resting size" bug, both halves of it.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class ControllerRestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var controller: IslandController

    private val settings = IslandSettings(
        enabled = true,
        autoCollapseSeconds = 6,
        compactRestSeconds = 10,
        stayCompactForActivities = false,
        tapExpansion = TapExpansion.STEP,
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
    fun tearDown() = controller.stop()

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun notification(key: String, ongoing: Boolean = false, progress: Int = -1) = NotificationItem(
        key = key, packageName = "com.example.app", appLabel = "Example", title = "Title $key",
        text = "Body", smallIcon = null, largeIcon = null, appIcon = null, accent = 0xFF3B82F6.toInt(),
        whenMs = System.currentTimeMillis(), contentIntent = null, actions = emptyList(),
        ongoing = ongoing, progress = progress, progressMax = if (progress >= 0) 100 else 0,
    )

    @Test
    fun `the island attaches and rests as a pill`() {
        assertEquals(IslandMode.PILL, controller.currentMode)
    }

    @Test
    fun `taps walk up the sizes and the island closes again on its own`() {
        repeat(3) {
            controller.onGesture(GestureAction.EXPAND)
            idle(100)
        }
        assertEquals(IslandMode.EXPANDED, controller.currentMode)
        idle(6_500)
        assertEquals(IslandMode.PILL, controller.currentMode)
    }

    @Test
    fun `a chatty ongoing notification cannot hold the opened island open`() {
        // The reported bug: every refresh re-armed the collapse countdown, so a download ticking
        // its progress kept the island open indefinitely.
        controller.onNotification(notification("download", ongoing = true, progress = 0))
        controller.onGesture(GestureAction.EXPAND_FULL)
        idle(100)
        assertEquals(IslandMode.EXPANDED, controller.currentMode)

        for (step in 1..16) {
            controller.onNotification(notification("download", ongoing = true, progress = step * 5))
            idle(500)
        }
        // Eight seconds of progress ticks later the six-second collapse has still happened. The
        // download began inside the last ten seconds, so it keeps its compact readout for now...
        assertEquals(IslandMode.COMPACT, controller.currentMode)

        for (step in 17..24) {
            controller.onNotification(notification("download", ongoing = true, progress = step * 4))
            idle(500)
        }
        // ...and once that window passes, ticks or not, it settles all the way back.
        assertEquals(IslandMode.PILL, controller.currentMode)
    }

    @Test
    fun `a long-running activity settles back to the pill`() {
        // The other half: any live activity used to pin the island at compact forever.
        controller.onNotification(notification("nav", ongoing = true, progress = 10))
        idle(200)
        assertEquals(IslandMode.COMPACT, controller.currentMode)

        for (step in 1..24) {
            controller.onNotification(notification("nav", ongoing = true, progress = 10 + step))
            idle(500)
        }
        assertEquals(IslandMode.PILL, controller.currentMode)
    }

    @Test
    fun `something genuinely new brings the readout back`() {
        controller.onNotification(notification("nav", ongoing = true, progress = 10))
        idle(12_000)
        assertEquals(IslandMode.PILL, controller.currentMode)

        controller.onNotification(notification("message"))
        idle(200)
        assertEquals(IslandMode.COMPACT, controller.currentMode)
    }

    @Test
    fun `stay-compact keeps the readout for as long as the activity lives`() {
        controller.applySettings(settings.copy(stayCompactForActivities = true))
        controller.onNotification(notification("nav", ongoing = true, progress = 10))
        idle(30_000)
        assertEquals(IslandMode.COMPACT, controller.currentMode)
    }
}
