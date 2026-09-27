package com.joyboard.notchisland.robo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.AudioManager
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.joyboard.notchisland.R
import com.joyboard.notchisland.data.CameraSource
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.withHole
import com.joyboard.notchisland.island.AutomationRequest
import com.joyboard.notchisland.island.BatteryState
import com.joyboard.notchisland.island.CalendarEvent
import com.joyboard.notchisland.island.Hole
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.IslandController
import com.joyboard.notchisland.island.SpeedReading
import com.joyboard.notchisland.island.IslandView
import com.joyboard.notchisland.island.NotificationAction
import com.joyboard.notchisland.island.NotificationItem
import com.joyboard.notchisland.island.Presentation
import com.joyboard.notchisland.island.Presentations
import com.joyboard.notchisland.island.Sky
import com.joyboard.notchisland.island.WeatherReport
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Every kind of live activity, compact and open, rendered one by one so the whole island can be
 * looked over at once. Output only: build/outputs/roborazzi/gallery/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class IslandGalleryTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val hole = Hole(centerX = 0f, centerY = 20f, width = 22f, height = 22f)
    private val settings = IslandSettings(iosMode = true, avoidHole = true, showFauxCamera = true)
        .withHole(hole, CameraSource.DETECTED)
        .copy(collapsedWidth = 124, collapsedHeight = 36, cornerRadius = 18, offsetY = 2,
            compactWidth = 210, mediumWidth = 300, expandedWidth = 380)
    private val presentations = Presentations(activity, accent = { Color.rgb(10, 132, 255) }, settings = { settings })

    private val listener = object : IslandView.Listener by FakeListener() {
        override fun currentWeather() = WeatherReport(21.4f, false, Sky.PARTLY_CLOUDY, 0f, emptyList())
        override fun nextAlarm() = System.currentTimeMillis() + 8 * 3_600_000L
    }

    private fun icon(res: Int) = ContextCompat.getDrawable(activity, res)

    private fun avatar(): Bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
        val c = Canvas(this)
        c.drawColor(Color.rgb(88, 86, 214))
        c.drawCircle(60f, 48f, 22f, Paint().apply { color = Color.WHITE; isAntiAlias = true })
        c.drawCircle(60f, 118f, 44f, Paint().apply { color = Color.WHITE; isAntiAlias = true })
    }

    /** A turn-left arrow, the way a maps app draws the next manoeuvre. */
    private fun arrow(): Bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
        val c = Canvas(this)
        val stroke = Paint().apply {
            color = Color.WHITE; isAntiAlias = true; style = Paint.Style.STROKE
            strokeWidth = 16f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(Path().apply {
            moveTo(78f, 110f); lineTo(78f, 62f); quadTo(78f, 42f, 58f, 42f); lineTo(36f, 42f)
        }, stroke)
        c.drawPath(Path().apply {
            moveTo(14f, 42f); lineTo(40f, 18f); lineTo(40f, 66f); close()
        }, Paint().apply { color = Color.WHITE; isAntiAlias = true })
    }

    private fun item(
        title: String, text: String, category: String? = null, ongoing: Boolean = false, progress: Int = -1,
        actions: List<String> = emptyList(), otp: String? = null,
    ) = NotificationItem(
        key = title, packageName = "com.example", appLabel = "Messages", title = title, text = text,
        smallIcon = icon(R.drawable.ic_bell), largeIcon = avatar(), appIcon = icon(R.drawable.ic_bell),
        accent = Color.rgb(52, 199, 89), whenMs = System.currentTimeMillis(), contentIntent = null,
        actions = actions.map { NotificationAction(it, null) }, ongoing = ongoing, category = category,
        progress = progress, progressMax = if (progress >= 0) 100 else 0, otp = otp,
    )

    private fun render(name: String, p: Presentation, mode: IslandMode) {
        val island = IslandView(activity, listener)
        val screen = FrameLayout(activity).apply { setBackgroundColor(Color.rgb(242, 242, 247)) }
        screen.addView(island, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = (settings.offsetY * activity.resources.displayMetrics.density).toInt()
        })
        activity.setContentView(screen)
        island.applySettings(settings)
        island.setPresentation(p)
        island.setHole(hole, hole.centerX - settings.offsetX, hole.centerY - settings.offsetY)
        island.snapToMode(mode)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        island.captureRoboImage("build/outputs/roborazzi/gallery/${name}_${mode.name.lowercase()}.png")
    }

    private fun both(name: String, p: Presentation) {
        render(name, p, IslandMode.COMPACT)
        render(name, p, IslandMode.EXPANDED)
    }

    @Test
    fun gallery() {
        val now = System.currentTimeMillis()
        both("a_notification", presentations.notification(item("Mia", "Are we still on for 7? I can bring the tickets.", actions = listOf("Reply", "Mark as read"))))
        both("b_passcode", presentations.notification(item("Bank", "Your code is 482915", otp = "482915")))
        both("c_call", presentations.call(item("Mia", "Incoming call", category = "call", actions = listOf("Decline", "Answer"))))
        both("d_download", presentations.ongoing(item("Downloading", "holiday-photos.zip · 64%", ongoing = true, progress = 64)))
        both("e_charging", presentations.charging(BatteryState(76, plugged = true, fast = true, full = false, temperatureC = 31f)))
        both("f_battery_low", presentations.batteryLow(BatteryState(12, plugged = false, fast = false, full = false)))
        both("g_timer", presentations.timer(272_000, 300_000, running = true))
        both("h_stopwatch", presentations.stopwatch(83_450, running = true, laps = listOf(41_200, 79_900)))
        both("i_volume", presentations.volume(AudioManager.STREAM_MUSIC, 9, 0.6f))
        both("j_ringer", presentations.ringer(R.drawable.ic_bell_off, "Silent"))
        both("k_unlocked", presentations.unlocked())
        both("l_privacy", presentations.privacy(R.drawable.ic_camera, "Camera in use"))
        both("m_calendar", presentations.calendar(CalendarEvent(1, "Design review", now + 12 * 60_000, now + 42 * 60_000, "Room 4"), now))
        both("n_rain", presentations.rainSoon(now + 15 * 60_000, now))
        both("o_headphones", presentations.headphones("Pixel Buds Pro", connected = true))
        both("p_focus", presentations.focus(on = true))
        both("q_automation", presentations.external(AutomationRequest("Laundry done", "The drum has stopped", 5_000, Color.rgb(48, 176, 199), R.drawable.ic_bell, false)))
        both("r_idle", presentations.idle())
        both("s_history", presentations.history(listOf(item("Mia", "See you at 7"), item("Bank", "Your code is 482915"), item("Leo", "Photos from Sunday"))))
        both("t_flashlight", presentations.flashlight())
        // The smaller card is all header: it previews the message that the open panel shows in full.
        render("u_notification", presentations.notification(item("Mia", "Are we still on for 7? I can bring the tickets.")), IslandMode.MEDIUM)
        render("v_timer", presentations.timer(272_000, 300_000, running = true), IslandMode.MEDIUM)
        both("w_microphone", presentations.privacy(R.drawable.ic_mic, "Microphone in use"))
        both("x_navigation", presentations.navigation(
            item("250 m", "Turn left onto High St", ongoing = true, actions = listOf("Exit navigation"))
                .copy(category = "navigation", largeIcon = arrow(), subText = "8 min · 2.1 km · 10:42 ETA")
        ))
        both("y_network", presentations.network(SpeedReading(2_516_582, 131_072, active = true)))
        both("z_battery_hot", presentations.batteryHot(BatteryState(64, plugged = true, fast = false, full = false, temperatureC = 46f)))
        both("za_note", presentations.note("Buy milk and bread on the way home"))
        both("zc_timer_done", presentations.timerFinished(5 * 60_000L, ringing = true))
        both("zd_offline", presentations.offline())
        both("ze_back_online", presentations.online(wifi = true))
        both("zf_airplane", presentations.airplane(on = true))
    }

    /** Two at once: the timer in front and the torch in the bubble beside it, through the real window. */
    @Test
    fun split() {
        val controller = IslandController(activity.applicationContext)
        controller.start(settings.copy(enabled = true, hapticsEnabled = false, featureMedia = false, featurePrivacy = false))
        controller.onStartTimer(5)
        controller.torchChanged(true)
        controller.onRequestMode(IslandMode.COMPACT)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        try {
            // The overlay is a window of its own, outside the activity, so it is drawn by hand.
            val root = controller.islandRootView!!
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).also { it.drawColor(Color.rgb(242, 242, 247)); root.draw(it) }
            bitmap.captureRoboImage("build/outputs/roborazzi/gallery/zb_split_compact.png")
        } finally {
            controller.stop()
        }
    }
}
