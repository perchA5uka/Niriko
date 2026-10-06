package com.otakup.niriko.ui.wallpaper

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperParallaxTest {
    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private fun aroundX(degrees: Float): FloatArray {
        val angle = Math.toRadians(degrees.toDouble()).toFloat()
        val c = cos(angle)
        val s = sin(angle)
        return floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c)
    }
    private fun aroundY(degrees: Float): FloatArray {
        val angle = Math.toRadians(degrees.toDouble()).toFloat()
        val c = cos(angle)
        val s = sin(angle)
        return floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)
    }
    private fun multiply(a: FloatArray, b: FloatArray) = FloatArray(9) { index ->
        val row = index / 3
        val col = index % 3
        (0..2).sumOf { k -> (a[row * 3 + k] * b[k * 3 + col]).toDouble() }.toFloat()
    }
    private fun settle(filter: WallpaperParallaxFilter, matrix: FloatArray) {
        repeat(100) { filter.sample(matrix, 0, (it + 1L) * 20_000_000L) }
    }

    @Test fun initialPostureIsNeutralEvenWhenPhoneIsUpright() {
        val filter = WallpaperParallaxFilter()
        assertTrue(filter.sample(aroundX(90f), 0, 0L))
        assertEquals(0f, filter.x, 0f)
        assertEquals(0f, filter.y, 0f)
        settle(filter, aroundX(90f))
        assertEquals(0f, filter.x, 0.0001f)
        assertEquals(0f, filter.y, 0.0001f)
    }

    @Test fun horizontalAndVerticalTiltMoveInOppositeScreenDirection() {
        val horizontal = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        settle(horizontal, aroundY(9f))
        assertEquals(-0.5f, horizontal.x, 0.001f)
        assertEquals(0f, horizontal.y, 0.001f)
        val vertical = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        settle(vertical, aroundX(9f))
        assertEquals(0f, vertical.x, 0.001f)
        assertEquals(-0.5f, vertical.y, 0.001f)
    }

    @Test fun uprightBaselineRetainsSameRelativeTiltMapping() {
        val baseline = aroundX(90f)
        val filter = WallpaperParallaxFilter().apply { sample(baseline, 0, 0L) }
        settle(filter, multiply(baseline, aroundY(9f)))
        assertEquals(-0.5f, filter.x, 0.001f)
        assertEquals(0f, filter.y, 0.001f)
    }

    @Test fun largeMovementIsClampedAndFilteredReturnSettlesToNeutral() {
        val filter = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        settle(filter, aroundY(80f))
        assertTrue(filter.x in -1f..1f)
        assertEquals(-1f, filter.x, 0.001f)
        repeat(100) { filter.sample(identity(), 0, (it + 101L) * 20_000_000L) }
        assertEquals(0f, filter.x, 0.001f)
    }

    @Test fun tiltingPastARightAngleDoesNotIntroduceOtherAxisMovement() {
        val filter = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        settle(filter, aroundY(120f))
        assertEquals(-1f, filter.x, 0.001f)
        assertEquals(0f, filter.y, 0.001f)
    }

    @Test fun smoothingSoftensIndividualSamplesWithoutFrameRateDependence() {
        val slow = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        val fast = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        slow.sample(aroundY(9f), 0, 20_000_000L)
        assertTrue(abs(slow.x) in 0.001f..0.1f)
        for (i in 2..50) slow.sample(aroundY(9f), 0, i * 20_000_000L)
        for (i in 1..100) fast.sample(aroundY(9f), 0, i * 10_000_000L)
        assertEquals(slow.x, fast.x, 0.0001f)
    }

    @Test fun rotationAndRestartRecalibrateWithoutCarryingOldOffset() {
        val filter = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        settle(filter, aroundY(9f))
        filter.sample(aroundY(9f), 1, 3_000_000_000L)
        assertEquals(0f, filter.x, 0f)
        filter.reset()
        filter.sample(aroundX(45f), 1, 4_000_000_000L)
        assertEquals(0f, filter.x, 0f)
        assertEquals(0f, filter.y, 0f)
    }

    @Test fun yawAroundScreenNormalDoesNotSlideWallpaper() {
        val filter = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        settle(filter, floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))
        assertEquals(0f, filter.x, 0f)
        assertEquals(0f, filter.y, 0f)
    }

    @Test fun invalidOrOutOfOrderSamplesCannotCorruptOffset() {
        val filter = WallpaperParallaxFilter().apply { sample(identity(), 0, 0L) }
        settle(filter, aroundY(9f))
        val previous = filter.x
        assertFalse(filter.sample(FloatArray(9) { Float.NaN }, 0, 3_000_000_000L))
        assertFalse(filter.sample(FloatArray(8), 0, 3_000_000_000L))
        assertFalse(filter.sample(identity(), 0, 1L))
        assertEquals(previous, filter.x, 0f)
    }

    @Test fun travelNeverExceedsCropMarginAcrossSizesAndDensities() {
        for (density in listOf(1f, 2f, 3f, 4f)) {
            for (dp in listOf(0f, 16f, 200f, 320f, 360f, 720f, 1280f)) {
                val size = dp * density
                val travel = wallpaperParallaxTravelPx(size, density)
                assertTrue(travel >= 0f)
                assertTrue(travel <= 18f * density)
                assertTrue(travel <= size * (WALLPAPER_PARALLAX_SCALE - 1f) / 2f)
            }
        }
        assertEquals(18f, wallpaperParallaxTravelPx(720f, 1f), 0f)
    }
}
