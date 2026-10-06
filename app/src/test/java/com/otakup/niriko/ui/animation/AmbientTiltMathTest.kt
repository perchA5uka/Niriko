package com.otakup.niriko.ui.animation

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class AmbientTiltMathTest {
    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private fun tilt(degrees: Float, horizontal: Boolean = true): FloatArray {
        val a = Math.toRadians(degrees.toDouble()).toFloat()
        val c = cos(a)
        val s = sin(a)
        return if (horizontal) floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)
        else floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c)
    }
    private fun assertLight(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
    }

    @Test fun staticAndInitialUprightPostureAreNeutral() {
        val upright = tilt(90f, false)
        assertLight(Offset.Zero, ambientDirection(upright, upright, 0)!!)
        val filter = AmbientTiltFilter()
        repeat(100) { filter.sample(upright, 0, it * 20_000_000L) }
        assertLight(Offset.Zero, filter.direction)
    }
    @Test fun fourTiltDirectionsAndTwentyDegreeLimits() {
        for (degrees in listOf(-80f, -20f, -10f, 0f, 10f, 20f, 80f)) {
            val expected = (degrees / 20f).coerceIn(-1f, 1f)
            assertLight(Offset(expected, 0f), ambientDirection(identity(), tilt(degrees), 0)!!)
            assertLight(Offset(0f, expected), ambientDirection(identity(), tilt(degrees, false), 0)!!)
        }
    }
    @Test fun allScreenRotationsMapToScreenCoordinates() {
        val input = Offset(0.5f, 0.25f)
        val expected = listOf(input, Offset(0.25f, -0.5f), Offset(-0.5f, -0.25f), Offset(-0.25f, 0.5f))
        expected.forEachIndexed { r, value -> assertLight(value, ambientScreenDirection(input, r)) }
    }
    @Test fun clampAndInvalidInputsStayFinite() {
        assertLight(Offset(1f, -1f), clampAmbientLight(Offset(9f, -9f)))
        assertLight(Offset.Zero, clampAmbientLight(Offset(Float.NaN, 1f)))
        assertNull(ambientDirection(identity(), FloatArray(8), 0))
        assertNull(ambientDirection(identity(), FloatArray(9) { Float.NaN }, 0))
    }
    @Test fun lowPassIsTimeBasedAndHasNoOvershoot() {
        var slow = Offset.Zero
        var fast = Offset.Zero
        repeat(50) { slow = smoothAmbientLight(slow, Offset(1f, -1f), 0.02f) }
        repeat(100) { fast = smoothAmbientLight(fast, Offset(1f, -1f), 0.01f) }
        assertLight(slow, fast)
        val first = smoothAmbientLight(Offset.Zero, Offset(1f, -1f), 0.02f)
        assertTrue(first.x in 0.1f..0.2f)
        assertTrue(first.y in -0.2f..-0.1f)
    }
    @Test fun noiseThresholdStillAccumulatesSmallRealMovement() {
        assertFalse(ambientLightChanged(Offset.Zero, Offset(0.004f, 0f)))
        assertTrue(ambientLightChanged(Offset.Zero, Offset(0.006f, 0f)))
        val filter = AmbientTiltFilter()
        filter.sample(identity(), 0, 0L)
        repeat(100) { filter.sample(tilt(0.2f), 0, (it + 1L) * 20_000_000L) }
        assertTrue(ambientLightChanged(Offset.Zero, filter.direction))
    }
    @Test fun rotationChangeDoesNotResetDisplayedLight() {
        val filter = AmbientTiltFilter()
        filter.sample(identity(), 0, 0L)
        repeat(50) { filter.sample(tilt(10f), 0, (it + 1L) * 20_000_000L) }
        val before = filter.direction
        filter.sample(tilt(10f), 1, 1_020_000_000L)
        assertTrue(filter.direction.x > 0.4f)
        assertTrue(filter.direction.y < 0f)
        assertTrue((filter.direction - before).getDistance() < 0.15f)
        repeat(100) { filter.sample(tilt(10f), 1, (it + 52L) * 20_000_000L) }
        assertEquals(-0.5f, filter.direction.y, 0.001f)
    }
    @Test fun rapidAlternatingTiltRemainsBounded() {
        val filter = AmbientTiltFilter()
        filter.sample(identity(), 0, 0L)
        repeat(200) {
            filter.sample(tilt(if (it % 2 == 0) 80f else -80f), 0, (it + 1L) * 20_000_000L)
            assertTrue(filter.direction.x in -1f..1f)
            assertTrue(kotlin.math.abs(filter.direction.x) < 0.2f)
        }
    }
    @Test fun invalidOrOldSamplesDoNotChangeState() {
        val filter = AmbientTiltFilter(Offset(0.3f, 0.2f))
        filter.sample(identity(), 0, 100L)
        assertFalse(filter.sample(tilt(20f), 0, 100L))
        assertFalse(filter.sample(FloatArray(9) { Float.NaN }, 0, 200L))
        assertLight(Offset(0.3f, 0.2f), filter.direction)
    }
    @Test fun restoredDisplayValueSettlesWithoutFirstSampleJump() {
        val filter = AmbientTiltFilter(Offset(0.5f, -0.4f))
        filter.sample(tilt(45f, false), 1, 0L)
        assertLight(Offset(0.5f, -0.4f), filter.direction)
        repeat(100) { filter.sample(tilt(45f, false), 1, (it + 1L) * 20_000_000L) }
        assertEquals(0f, filter.direction.x, 0.001f)
    }
    @Test fun motionAndPageAndVisibilityGatesAreAllRequired() {
        for (active in listOf(false, true)) for (visible in listOf(false, true)) for (reduce in listOf(false, true)) {
            assertEquals(active && visible && !reduce, ambientReflectionEnabled(active, visible, reduce))
        }
    }
}
