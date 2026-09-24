package com.joyboard.notchisland

import com.joyboard.notchisland.island.ActivityKind
import com.joyboard.notchisland.island.ActivityQueue
import com.joyboard.notchisland.island.LiveActivity
import com.joyboard.notchisland.island.Presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityQueueTest {

    private var now = 1_000L
    private val queue = ActivityQueue { now }

    private fun activity(kind: ActivityKind, expiresAt: Long = Long.MAX_VALUE) =
        LiveActivity(kind, Presentation(kind), expiresAt)

    @Test
    fun `empty queue has no winner`() {
        assertNull(queue.top())
        assertNull(queue.millisUntilNextExpiry())
    }

    @Test
    fun `highest priority owns the island`() {
        queue.put(activity(ActivityKind.MEDIA))
        queue.put(activity(ActivityKind.NOTIFICATION, expiresAt = now + 500))
        queue.put(activity(ActivityKind.VOLUME, expiresAt = now + 500))
        assertEquals(ActivityKind.NOTIFICATION, queue.top()?.kind)
    }

    @Test
    fun `sticky activity resurfaces once the transient one expires`() {
        queue.put(activity(ActivityKind.MEDIA))
        queue.put(activity(ActivityKind.NOTIFICATION, expiresAt = now + 500))
        assertEquals(ActivityKind.NOTIFICATION, queue.top()?.kind)

        now += 501
        assertEquals("media should come back, not nothing", ActivityKind.MEDIA, queue.top()?.kind)
        assertFalse(queue.contains(ActivityKind.NOTIFICATION))
    }

    @Test
    fun `expiry is reported for the soonest timed activity only`() {
        queue.put(activity(ActivityKind.MEDIA))
        queue.put(activity(ActivityKind.NOTIFICATION, expiresAt = now + 4_000))
        queue.put(activity(ActivityKind.VOLUME, expiresAt = now + 1_500))
        assertEquals(1_500L, queue.millisUntilNextExpiry())
    }

    @Test
    fun `sticky activities never report an expiry`() {
        queue.put(activity(ActivityKind.MEDIA))
        queue.put(activity(ActivityKind.TIMER))
        assertNull(queue.millisUntilNextExpiry())
    }

    @Test
    fun `putting the same kind twice replaces rather than stacks`() {
        queue.put(activity(ActivityKind.NOTIFICATION, expiresAt = now + 100))
        queue.put(activity(ActivityKind.NOTIFICATION, expiresAt = now + 900))
        assertEquals(1, queue.size)
        now += 200
        assertTrue("the newer expiry should win", queue.contains(ActivityKind.NOTIFICATION))
    }

    @Test
    fun `an already expired activity never shows`() {
        queue.put(activity(ActivityKind.RINGER, expiresAt = now - 1))
        assertNull(queue.top())
    }

    @Test
    fun `call outranks every other activity`() {
        ActivityKind.entries
            .filter { it != ActivityKind.CALL }
            .forEach { kind ->
                assertTrue(
                    "$kind should not outrank a call",
                    ActivityKind.CALL.priority > kind.priority
                )
            }
    }
}
