package com.otakup.niriko.ui.stats

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自绘雷达图的纯几何单测（无需 Robolectric：只碰 Offset / Float 数学）。
 * 见 docs/ui-reference-plan-round3.md §四 R4。
 */
class NirikoRadarChartTest {

    private val delta = 0.0001f

    @Test
    fun angleStartsAtTopAndSplitsEvenly() {
        // 4 轴：正上 -90° → 右 0° → 下 90° → 左 180°
        assertEquals(-90f, RadarGeometry.angleDegrees(0, 4), delta)
        assertEquals(0f, RadarGeometry.angleDegrees(1, 4), delta)
        assertEquals(90f, RadarGeometry.angleDegrees(2, 4), delta)
        assertEquals(180f, RadarGeometry.angleDegrees(3, 4), delta)
        // 3 轴：-90 / 30 / 150
        assertEquals(-90f, RadarGeometry.angleDegrees(0, 3), delta)
        assertEquals(30f, RadarGeometry.angleDegrees(1, 3), delta)
        assertEquals(150f, RadarGeometry.angleDegrees(2, 3), delta)
    }

    @Test
    fun angleDegradesGracefullyForEmptyAxisList() {
        assertEquals(-90f, RadarGeometry.angleDegrees(0, 0), delta)
    }

    @Test
    fun normalizeScalesToMaximumAndClamps() {
        assertEquals(0.5f, RadarGeometry.normalize(5f, 10f), delta)
        assertEquals(1f, RadarGeometry.normalize(10f, 10f), delta)
        assertEquals(1f, RadarGeometry.normalize(20f, 10f), delta)
        assertEquals(0f, RadarGeometry.normalize(-3f, 10f), delta)
        assertEquals(0f, RadarGeometry.normalize(0f, 10f), delta)
    }

    @Test
    fun normalizeReturnsZeroForNonPositiveOrNonFiniteMaximum() {
        assertEquals(0f, RadarGeometry.normalize(7f, 0f), delta)
        assertEquals(0f, RadarGeometry.normalize(7f, -1f), delta)
        assertEquals(0f, RadarGeometry.normalize(7f, Float.NaN), delta)
        assertEquals(0f, RadarGeometry.normalize(7f, Float.POSITIVE_INFINITY), delta)
        assertEquals(0f, RadarGeometry.normalize(Float.NaN, 10f), delta)
    }

    @Test
    fun pointProjectsWithScreenCoordinates() {
        val center = Offset(100f, 100f)
        val top = RadarGeometry.point(center, 50f, -90f)
        assertEquals(100f, top.x, delta)
        assertEquals(50f, top.y, delta)
        val right = RadarGeometry.point(center, 50f, 0f)
        assertEquals(150f, right.x, delta)
        assertEquals(100f, right.y, delta)
        val bottom = RadarGeometry.point(center, 50f, 90f)
        assertEquals(100f, bottom.x, delta)
        assertEquals(150f, bottom.y, delta)
        val left = RadarGeometry.point(center, 50f, 180f)
        assertEquals(50f, left.x, delta)
        assertEquals(100f, left.y, delta)
    }

    @Test
    fun pointReturnsCenterForZeroRadius() {
        val center = Offset(12.5f, -4f)
        val p = RadarGeometry.point(center, 0f, 37f)
        assertEquals(center.x, p.x, delta)
        assertEquals(center.y, p.y, delta)
    }

    @Test
    fun pointStaysOnCircleForArbitraryAngle() {
        val center = Offset(0f, 0f)
        val p = RadarGeometry.point(center, 20f, 123f)
        val distance = kotlin.math.sqrt(p.x * p.x + p.y * p.y)
        assertEquals(20f, distance, 0.001f)
    }

    @Test
    fun axisCountConstantsAreSane() {
        assertTrue(MIN_RADAR_AXES >= 3)
        assertTrue(MAX_RADAR_AXES > MIN_RADAR_AXES)
    }
}
