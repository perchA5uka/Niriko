package com.otakup.niriko.ui.animation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PressTiltTest {
    private val size = IntSize(200, 300)

    private fun assertRotation(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
    }

    @Test fun centerIsNeutral() {
        assertRotation(Offset.Zero, pressTiltRotation(Offset(100f, 150f), size, 5f))
    }

    @Test fun northWestPressSinksNorthWestCorner() {
        assertRotation(Offset(7f, -7f), pressTiltRotation(Offset.Zero, size, 7f))
    }

    @Test fun northEastPressSinksNorthEastCorner() {
        assertRotation(Offset(7f, 7f), pressTiltRotation(Offset(200f, 0f), size, 7f))
    }

    @Test fun southWestPressSinksSouthWestCorner() {
        assertRotation(Offset(-7f, -7f), pressTiltRotation(Offset(0f, 300f), size, 7f))
    }

    @Test fun southEastPressSinksSouthEastCorner() {
        assertRotation(Offset(-7f, 7f), pressTiltRotation(Offset(200f, 300f), size, 7f))
    }

    @Test fun positionIsNormalizedForEachDimension() {
        assertRotation(Offset(3.5f, 3.5f), pressTiltRotation(Offset(150f, 75f), size, 7f))
    }

    @Test fun outOfBoundsPositionIsClamped() {
        assertRotation(Offset(-7f, -7f), pressTiltRotation(Offset(-100f, 600f), size, 7f))
    }

    @Test fun configuredRotationNeverExceedsEightDegrees() {
        assertRotation(Offset(-8f, 8f), pressTiltRotation(Offset(200f, 300f), size, 30f))
        assertRotation(Offset.Zero, pressTiltRotation(Offset.Zero, size, -5f))
    }

    @Test fun unmeasuredOrNonPointerPressIsNeutral() {
        assertRotation(Offset.Zero, pressTiltRotation(Offset.Zero, IntSize.Zero, 5f))
        assertRotation(Offset.Zero, pressTiltRotation(Offset.Zero, IntSize(200, 0), 5f))
        assertRotation(Offset.Zero, pressTiltRotation(Offset.Unspecified, size, 5f))
    }
}
