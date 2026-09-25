package com.joyboard.notchisland

import com.joyboard.notchisland.island.IslandMode
import com.joyboard.notchisland.island.RestPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestPolicyTest {

    private fun target(
        userStage: IslandMode? = null,
        hasActivity: Boolean = false,
        msSince: Long = 0,
        stayCompact: Boolean = false,
        restMs: Long = 10_000,
        alwaysShowPill: Boolean = true,
    ) = RestPolicy.target(userStage, hasActivity, msSince, stayCompact, restMs, alwaysShowPill)

    @Test
    fun `what the user asked for always wins`() {
        assertEquals(IslandMode.EXPANDED, target(userStage = IslandMode.EXPANDED, hasActivity = true))
        assertEquals(IslandMode.MEDIUM, target(userStage = IslandMode.MEDIUM, msSince = 999_999))
    }

    @Test
    fun `a fresh activity earns the compact readout`() {
        assertEquals(IslandMode.COMPACT, target(hasActivity = true, msSince = 1_000))
    }

    @Test
    fun `a long-running activity does not hold the island open forever`() {
        // The bug this exists to prevent: an ongoing download pinning it at compact.
        assertEquals(IslandMode.PILL, target(hasActivity = true, msSince = 60_000))
    }

    @Test
    fun `stay-compact restores the iOS behaviour`() {
        assertEquals(
            IslandMode.COMPACT,
            target(hasActivity = true, msSince = 60_000, stayCompact = true)
        )
    }

    @Test
    fun `nothing live means the resting pill`() {
        assertEquals(IslandMode.PILL, target())
    }

    @Test
    fun `with the pill switched off it disappears entirely`() {
        assertEquals(IslandMode.HIDDEN, target(alwaysShowPill = false))
        assertEquals(
            IslandMode.HIDDEN,
            target(hasActivity = true, msSince = 60_000, alwaysShowPill = false)
        )
    }

    @Test
    fun `the settle boundary is scheduled while compact time remains`() {
        assertEquals(
            6_000L,
            RestPolicy.millisUntilSettle(null, true, 4_000, false, 10_000)
        )
    }

    @Test
    fun `nothing is scheduled once it has already settled`() {
        assertNull(RestPolicy.millisUntilSettle(null, true, 10_000, false, 10_000))
        assertNull(RestPolicy.millisUntilSettle(null, true, 1_000, true, 10_000))
        assertNull(RestPolicy.millisUntilSettle(IslandMode.EXPANDED, true, 1_000, false, 10_000))
        assertNull(RestPolicy.millisUntilSettle(null, false, 0, false, 10_000))
    }
}
