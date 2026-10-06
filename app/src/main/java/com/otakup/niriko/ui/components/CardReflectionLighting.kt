package com.otakup.niriko.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import com.otakup.niriko.ui.animation.AmbientTiltState
import com.otakup.niriko.ui.animation.LocalAmbientTilt
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.PressTiltLighting
import com.otakup.niriko.ui.animation.clampAmbientLight
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

internal const val REFLECTION_RELEASE_NS = 250_000_000L
internal const val REFLECTION_PRESS_NS = 150_000_000L

/** Transitions start at the last drawn value; their destination is sampled live on every draw. */
internal class CardReflectionTransition {
    private var initialized = false
    private var wasPressed = false
    private var displayed = Offset.Zero
    private var origin = Offset.Zero
    private var start: Long? = null
    private var duration = REFLECTION_RELEASE_NS

    fun sample(pressed: Boolean, press: Offset, ambient: Offset, available: Boolean, reduceMotion: Boolean, now: Long): Offset {
        if (reduceMotion) {
            initialized = false
            start = null
            displayed = Offset.Zero
            return displayed
        }
        val target = clampAmbientLight(if (pressed) press else if (available) ambient else Offset.Zero)
        if (!initialized) {
            initialized = true
            wasPressed = pressed
            displayed = target
        } else if (wasPressed != pressed) {
            origin = displayed
            start = now
            duration = if (pressed) REFLECTION_PRESS_NS else REFLECTION_RELEASE_NS
            wasPressed = pressed
        }
        val began = start
        displayed = if (began == null) target else {
            val t = ((now - began).coerceAtLeast(0L).toFloat() / duration).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            if (t >= 1f) start = null
            clampAmbientLight(origin + (target - origin) * eased)
        }
        return displayed
    }
}

internal class CardReflectionLighting(
    private val press: PressTiltLighting?,
    private val ambient: AmbientTiltState?,
    private val reduceMotion: Boolean,
    private val frame: State<Long>,
) {
    private val transition = CardReflectionTransition()

    fun direction(): Offset = transition.sample(
        pressed = press?.isPressed == true,
        press = press?.let { cardLightDirection(it.rotationX, it.rotationY) } ?: Offset.Zero,
        ambient = ambient?.direction ?: Offset.Zero,
        available = ambient?.available == true,
        reduceMotion = reduceMotion,
        now = maxOf(frame.value, System.nanoTime()),
    )
}

internal val LocalCardReflectionLighting = staticCompositionLocalOf<CardReflectionLighting?> { null }

@Composable
internal fun rememberCardReflectionLighting(press: PressTiltLighting?): CardReflectionLighting {
    LocalCardReflectionLighting.current?.let { return it }
    val ambient = LocalAmbientTilt.current
    val reduceMotion = LocalReduceMotion.current
    val frame = remember { mutableLongStateOf(0L) }
    val lighting = remember(press, ambient, reduceMotion) { CardReflectionLighting(press, ambient, reduceMotion, frame) }
    LaunchedEffect(press, reduceMotion) {
        if (press == null || reduceMotion) return@LaunchedEffect
        // Finite frames only for interaction handoff; sensor updates never restart this coroutine.
        snapshotFlow { press.isPressed }.drop(1).collectLatest {
            val began = withFrameNanos { time -> frame.longValue = time; time }
            do {
                val time = withFrameNanos { it }
                frame.longValue = time
            } while (time - began < 300_000_000L)
        }
    }
    return lighting
}
