package com.joyboard.notchisland.robo

import android.app.Activity
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.IslandView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** The ripple, the arrival glow and the switch that turns them off. */
@RunWith(AndroidJUnit4::class)
class IslandEffectsTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val island = IslandView(activity, FakeListener()).also {
        activity.setContentView(FrameLayout(activity).apply { addView(it, FrameLayout.LayoutParams(300, 100)) })
        it.applySettings(IslandSettings())
        it.snapToMode(IslandMode.COMPACT)
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun touch(action: Int, x: Float = 150f, y: Float = 50f) {
        val now = SystemClock.uptimeMillis()
        island.onTouchEvent(MotionEvent.obtain(now, now, action, x, y, 0))
    }

    @Test
    fun `the ripple is the island's foreground only while effects are on`() {
        assertTrue(island.foreground is RippleDrawable)
        island.applySettings(IslandSettings(effects = false))
        assertNull(island.foreground)
    }

    @Test
    fun `an arrival starts the glow, and it ends fully faded`() {
        island.announce(0xFF34C759.toInt())
        assertEquals(1, island.glowsStarted)
        idle(1_200)
        assertEquals(0f, island.glowForTest, 0f)
    }

    @Test
    fun `the glow rises fast, peaks, and fades to nothing`() {
        assertEquals(0f, IslandView.glowCurve(0f), 0f)
        assertEquals(1f, IslandView.glowCurve(0.2f), 1e-4f)
        assertEquals(0.5f, IslandView.glowCurve(0.6f), 1e-4f)
        assertEquals(0f, IslandView.glowCurve(1f), 1e-4f)
        for (i in 0..100) assertTrue(IslandView.glowCurve(i / 100f) in 0f..1f)
    }

    @Test
    fun `with effects off an arrival changes nothing`() {
        island.applySettings(IslandSettings(effects = false))
        island.announce(0xFF34C759.toInt())
        assertEquals(0, island.glowsStarted)
        assertEquals(1f, island.scaleX, 0f)
    }

    @Test
    fun `a press shows the ripple and letting go or swiping ends it`() {
        touch(MotionEvent.ACTION_DOWN)
        assertTrue(island.isPressed)
        touch(MotionEvent.ACTION_UP)
        assertFalse(island.isPressed)

        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, x = 150f, y = 50f + 200f)
        assertFalse("a swipe is not a press", island.isPressed)
        touch(MotionEvent.ACTION_CANCEL)
    }

    @Test
    fun `the island hangs from its top edge, so it scales from there`() {
        island.layout(0, 0, 300, 100)
        assertEquals(0f, island.pivotY, 0f)
    }
}
