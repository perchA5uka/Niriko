package com.otakup.niriko.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.otakup.niriko.ui.animation.PressTiltLighting
import org.junit.Assert.*
import org.junit.Test

class CardReflectionLightingTest {
    private val ambient = Offset(-0.4f, 0.2f)
    private val press = Offset(0.8f, -0.6f)
    private fun CardReflectionTransition.draw(down: Boolean, time: Long, light: Offset = ambient, available: Boolean = true, motionOff: Boolean = false, touch: Offset = press) =
        sample(down, touch, light, available, motionOff, time)
    private fun assertLight(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.0001f)
        assertEquals(expected.y, actual.y, 0.0001f)
    }

    @Test fun defaultAmbientAndPressHaveExclusivePriority() {
        val controller = CardReflectionTransition()
        assertLight(Offset.Zero, controller.draw(false, 0, available = false))
        assertLight(ambient, controller.draw(false, 1))
        assertLight(ambient, controller.draw(true, 2))
        assertLight(press, controller.draw(true, REFLECTION_PRESS_NS + 2))
        assertLight(press, controller.draw(true, REFLECTION_PRESS_NS + 3, Offset(1f, 1f)))
    }
    @Test fun releaseStartsAtActualDisplayedPointAndDoesNotSumCoordinates() {
        val controller = CardReflectionTransition()
        controller.draw(false, 0)
        controller.draw(true, 1)
        val partial = controller.draw(true, REFLECTION_PRESS_NS / 2 + 1, touch = Offset(0.3f, -0.2f))
        assertLight(partial, controller.draw(false, REFLECTION_PRESS_NS / 2 + 2))
        assertLight(ambient, controller.draw(false, REFLECTION_PRESS_NS / 2 + 2 + REFLECTION_RELEASE_NS))
    }
    @Test fun releaseDestinationRemainsLiveAsDeviceMoves() {
        val controller = CardReflectionTransition()
        controller.draw(true, 0)
        controller.draw(false, 1)
        val nextAmbient = Offset(-0.8f, 0.6f)
        val middle = controller.draw(false, REFLECTION_RELEASE_NS / 2 + 1, nextAmbient)
        assertLight(press + (nextAmbient - press) * 0.5f, middle)
        assertLight(nextAmbient, controller.draw(false, REFLECTION_RELEASE_NS + 1, nextAmbient))
        assertLight(Offset(0.2f, 0.7f), controller.draw(false, REFLECTION_RELEASE_NS + 2, Offset(0.2f, 0.7f)))
    }
    @Test fun releaseUsesDefaultWhenSensorBecomesUnavailable() {
        val controller = CardReflectionTransition()
        controller.draw(true, 0)
        controller.draw(false, 1, available = false)
        assertLight(Offset.Zero, controller.draw(false, REFLECTION_RELEASE_NS + 1, available = false))
    }
    @Test fun quickRepressInterruptsFromCurrentDisplay() {
        val controller = CardReflectionTransition()
        controller.draw(true, 0)
        controller.draw(false, 1)
        val middle = controller.draw(false, 100_000_001)
        assertLight(middle, controller.draw(true, 100_000_002, touch = Offset(-0.5f, -0.5f)))
        assertLight(Offset(-0.5f, -0.5f), controller.draw(true, 100_000_002 + REFLECTION_PRESS_NS, touch = Offset(-0.5f, -0.5f)))
    }
    @Test fun independentCardsDoNotSharePressTransitions() {
        val a = CardReflectionTransition()
        val b = CardReflectionTransition()
        a.draw(false, 0)
        b.draw(false, 0)
        a.draw(true, 1)
        assertLight(press, a.draw(true, REFLECTION_PRESS_NS + 1))
        assertLight(ambient, b.draw(false, REFLECTION_PRESS_NS + 1))
    }
    @Test fun flipFacesReadTheSameControllerWithoutReset() {
        val shared = CardReflectionTransition()
        val front = shared.draw(true, 0)
        val back = shared.draw(true, 1)
        assertLight(front, back)
        shared.draw(false, 2)
        val frontDuringReturn = shared.draw(false, 125_000_002)
        val backDuringReturn = shared.draw(false, 125_000_002)
        assertLight(frontDuringReturn, backDuringReturn)
    }
    @Test fun reducedMotionCancelsDynamicLightingAndReenableIsFinite() {
        val controller = CardReflectionTransition()
        controller.draw(true, 0)
        controller.draw(false, 1)
        assertLight(Offset.Zero, controller.draw(false, 2, motionOff = true))
        assertLight(Offset.Zero, controller.draw(true, 3, motionOff = true))
        assertLight(ambient, controller.draw(false, 4))
    }
    @Test fun invalidInputsAndClockOrderingStayBounded() {
        val controller = CardReflectionTransition()
        assertLight(Offset.Zero, controller.draw(false, 10, Offset(Float.NaN, 1f)))
        controller.draw(true, 20)
        assertLight(Offset.Zero, controller.draw(true, 19))
        assertLight(Offset(1f, -1f), controller.draw(true, REFLECTION_PRESS_NS + 20, touch = Offset(5f, -5f)))
    }
    @Test fun pressObservationDoesNotModifyPhysicalAnimationStates() {
        val lighting = PressTiltLighting()
        val x = mutableStateOf(-2.4f)
        val y = mutableStateOf(3.1f)
        lighting.bind(x, y)
        lighting.setPressed(true)
        assertTrue(lighting.isPressed)
        lighting.setPressed(false)
        assertFalse(lighting.isPressed)
        assertEquals(-2.4f, lighting.rotationX, 0f)
        assertEquals(3.1f, lighting.rotationY, 0f)
        lighting.setPressed(true)
        lighting.bind()
        assertFalse(lighting.isPressed)
    }
}
