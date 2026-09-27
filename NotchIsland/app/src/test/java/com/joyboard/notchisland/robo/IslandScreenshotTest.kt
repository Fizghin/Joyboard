package com.joyboard.notchisland.robo

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import com.joyboard.notchisland.island.ActivityKind
import com.joyboard.notchisland.island.ExpandedBody
import com.joyboard.notchisland.island.Hole
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.IslandView
import com.joyboard.notchisland.island.MediaSnapshot
import com.joyboard.notchisland.island.Presentation
import com.joyboard.notchisland.island.Trailing
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Renders the real IslandView, in every size, into PNGs under build/outputs/roborazzi. Nothing is
 * compared — this exists so the island can actually be looked at, which until now it never had
 * been outside a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class IslandScreenshotTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun art(): Bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        canvas.drawColor(Color.rgb(255, 55, 95))
        canvas.drawCircle(60f, 60f, 34f, android.graphics.Paint().apply { color = Color.rgb(255, 214, 10) })
    }

    private fun mediaPresentation(): Presentation {
        val media = MediaSnapshot(
            packageName = "com.example.music", appLabel = "Music", title = "Midnight City",
            artist = "M83", album = "Hurry Up, We're Dreaming", artwork = art(), appIcon = null,
            playing = true, positionMs = 96_000, durationMs = 243_000, accent = Color.rgb(255, 55, 95),
        )
        return Presentation(
            kind = ActivityKind.MEDIA,
            leadingBitmap = media.artwork,
            leadingIcon = ContextCompat.getDrawable(activity, R.drawable.ic_music),
            trailing = Trailing.Waveform(true, media.accent),
            accent = media.accent,
            title = media.title,
            subtitle = "${media.artist} · ${media.appLabel}",
            body = ExpandedBody.Media(media),
        )
    }

    /** Draws the island on a phone-width white strip with the hole painted where it really is. */
    private fun render(name: String, mode: IslandMode, settings: IslandSettings, hole: Hole?) {
        val island = IslandView(activity, FakeListener())
        val screen = FrameLayout(activity).apply { setBackgroundColor(Color.rgb(242, 242, 247)) }
        screen.addView(island, FrameLayout.LayoutParams(1, 1, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = (settings.offsetY * activity.resources.displayMetrics.density).toInt()
        })
        activity.setContentView(screen)

        island.applySettings(settings)
        island.setPresentation(mediaPresentation())
        hole?.let { island.setHole(it, it.centerX - settings.offsetX, it.centerY - settings.offsetY) }
        island.snapToMode(mode)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        // The real camera, drawn on top the way the hardware would be.
        if (hole != null) {
            val density = activity.resources.displayMetrics.density
            screen.addView(android.view.View(activity).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.rgb(20, 20, 24))
                    setStroke((1 * density).toInt(), Color.rgb(255, 55, 95))
                }
            }, FrameLayout.LayoutParams(
                (hole.width * density).toInt(), (hole.height * density).toInt(),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                leftMargin = (hole.centerX * density).toInt()
                topMargin = ((hole.centerY - hole.height / 2) * density).toInt()
            })
            shadowOf(Looper.getMainLooper()).idle()
        }

        screen.captureRoboImage("build/outputs/roborazzi/$name.png")
        assertTrue("island never laid out", island.width > 0 && island.height > 0)
    }

    private val centred = Hole(centerX = 0f, centerY = 24f, width = 22f, height = 22f)

    private fun iosAround(hole: Hole) =
        IslandSettings(iosMode = true, showFauxCamera = false, avoidHole = true)
            .withHole(hole, CameraSource.DETECTED)
            .copy(collapsedWidth = 126, collapsedHeight = 37, cornerRadius = 19, offsetY = 5,
                compactWidth = 210, mediumWidth = 290, expandedWidth = 372)

    @Test
    fun centredCameraInEverySize() {
        val s = iosAround(centred)
        render("1_pill", IslandMode.PILL, s, centred)
        render("2_compact", IslandMode.COMPACT, s, centred)
        render("3_medium", IslandMode.MEDIUM, s, centred)
        render("4_expanded", IslandMode.EXPANDED, s, centred)
    }

    @Test
    fun cameraOverTheLeadingIcon() {
        // The hole sits where the album art would, so the art must move past it.
        val offCentre = Hole(centerX = -78f, centerY = 24f, width = 22f, height = 22f)
        val s = iosAround(offCentre)
        render("5_compact_hole_left_avoided", IslandMode.COMPACT, s, offCentre)
        render("6_compact_hole_left_ignored", IslandMode.COMPACT, s.copy(avoidHole = false), offCentre)
    }
}
