package com.joyboard.notchisland

import com.joyboard.notchisland.data.ColorSource
import com.joyboard.notchisland.data.GestureAction
import com.joyboard.notchisland.data.IslandSettings
import com.joyboard.notchisland.data.PositionMode
import com.joyboard.notchisland.data.SettingsCodec
import com.joyboard.notchisland.data.ThemeMode
import com.joyboard.notchisland.data.isQuietAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun `a customised profile survives a round trip`() {
        val original = IslandSettings(
            collapsedWidth = 143,
            cornerRadius = 7,
            positionMode = PositionMode.OVERLAP_STATUS_BAR,
            touchStripHeight = 31,
            accentSource = ColorSource.ARTWORK,
            backgroundSource = ColorSource.MATERIAL_YOU,
            opacity = 0.65f,
            animationSpeed = 1.75f,
            themeMode = ThemeMode.DARK,
            longPressAction = GestureAction.TOGGLE_TORCH,
            blockedPackages = setOf("com.a", "com.b"),
            autoExpandPackages = setOf("com.c"),
            quietHoursEnabled = true,
            quietStartMinutes = 90,
            quietEndMinutes = 400,
        )
        val restored = SettingsCodec.fromJson(SettingsCodec.toJson(original))

        assertEquals(143, restored.collapsedWidth)
        assertEquals(PositionMode.OVERLAP_STATUS_BAR, restored.positionMode)
        assertEquals(31, restored.touchStripHeight)
        assertEquals(ColorSource.ARTWORK, restored.accentSource)
        assertEquals(ColorSource.MATERIAL_YOU, restored.backgroundSource)
        assertEquals(0.65f, restored.opacity, 0.001f)
        assertEquals(1.75f, restored.animationSpeed, 0.001f)
        assertEquals(GestureAction.TOGGLE_TORCH, restored.longPressAction)
        assertEquals(setOf("com.a", "com.b"), restored.blockedPackages)
        assertEquals(setOf("com.c"), restored.autoExpandPackages)
        assertTrue(restored.quietHoursEnabled)
        assertEquals(400, restored.quietEndMinutes)
    }

    /**
     * Changes every setting away from its default, by type, and checks the backup brings each
     * one back — so a setting added without a line in the codec fails here instead of silently
     * going missing from people's backups.
     */
    @Test
    fun `every setting survives a round trip except per-device state`() {
        val defaults = IslandSettings()
        val components = IslandSettings::class.java.methods
            .filter { it.name.matches(Regex("component\\d+")) }
            .sortedBy { it.name.removePrefix("component").toInt() }
        val changed = components.map { changedValue(it.invoke(defaults), it.returnType) }
        val constructor = IslandSettings::class.java.constructors
            .first { it.parameterCount == components.size }
        val original = constructor.newInstance(*changed.toTypedArray()) as IslandSettings

        val restored = SettingsCodec.fromJson(SettingsCodec.toJson(original))

        // Fields are declared in constructor order, which is also componentN order.
        val names = IslandSettings::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }
        assertEquals(components.size, names.size)
        val lost = components.indices
            .filter { i -> components[i].invoke(restored) != changed[i] }
            .map { names[it] }
            .toSet()
        // These describe this phone and its update state, not a look, so they stay behind.
        assertEquals(setOf("enabled", "lastUpdateCheck", "skippedVersion"), lost)
    }

    private fun changedValue(value: Any?, type: Class<*>): Any? = when {
        type == java.lang.Boolean.TYPE -> !(value as Boolean)
        type == Integer.TYPE -> (value as Int) + 3
        type == java.lang.Float.TYPE -> (value as Float) + 0.25f
        type == java.lang.Long.TYPE -> (value as Long) + 7L
        type == String::class.java -> "changed-${value.hashCode()}"
        Set::class.java.isAssignableFrom(type) -> setOf("com.changed.one", "com.changed.two")
        type.isEnum -> type.enumConstants.first { it != value }
        else -> error("no changed value for $type")
    }

    @Test
    fun `a backup from an older build keeps current defaults for missing keys`() {
        val restored = SettingsCodec.fromJson("""{"format":1,"collapsedWidth":200}""")
        assertEquals(200, restored.collapsedWidth)
        assertEquals(IslandSettings().cornerRadius, restored.cornerRadius)
        assertEquals(IslandSettings().accentSource, restored.accentSource)
    }

    @Test
    fun `an unknown enum value falls back instead of throwing`() {
        val restored = SettingsCodec.fromJson("""{"positionMode":"FROM_THE_FUTURE"}""")
        assertEquals(IslandSettings().positionMode, restored.positionMode)
    }

    @Test
    fun `quiet hours inside a normal window`() {
        val s = IslandSettings(quietHoursEnabled = true, quietStartMinutes = 540, quietEndMinutes = 1020)
        assertFalse(s.isQuietAt(9 * 60 - 1))
        assertTrue(s.isQuietAt(9 * 60))
        assertTrue(s.isQuietAt(12 * 60))
        assertFalse(s.isQuietAt(17 * 60))
    }

    @Test
    fun `quiet hours wrapping over midnight`() {
        val s = IslandSettings(
            quietHoursEnabled = true,
            quietStartMinutes = 23 * 60,
            quietEndMinutes = 7 * 60,
        )
        assertTrue(s.isQuietAt(23 * 60 + 30))
        assertTrue(s.isQuietAt(2 * 60))
        assertTrue(s.isQuietAt(0))
        assertFalse(s.isQuietAt(7 * 60))
        assertFalse(s.isQuietAt(15 * 60))
    }

    @Test
    fun `quiet hours off means never quiet`() {
        val s = IslandSettings(quietStartMinutes = 0, quietEndMinutes = 1439)
        assertFalse(s.isQuietAt(600))
    }
}
