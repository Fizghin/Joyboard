package com.joyboard.notchisland

import com.joyboard.notchisland.island.SeenNotifications
import org.junit.Assert.assertEquals
import org.junit.Test

class SeenNotificationsTest {

    @Test
    fun `a new notification is announced, the same one re-posted is not`() {
        val seen = SeenNotifications()
        assertEquals(true, seen.shouldAnnounce("a", "Mia", "Hi", alertOnce = false))
        assertEquals(false, seen.shouldAnnounce("a", "Mia", "Hi", alertOnce = false))
    }

    @Test
    fun `new words are announced unless the app asked to alert once`() {
        val seen = SeenNotifications()
        seen.shouldAnnounce("a", "Mia", "Hi", alertOnce = false)
        assertEquals(true, seen.shouldAnnounce("a", "Mia", "Are you there?", alertOnce = false))
        seen.shouldAnnounce("b", "Sync", "1 of 10", alertOnce = true)
        assertEquals(false, seen.shouldAnnounce("b", "Sync", "2 of 10", alertOnce = true))
    }

    @Test
    fun `a removed notification is new when it comes back`() {
        val seen = SeenNotifications()
        seen.shouldAnnounce("a", "Mia", "Hi", alertOnce = false)
        seen.forget("a")
        assertEquals(true, seen.shouldAnnounce("a", "Mia", "Hi", alertOnce = false))
    }

    @Test
    fun `it remembers only so many`() {
        val seen = SeenNotifications(limit = 2)
        seen.shouldAnnounce("a", "", "1", alertOnce = false)
        seen.shouldAnnounce("b", "", "2", alertOnce = false)
        seen.shouldAnnounce("c", "", "3", alertOnce = false)
        assertEquals("the oldest was forgotten", true, seen.shouldAnnounce("a", "", "1", alertOnce = false))
    }
}
