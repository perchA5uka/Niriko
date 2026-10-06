package com.otakup.niriko.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 非模态面板（R6）关闭手势判定与阈值常量的纯函数测试。 */
class NirikoNonModalSheetTest {

    private val distance = 72f
    private val velocity = 900f

    @Test
    fun doesNotDismissWhenBelowBothThresholds() {
        assertFalse(shouldDismissSheet(dragDistancePx = 10f, dragVelocityPx = 100f, distanceThresholdPx = distance, velocityThresholdPx = velocity))
    }

    @Test
    fun dismissesOnDistanceAlone() {
        assertTrue(shouldDismissSheet(dragDistancePx = 73f, dragVelocityPx = 0f, distanceThresholdPx = distance, velocityThresholdPx = velocity))
    }

    @Test
    fun dismissesOnVelocityAlone() {
        assertTrue(shouldDismissSheet(dragDistancePx = 0f, dragVelocityPx = 901f, distanceThresholdPx = distance, velocityThresholdPx = velocity))
    }

    @Test
    fun thresholdBoundaryIsNotDismissed() {
        assertFalse(shouldDismissSheet(dragDistancePx = distance, dragVelocityPx = velocity, distanceThresholdPx = distance, velocityThresholdPx = velocity))
    }

    @Test
    fun upwardDragNeverDismisses() {
        assertFalse(shouldDismissSheet(dragDistancePx = -500f, dragVelocityPx = -2000f, distanceThresholdPx = distance, velocityThresholdPx = velocity))
    }

    @Test
    fun dismissConstantsAreSane() {
        assertEquals(72f, SHEET_DISMISS_DISTANCE_DP, 0f)
        assertEquals(900f, SHEET_DISMISS_VELOCITY_DP, 0f)
        assertTrue(SHEET_DISMISS_VELOCITY_DP > 0f)
    }
}
