package com.otakup.niriko.ui.animation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.PI

/** Stable geometry only: exact endpoints, reversible shallow arc, no bounds overshoot. */
fun subjectCoverPathRect(start: Rect, end: Rect, fraction: Float, density: Float = 1f): Rect {
    val t = fraction.coerceIn(0f, 1f)
    if (t == 0f) return start
    if (t == 1f) return end
    val contracting = end.width * end.height < start.width * start.height
    val small = if (contracting) end else start
    val large = if (contracting) start else end
    val u = if (contracting) 1f - t else t
    val position = u + 0.75f * u * (1f - u)
    // Position leaves the source first; dimensions catch up during the same flight.
    val dimensions = (position - 0.22f * u * (1f - u)).coerceIn(0f, 1f)
    val dx = large.center.x - small.center.x
    val dy = large.center.y - small.center.y
    val distance = hypot(dx, dy)
    val arc = minOf(10f * density.coerceAtLeast(0f), distance * 0.06f)
    val bend = sin(PI * position).toFloat() * arc
    val normalX = if (distance > 0f) -dy / distance else 0f
    val normalY = if (distance > 0f) dx / distance else 0f
    val centerX = small.center.x + dx * position + normalX * bend
    val centerY = small.center.y + dy * position + normalY * bend
    val width = small.width + (large.width - small.width) * dimensions
    val height = small.height + (large.height - small.height) * dimensions
    return Rect(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
}

fun subjectCoverElasticAmount(progress: Float, seeking: Boolean, reduced: Boolean): Float {
    if (seeking || reduced || progress <= 0.725f || progress >= 1f) return 0f
    val phase = ((progress - 0.725f) / 0.275f).coerceIn(0f, 1f)
    val pulse = sin(PI * phase).toFloat()
    return pulse * pulse
}

/** Inner presentation only. Navigation still owns gesture seeking and cancellation. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Modifier.subjectCoverPresentation(scope: AnimatedVisibilityScope?): Modifier {
    if (scope == null) return this
    val reduced = LocalReduceMotion.current
    val presentation by scope.transition.animateFloat(
        transitionSpec = { tween(NirikoMotionSpecs.SUBJECT_COVER_DURATION_MS, easing = LinearEasing) },
        label = "coverElasticPresentation",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val arriving = scope.transition.targetState == EnterExitState.Visible
    return graphicsLayer {
        val progress = if (arriving) presentation else 1f - presentation
        val amount = subjectCoverElasticAmount(progress, scope.transition.isSeeking, reduced)
        scaleX = 1f + 0.015f * amount
        scaleY = 1f + 0.009f * amount
        translationY = (if (arriving) -1f else 1f) * 3f * density * amount
    }
}
