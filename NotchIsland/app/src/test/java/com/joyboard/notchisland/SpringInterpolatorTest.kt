package com.joyboard.notchisland

import com.joyboard.notchisland.island.SpringInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpringInterpolatorTest {

    private fun samples(spring: SpringInterpolator, n: Int = 400) =
        (0..n).map { spring.getInterpolation(it / n.toFloat()) }

    @Test
    fun `starts at rest and ends exactly at the target`() {
        listOf(SpringInterpolator.island(), SpringInterpolator.subtle(), SpringInterpolator(0.3f, 1f)).forEach {
            assertEquals(0f, it.getInterpolation(0f), 0f)
            assertEquals(1f, it.getInterpolation(1f), 0f)
        }
    }

    @Test
    fun `the island spring bounces, by a few percent`() {
        // This is the point of it: an ease curve cannot overshoot. For a damping fraction of
        // 0.72 the theoretical overshoot is exp(-pi*z/sqrt(1-z^2)), about 3.8%.
        val peak = samples(SpringInterpolator.island()).max()
        assertTrue("expected an overshoot, peak was $peak", peak > 1.01f)
        assertTrue("overshoot should read as a bounce, not a wobble: $peak", peak < 1.08f)
    }

    @Test
    fun `a critically damped spring never overshoots`() {
        val values = samples(SpringInterpolator(response = 0.4f, dampingFraction = 1f))
        assertTrue(values.max() <= 1.0001f)
        values.zipWithNext().forEach { (a, b) -> assertTrue("not monotonic: $a -> $b", b >= a - 1e-6f) }
    }

    @Test
    fun `it has visibly settled before the animator stops`() {
        // The animator clamps to 1 at the end; if the spring were still moving, that last frame
        // would jump. Over the final tenth it must already be within half a percent.
        listOf(SpringInterpolator.island(), SpringInterpolator.subtle()).forEach { spring ->
            (360..400).map { spring.getInterpolation(it / 400f) }.forEach {
                assertTrue("still moving near the end: $it", abs(it - 1f) < 0.005f)
            }
        }
    }

    @Test
    fun `the subtle spring bounces less than the island one`() {
        assertTrue(samples(SpringInterpolator.subtle()).max() < samples(SpringInterpolator.island()).max())
    }

    @Test
    fun `extreme parameters stay finite`() {
        listOf(
            SpringInterpolator(response = 0f, dampingFraction = 0f),
            SpringInterpolator(response = 10f, dampingFraction = 5f),
            SpringInterpolator(response = 0.01f, dampingFraction = 1f),
        ).forEach { spring ->
            samples(spring).forEach { assertFalse("non-finite value", it.isNaN() || it.isInfinite()) }
        }
    }

    @Test
    fun `settle time follows the response and stays in bounds`() {
        assertTrue(SpringInterpolator(0.3f).settleDurationMs < SpringInterpolator(0.6f).settleDurationMs)
        assertTrue(SpringInterpolator(0.001f).settleDurationMs >= 120)
        assertTrue(SpringInterpolator(50f).settleDurationMs <= 1400)
    }
}
