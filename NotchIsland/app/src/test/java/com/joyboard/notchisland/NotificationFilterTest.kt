package com.joyboard.notchisland

import com.joyboard.notchisland.island.NotificationFilter
import com.joyboard.notchisland.island.NotificationKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterTest {

    private val high = 4
    private val low = 2

    private fun show(
        kind: NotificationKind = NotificationKind.PREVIEW,
        blocked: Boolean = false,
        importance: Int = high,
        passesDnd: Boolean = true,
        ambient: Boolean = false,
        showSilent: Boolean = false,
        respectDnd: Boolean = true,
    ) = NotificationFilter.shouldShow(kind, blocked, importance, passesDnd, ambient, showSilent, respectDnd)

    @Test
    fun `an ordinary audible notification shows`() = assertTrue(show())

    @Test
    fun `a silent notification is left alone by default`() {
        assertFalse(show(importance = low))
        assertFalse("ambient means the phone already tucked it away", show(ambient = true))
    }

    @Test
    fun `silent notifications show when asked for`() {
        assertTrue(show(importance = low, showSilent = true))
    }

    @Test
    fun `do not disturb silences previews and calls`() {
        assertFalse(show(passesDnd = false))
        assertFalse(show(kind = NotificationKind.CALL, passesDnd = false))
    }

    @Test
    fun `do not disturb can be overridden`() {
        assertTrue(show(passesDnd = false, respectDnd = false))
    }

    @Test
    fun `ongoing activities ignore importance and do not disturb`() {
        // Navigation and downloads are routinely posted at low importance.
        assertTrue(show(kind = NotificationKind.ONGOING, importance = low, passesDnd = false))
    }

    @Test
    fun `a blocked app is blocked whatever it is`() {
        NotificationKind.entries.forEach { assertFalse("$it", show(kind = it, blocked = true)) }
    }

    @Test
    fun `an unranked notification counts as audible`() {
        // Importance -1000 is IMPORTANCE_UNSPECIFIED: no ranking, so do not hide it.
        assertTrue(show(importance = -1000))
    }
}
