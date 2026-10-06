package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val CELEBRATION_DURATION_MS = 1050
internal const val CELEBRATION_PARTICLE_COUNT = 24

internal fun celebrationAlpha(progress: Float): Float =
    ((1f - progress.coerceIn(0f, 1f)) / 0.35f).coerceIn(0f, 1f)

internal fun celebrationScale(progress: Float): Float =
    1f + 0.08f * sin(PI.toFloat() * progress.coerceIn(0f, 1f))

@Stable
class CompletionCelebrationState internal constructor() {
    var isActive by mutableStateOf(false)
        private set

    internal suspend fun play(animation: suspend () -> Unit) {
        if (isActive) return
        isActive = true
        try {
            animation()
        } finally {
            finish()
        }
    }

    internal fun finish() {
        isActive = false
    }
}

/** No replay, saved state, layout displacement or pointer interception. */
@Composable
fun CompletionCelebrationHost(
    events: Flow<Long>,
    modifier: Modifier = Modifier,
    playbackState: CompletionCelebrationState = remember { CompletionCelebrationState() },
    content: @Composable BoxScope.() -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion by rememberUpdatedState(LocalReduceMotion.current)
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    val progress = remember { Animatable(1f) }
    LaunchedEffect(LocalReduceMotion.current) {
        if (reduceMotion) {
            playbackState.finish()
            progress.stop()
        }
    }
    LaunchedEffect(events, lifecycle, playbackState) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                events.collect {
                    if (!reduceMotion && !playbackState.isActive) {
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            playbackState.play {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                progress.snapTo(0f)
                                progress.animateTo(1f, tween(CELEBRATION_DURATION_MS, easing = LinearEasing))
                            }
                        }
                    }
                }
            } finally {
                playbackState.finish()
            }
        }
    }
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onSurface)
    Box(modifier) {
        content()
        if (playbackState.isActive && !reduceMotion) {
            Canvas(Modifier.matchParentSize()) {
                val t = progress.value
                val alpha = celebrationAlpha(t)
                val origin = Offset(size.width / 2f, size.height * 0.43f)
                val confirmationAlpha = alpha * (t / 0.12f).coerceIn(0f, 1f)
                val confirmationRadius = 16f * density * celebrationScale(t)
                drawCircle(colors[0].copy(alpha = confirmationAlpha * 0.16f), confirmationRadius, origin)
                val check = Path().apply {
                    moveTo(origin.x - confirmationRadius * 0.45f, origin.y)
                    lineTo(origin.x - confirmationRadius * 0.12f, origin.y + confirmationRadius * 0.30f)
                    lineTo(origin.x + confirmationRadius * 0.48f, origin.y - confirmationRadius * 0.32f)
                }
                drawPath(check, colors[0].copy(alpha = confirmationAlpha), style = Stroke(2f * density))
                repeat(CELEBRATION_PARTICLE_COUNT) { index ->
                    val angle = (-165f + index * 150f / (CELEBRATION_PARTICLE_COUNT - 1)) * PI.toFloat() / 180f
                    val speed = (75f + index % 5 * 12f) * density
                    val position = origin + Offset(cos(angle) * speed * t,
                        sin(angle) * speed * t + 85f * density * t * t)
                    val radius = (2.2f + index % 3 * 0.6f) * density * (1f + 0.08f * sin(PI.toFloat() * t))
                    val color = colors[index % colors.size].copy(alpha = alpha)
                    if (index % 3 == 0) {
                        drawCircle(color, radius, position)
                    } else {
                        rotate(index * 23f + t * 160f, position) {
                            drawRect(color, position - Offset(radius, radius / 2f), Size(radius * 2f, radius))
                        }
                    }
                }
            }
        }
    }
}
