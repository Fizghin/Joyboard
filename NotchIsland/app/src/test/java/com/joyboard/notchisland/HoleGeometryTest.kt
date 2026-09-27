package com.joyboard.notchisland

import com.joyboard.notchisland.data.CameraSource
import com.joyboard.notchisland.data.DevicePresets
import com.joyboard.notchisland.data.HoleAnchor
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.data.SettingsCodec
import com.joyboard.notchisland.data.fittedTo
import com.joyboard.notchisland.data.hole
import com.joyboard.notchisland.island.Clearance
import com.joyboard.notchisland.island.Hole
import com.joyboard.notchisland.island.HoleGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleGeometryTest {

    private val punch = Hole(centerX = 0f, centerY = 18f, width = 20f, height = 20f)

    private fun row(dx: Float, islandWidth: Float = 190f, hole: Hole = punch, dy: Float = 18f, trailing: Float = 30f) =
        HoleGeometry.rowClearance(
            HoleGeometry.locate(dx, dy, hole, islandWidth), islandWidth, 34f,
            basePadding = 12f, leadingWidth = 18f, trailingWidth = trailing, margin = 6f,
        )

    @Test
    fun `a centred camera sits in the gap and needs nothing`() {
        assertEquals(Clearance.NONE, row(dx = 0f))
    }

    @Test
    fun `a camera over the leading icon pushes it past the hole`() {
        // Island 190 wide, hole centred 75 dp left of its middle: spans 10..30.
        val c = row(dx = -75f)
        assertEquals(30f + 6f - 12f, c.start, 0.01f)
        assertEquals(0f, c.end, 0.01f)
    }

    @Test
    fun `a camera over the trailing readout pushes it the other way`() {
        val c = row(dx = 70f) // hole spans 155..175
        assertEquals(0f, c.start, 0.01f)
        assertEquals(190f - 155f + 6f - 12f, c.end, 0.01f)
    }

    @Test
    fun `a camera above the island is not the island's problem`() {
        // Below-the-status-bar anchor: the hole ends before the island begins.
        assertEquals(Clearance.NONE, row(dx = -75f, dy = -30f))
    }

    @Test
    fun `a camera wider than the gap pushes only the side its centre is on`() {
        val notch = Hole(0f, 18f, 170f, 20f)
        val c = row(dx = -10f, hole = notch)
        assertTrue(c.start > 0f)
        assertEquals(0f, c.end, 0.01f)
    }

    @Test
    fun `the open panel starts below the camera`() {
        val local = HoleGeometry.locate(0f, 18f, punch, 330f)
        val c = HoleGeometry.panelClearance(local, 330f, 10_000f, basePaddingTop = 14f, margin = 6f)
        assertEquals(28f + 6f - 14f, c.top, 0.01f)
    }

    @Test
    fun `a punch hole is round and a pill cutout is not`() {
        assertTrue(punch.isRound)
        assertFalse(Hole(0f, 20f, 62f, 22f).isRound)
    }
}

class DevicePresetTest {

    private val screen = 412f

    @Test
    fun `the three asked-for phones are known`() {
        listOf("pixel6", "s23fe", "honorx9b").forEach { assertNotNull("missing $it", DevicePresets.byId(it)) }
    }

    @Test
    fun `model codes pick the right phone`() {
        assertEquals("s23fe", DevicePresets.match("SM-S711B")?.id)
        assertEquals("s23fe", DevicePresets.match("SM-S711U1")?.id)
        assertEquals("honorx9b", DevicePresets.match("ALI-NX1")?.id)
        assertNull(DevicePresets.match("Some Unknown Phone"))
    }

    @Test
    fun `an exact name is not claimed by a shorter prefix`() {
        assertEquals("pixel6", DevicePresets.match("Pixel 6")?.id)
        assertEquals("pixel6pro", DevicePresets.match("Pixel 6 Pro")?.id)
        assertEquals("pixel9proxl", DevicePresets.match("Pixel 9 Pro XL")?.id)
    }

    @Test
    fun `ids are unique`() {
        val ids = DevicePresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `corner cameras are placed from their edge whatever the screen width`() {
        val left = DevicePresets.all.first { it.anchor == HoleAnchor.LEFT }
        assertEquals(-(screen / 2 - left.inset), left.hole(screen).centerX, 0.01f)
        assertEquals(-(360f / 2 - left.inset), left.hole(360f).centerX, 0.01f)
    }

    @Test
    fun `a centred phone gets the island wrapped around its camera`() {
        val applied = DevicePresets.byId("s23fe")!!.applyTo(IslandSettings(), screen)
        val hole = applied.hole!!
        assertEquals(PositionMode.OVERLAP_STATUS_BAR, applied.positionMode)
        assertEquals(0, applied.offsetX)
        // The island's vertical middle lands on the camera.
        assertEquals(hole.centerY, applied.offsetY + applied.collapsedHeight / 2f, 1f)
        assertTrue(applied.collapsedHeight > hole.height)
        assertEquals(CameraSource.PRESET, applied.cameraSource)
        assertEquals("s23fe", applied.devicePresetId)
    }

    @Test
    fun `a corner camera leaves the island centred`() {
        val before = IslandSettings(offsetX = 0, collapsedWidth = 140)
        val applied = DevicePresets.byId("oneplus12")!!.applyTo(before, screen)
        assertEquals(0, applied.offsetX)
        assertEquals(140, applied.collapsedWidth)
        assertTrue(applied.hole!!.centerX < -100f)
    }

    @Test
    fun `fitting to a detected hole records where it came from`() {
        val applied = IslandSettings().fittedTo(Hole(3f, 22f, 24f, 24f), CameraSource.DETECTED)
        assertEquals(CameraSource.DETECTED, applied.cameraSource)
        assertEquals(3, applied.offsetX)
    }

    @Test
    fun `the camera survives a backup round trip`() {
        val original = IslandSettings().fittedTo(Hole(-150f, 21.5f, 23f, 23f), CameraSource.MANUAL)
            .copy(avoidHole = false, devicePresetId = "generic_left")
        val restored = SettingsCodec.fromJson(SettingsCodec.toJson(original))
        assertEquals(original.hole, restored.hole)
        assertEquals(CameraSource.MANUAL, restored.cameraSource)
        assertEquals(false, restored.avoidHole)
        assertEquals("generic_left", restored.devicePresetId)
    }
}
