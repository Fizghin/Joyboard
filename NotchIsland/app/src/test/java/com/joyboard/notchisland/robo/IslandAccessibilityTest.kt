package com.joyboard.notchisland.robo

import android.app.Activity
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.island.ActivityKind
import com.joyboard.notchisland.island.ExpandedBody
import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.IslandView
import com.joyboard.notchisland.island.MediaSnapshot
import com.joyboard.notchisland.island.Presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** A screen reader cannot swipe or double-tap the island, so it has to offer the same things. */
@RunWith(AndroidJUnit4::class)
class IslandAccessibilityTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val listener = FakeListener()
    private val island = IslandView(activity, listener).also {
        activity.setContentView(FrameLayout(activity).apply { addView(it, FrameLayout.LayoutParams(300, 100)) })
        it.applySettings(IslandSettings())
    }

    private val media = Presentation(
        kind = ActivityKind.MEDIA, title = "Midnight City", subtitle = "M83 · Music",
        body = ExpandedBody.Media(
            MediaSnapshot("p", "Music", "Midnight City", "M83", null, null, null, true, 0, 1_000, 0)
        ),
    )

    private fun actionLabels(): List<String> {
        val info = AccessibilityNodeInfo()
        island.onInitializeAccessibilityNodeInfo(info)
        return AccessibilityNodeInfoCompat.wrap(info).actionList.mapNotNull { it.label?.toString() }
    }

    private fun perform(label: String) {
        val info = AccessibilityNodeInfo()
        island.onInitializeAccessibilityNodeInfo(info)
        val id = AccessibilityNodeInfoCompat.wrap(info).actionList.first { it.label == label }.id
        island.performAccessibilityAction(id, null)
    }

    @Test
    fun `it says what it is showing`() {
        island.setPresentation(media)
        assertEquals("Midnight City, M83 · Music", island.contentDescription)
    }

    @Test
    fun `a resting island can be opened`() {
        island.setPresentation(media)
        island.snapToMode(IslandMode.PILL)
        assertTrue("Open" in actionLabels())
        assertFalse("Close" in actionLabels())
        perform("Open")
        assertEquals(GestureAction.EXPAND_FULL, listener.gestures.last())
    }

    @Test
    fun `an open island can be closed`() {
        island.setPresentation(media)
        island.snapToMode(IslandMode.EXPANDED)
        assertTrue("Close" in actionLabels())
        perform("Close")
        assertEquals(GestureAction.COLLAPSE, listener.gestures.last())
    }

    @Test
    fun `music offers transport without any swiping`() {
        island.setPresentation(media)
        island.snapToMode(IslandMode.COMPACT)
        assertTrue(actionLabels().containsAll(listOf("Play or pause", "Next track", "Previous track")))
        perform("Next track")
        assertEquals(GestureAction.MEDIA_NEXT, listener.gestures.last())
    }

    @Test
    fun `activating it is the same as a tap`() {
        island.setPresentation(media)
        island.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(IslandSettings().tapAction, listener.gestures.last())
    }

    @Test
    fun `actions do not pile up as the island changes`() {
        island.setPresentation(media)
        repeat(5) { island.snapToMode(IslandMode.COMPACT); island.snapToMode(IslandMode.EXPANDED) }
        assertEquals(actionLabels().size, actionLabels().toSet().size)
    }
}
