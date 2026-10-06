package com.otakup.niriko.ui.subject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailPosterCardTest {
    @Test fun backCounterRotationProducesReadableFullTurnNotMirroredHalfTurn() {
        assertEquals(360f, 180f + detailCardCounterRotation(showBack = true, reduceMotion = false), 0.0001f)
        assertEquals(0f, detailCardCounterRotation(showBack = false, reduceMotion = false), 0.0001f)
    }

    @Test fun reducedMotionSwitchesFacesWithoutRotatingContent() {
        assertEquals(0f, detailCardCounterRotation(showBack = true, reduceMotion = true), 0.0001f)
        assertEquals(0f, detailCardCounterRotation(showBack = false, reduceMotion = true), 0.0001f)
    }

    @Test fun frontRemainsVisibleUntilEdgeOn() {
        listOf(0f, 45f, 89.9f, 90f).forEach { assertFalse(detailCardShowsBack(it)) }
    }

    @Test fun backIsVisibleOnlyAfterCrossingEdgeOn() {
        listOf(90.1f, 135f, 180f).forEach { assertTrue(detailCardShowsBack(it)) }
    }

    @Test fun reverseFlipSwitchesFacesAtTheSameBoundary() {
        assertTrue(detailCardShowsBack(100f))
        assertFalse(detailCardShowsBack(80f))
    }

    @Test fun invalidRotationDoesNotDisplayAnUnintendedBack() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertFalse(detailCardShowsBack(it))
        }
    }
}
