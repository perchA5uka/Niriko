package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize

/** Read-only lighting channel bound to the existing tilt animation states. */
class PressTiltLighting {
    private var x: State<Float>? = null
    private var y: State<Float>? = null
    var isPressed by mutableStateOf(false)
        private set

    internal fun setPressed(pressed: Boolean) {
        isPressed = pressed
    }
    val rotationX: Float get() = x?.value ?: 0f
    val rotationY: Float get() = y?.value ?: 0f

    internal fun bind(rotationX: State<Float>? = null, rotationY: State<Float>? = null) {
        x = rotationX
        y = rotationY
        if (rotationX == null || rotationY == null) isPressed = false
    }
}

internal fun pressTiltRotation(position: Offset, size: IntSize, maxRotation: Float): Offset {
    if (size.width <= 0 || size.height <= 0 || !position.isSpecified) return Offset.Zero
    val limit = maxRotation.coerceIn(0f, 8f)
    val x = (position.x / size.width * 2f - 1f).coerceIn(-1f, 1f)
    val y = (position.y / size.height * 2f - 1f).coerceIn(-1f, 1f)
    // Sink the touched edge rather than lifting it toward the viewer.
    return Offset(-y * limit, x * limit)
}

/** Passive artwork feedback without adding a click action or consuming scroll gestures. */
@Composable
fun Modifier.pressTilt(
    maxRotation: Float = 7f,
    pressScale: Float = 0.99f,
    enabled: Boolean = true,
    hapticEnabled: Boolean = false,
    lighting: PressTiltLighting? = null,
): Modifier {
    if (!enabled || LocalReduceMotion.current) {
        lighting?.bind()
        return this
    }
    val interactionSource = remember { MutableInteractionSource() }
    return pressTilt(interactionSource, maxRotation, pressScale, enabled, hapticEnabled, lighting)
        .pointerInput(interactionSource) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                var released = false
                try {
                    while (true) {
                        // Final pass observes consumption by the parent scrolling container.
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            released = true
                            break
                        }
                        val position = change.position
                        if (position.x < 0f || position.x > size.width ||
                            position.y < 0f || position.y > size.height) break
                    }
                } finally {
                    interactionSource.tryEmit(
                        if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press),
                    )
                }
            }
        }
}

/** Observes the existing clickable's press lifecycle; never consumes pointer input. */
@Composable
fun Modifier.pressTilt(
    interactionSource: InteractionSource,
    maxRotation: Float = 7f,
    pressScale: Float = 0.99f,
    enabled: Boolean = true,
    hapticEnabled: Boolean = false,
    lighting: PressTiltLighting? = null,
): Modifier {
    if (!enabled || LocalReduceMotion.current) {
        lighting?.bind()
        return this
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var press by remember(interactionSource) { mutableStateOf<PressInteraction.Press?>(null) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(interactionSource, hapticEnabled, haptic) {
        try {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        lighting?.setPressed(true)
                        press = interaction
                        if (hapticEnabled && interaction.pressPosition.isSpecified) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    is PressInteraction.Release -> if (interaction.press == press) {
                        lighting?.setPressed(false)
                        press = null
                    }
                    is PressInteraction.Cancel -> if (interaction.press == press) {
                        lighting?.setPressed(false)
                        press = null
                    }
                }
            }
        } finally {
            lighting?.setPressed(false)
            press = null
        }
    }
    val position = press?.pressPosition ?: Offset.Unspecified
    val rotation = pressTiltRotation(position, size, maxRotation)
    val rotationX = animateFloatAsState(rotation.x, NirikoMotionSpecs.spatialFast(), label = "pressTiltX")
    val rotationY = animateFloatAsState(rotation.y, NirikoMotionSpecs.spatialFast(), label = "pressTiltY")
    val scale = animateFloatAsState(
        if (press != null && position.isSpecified) pressScale.coerceIn(0.985f, 1f) else 1f,
        NirikoMotionSpecs.spatialFast(),
        label = "pressTiltScale",
    )
    lighting?.bind(rotationX, rotationY)
    return this
        .onSizeChanged { size = it }
        .graphicsLayer {
            this.rotationX = rotationX.value.coerceIn(-8f, 8f)
            this.rotationY = rotationY.value.coerceIn(-8f, 8f)
            scaleX = scale.value
            scaleY = scale.value
            cameraDistance = 12f * density
        }
}
