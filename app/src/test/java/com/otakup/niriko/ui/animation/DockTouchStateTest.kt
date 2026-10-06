package com.otakup.niriko.ui.animation

import com.otakup.niriko.ui.bottombar.DockTouchState
import org.junit.Assert.*
import org.junit.Test

class DockTouchStateTest {
    @Test fun eachCellIsClickableAcrossItsFullWidth() {
        val state = DockTouchState(4, 100f)
        for (i in 0..3) for (fraction in listOf(0.01f, 0.5f, 0.99f)) {
            state.start((i + fraction) * 100f)
            assertEquals(i, state.index)
            assertEquals(i.toFloat(), state.indicatorValue, 0.001f)
        }
    }
    @Test fun holdingUnselectedCellStartsThereWithoutMovement() {
        val state = DockTouchState(4, 100f)
        state.start(280f)
        assertEquals(2, state.index)
        assertEquals(2f, state.indicatorValue, 0f)
        assertFalse(state.hasDragged(8f))
    }
    @Test fun draggingPreservesGrabOffsetAndCommitsHitCell() {
        val state = DockTouchState(4, 100f)
        state.start(275f)
        state.move(-110f)
        assertEquals(1, state.index)
        assertEquals(0.9f, state.indicatorValue, 0.001f)
        assertTrue(state.hasDragged(8f))
    }
    @Test fun rtlMirrorsHitCellsAndDragDirection() {
        val state = DockTouchState(4, 100f, true)
        state.start(325f)
        assertEquals(0, state.index)
        state.move(-190f)
        assertEquals(2, state.index)
        assertEquals(1.9f, state.indicatorValue, 0.001f)
    }
    @Test fun tinyMovementIsNotAnIntentionalDragAndEdgesClamp() {
        val state = DockTouchState(4, 100f)
        state.start(399f)
        state.move(2f)
        assertEquals(3, state.index)
        assertFalse(state.hasDragged(8f))
        state.move(-500f)
        assertEquals(0, state.index)
        assertEquals(0f, state.indicatorValue, 0f)
    }
}
