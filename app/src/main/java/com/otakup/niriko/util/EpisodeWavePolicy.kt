package com.otakup.niriko.util

import kotlin.math.PI
import kotlin.math.sin

/** Each badge receives a distinct start time, independent of row geometry. */
object EpisodeWavePolicy {
    const val DURATION_MS = 700
    const val PULSE_FRACTION = 0.32f

    fun badgeAlpha(progress: Float, index: Int, count: Int): Float {
        if (count <= 0 || index !in 0 until count || !progress.isFinite()) return 0f
        val start = if (count == 1) 0f else index.toFloat() / (count - 1) * (1f - PULSE_FRACTION)
        val local = (progress - start) / PULSE_FRACTION
        if (local <= 0f || local >= 1f) return 0f
        return sin(PI.toFloat() * local).coerceIn(0f, 1f)
    }
}
