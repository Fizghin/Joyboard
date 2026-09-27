package com.joyboard.notchisland

import com.joyboard.notchisland.island.Hole
import com.joyboard.notchisland.island.HoleResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HoleResolverTest {

    private val outerCamera = Hole(0f, 18f, 20f, 20f)
    private val innerCamera = Hole(120f, 20f, 18f, 18f)

    @Test
    fun `the same screen keeps the stored hole without asking the phone`() {
        var asked = false
        val hole = HoleResolver.resolve(outerCamera, 384f, 390f) { asked = true; innerCamera }
        assertSame(outerCamera, hole)
        assertFalse(asked)
    }

    @Test
    fun `the other screen of a foldable uses that screen's own report`() {
        assertEquals(innerCamera, HoleResolver.resolve(outerCamera, 384f, 673f) { innerCamera })
    }

    @Test
    fun `the other screen with no report avoids nothing`() {
        assertNull(HoleResolver.resolve(outerCamera, 384f, 673f) { null })
    }

    @Test
    fun `an unknown original screen trusts the stored hole, as before`() {
        assertSame(outerCamera, HoleResolver.resolve(outerCamera, 0f, 673f) { innerCamera })
    }

    @Test
    fun `no stored hole stays none`() {
        assertNull(HoleResolver.resolve(null, 384f, 673f) { innerCamera })
    }
}
