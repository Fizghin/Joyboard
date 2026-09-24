package com.joyboard.notchisland

import com.joyboard.notchisland.data.IslandPreset
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.island.ExpansionStepper
import com.joyboard.notchisland.island.IslandMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionStepperTest {

    @Test
    fun `a tap walks the island up one size at a time`() {
        assertEquals(IslandMode.COMPACT, ExpansionStepper.next(IslandMode.PILL))
        assertEquals(IslandMode.MEDIUM, ExpansionStepper.next(IslandMode.COMPACT))
        assertEquals(IslandMode.EXPANDED, ExpansionStepper.next(IslandMode.MEDIUM))
    }

    @Test
    fun `a tap on the fully open island sends it back to rest`() {
        assertNull(ExpansionStepper.next(IslandMode.EXPANDED))
    }

    @Test
    fun `a hidden island still opens to the preview`() {
        assertEquals(IslandMode.COMPACT, ExpansionStepper.next(IslandMode.HIDDEN))
    }

    @Test
    fun `three taps from rest reach the full panel`() {
        var mode: IslandMode? = IslandMode.PILL
        repeat(3) { mode = ExpansionStepper.next(mode!!) }
        assertEquals(IslandMode.EXPANDED, mode)
    }

    @Test
    fun `the sizes are ordered, which the grow-or-shrink animation relies on`() {
        val order = listOf(
            IslandMode.HIDDEN, IslandMode.PILL, IslandMode.COMPACT,
            IslandMode.MEDIUM, IslandMode.EXPANDED,
        )
        assertEquals(order, order.sortedBy { it.ordinal })
    }
}

class IslandPresetTest {

    @Test
    fun `the iPhone preset carries the real housing measurements`() {
        val applied = IslandPreset.IPHONE_14_PRO.applyTo(IslandSettings())
        assertEquals(126, applied.collapsedWidth)
        assertEquals(37, applied.collapsedHeight)
        assertEquals(11, applied.offsetY)
        assertEquals(IslandPreset.IPHONE_14_PRO, applied.preset)
    }

    @Test
    fun `a preset anchors over the status bar and centres the island`() {
        val applied = IslandPreset.IPHONE_16_PRO.applyTo(IslandSettings(offsetX = 40))
        assertEquals(PositionMode.OVERLAP_STATUS_BAR, applied.positionMode)
        assertEquals(0, applied.offsetX)
    }

    @Test
    fun `the medium card sits between compact and expanded`() {
        IslandPreset.entries.forEach { preset ->
            val applied = preset.applyTo(IslandSettings())
            assertTrue(
                "${preset.name} medium width should be between the other two",
                applied.mediumWidth in applied.compactWidth..applied.expandedWidth
            )
        }
    }

    @Test
    fun `a preset leaves unrelated settings alone`() {
        val before = IslandSettings(hapticStrength = 3, autoCollapseSeconds = 12)
        val after = IslandPreset.IPHONE_14_PRO_MAX.applyTo(before)
        assertEquals(3, after.hapticStrength)
        assertEquals(12, after.autoCollapseSeconds)
    }

    @Test
    fun `apple presets are flagged and the android one is not`() {
        assertTrue(IslandPreset.IPHONE_14_PRO.isApple)
        assertTrue(!IslandPreset.ANDROID_PUNCH_HOLE.isApple)
        assertTrue(!IslandPreset.CUSTOM.isApple)
    }
}
