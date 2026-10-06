package com.otakup.niriko.ui.animation

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class SubjectCoverPathTest {
    private val small = Rect(20f, 600f, 120f, 750f)
    private val large = Rect(16f, 72f, 384f, 624f)
    @Test fun pathHasExactEndpointsAndReturnsAlongSameArc() {
        assertEquals(small, subjectCoverPathRect(small, large, 0f))
        assertEquals(large, subjectCoverPathRect(small, large, 1f))
        for (i in 0..100) {
            val forward = subjectCoverPathRect(small, large, i / 100f)
            val reverse = subjectCoverPathRect(large, small, 1f - i / 100f)
            assertEquals(forward.left, reverse.left, 0.0002f)
            assertEquals(forward.top, reverse.top, 0.0002f)
            assertEquals(forward.width, reverse.width, 0.0002f)
        }
    }
    @Test fun positionLeadsDimensionsAndArcStaysWithinTenDp() {
        val dx = large.center.x - small.center.x
        val dy = large.center.y - small.center.y
        val distance = hypot(dx, dy)
        for (i in 1 until 100) {
            val rect = subjectCoverPathRect(small, large, i / 100f, density = 2f)
            val x = rect.center.x - small.center.x
            val y = rect.center.y - small.center.y
            val position = (x * dx + y * dy) / (distance * distance)
            val dimensions = (rect.width - small.width) / (large.width - small.width)
            assertTrue(position > dimensions)
            val bend = abs(x * dy - y * dx) / distance
            assertTrue(bend <= 20.001f)
            assertTrue(rect.width >= small.width && rect.width <= large.width)
        }
    }
    @Test fun shortDistanceAndCoincidentCentersDoNotMakeAnArtificialDetour() {
        val short = small.translate(androidx.compose.ui.geometry.Offset(2f, 1f))
        val mid = subjectCoverPathRect(small, short, 0.5f)
        assertTrue(hypot(mid.center.x - small.center.x, mid.center.y - small.center.y) < 3f)
        val centered = Rect(-30f, 525f, 170f, 825f)
        assertEquals(small.center, subjectCoverPathRect(small, centered, 0.5f).center)
    }
    @Test fun elasticityOnlyOccursDuringLandingAndNeverDuringSeekingOrReducedMotion() {
        assertEquals(0f, subjectCoverElasticAmount(0f, false, false), 0f)
        assertEquals(0f, subjectCoverElasticAmount(0.7f, false, false), 0f)
        assertTrue(subjectCoverElasticAmount(0.86f, false, false) > 0.9f)
        assertEquals(0f, subjectCoverElasticAmount(1f, false, false), 0f)
        for (i in 0..100) {
            val p = i / 100f
            assertEquals(0f, subjectCoverElasticAmount(p, true, false), 0f)
            assertEquals(0f, subjectCoverElasticAmount(p, false, true), 0f)
            assertTrue(subjectCoverElasticAmount(p, false, false) in 0f..1f)
        }
    }
}
