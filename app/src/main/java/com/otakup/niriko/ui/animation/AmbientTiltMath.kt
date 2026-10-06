package com.otakup.niriko.ui.animation

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

internal const val AMBIENT_MAX_TILT = 0.34906585f
internal const val AMBIENT_PUBLISH_INTERVAL_NS = 33_333_333L
internal const val AMBIENT_NOISE_THRESHOLD = 0.005f

internal fun clampAmbientLight(value: Offset): Offset =
    if (value.x.isFinite() && value.y.isFinite()) Offset(value.x.coerceIn(-1f, 1f), value.y.coerceIn(-1f, 1f))
    else Offset.Zero

/** Screen X points right and screen Y points down, unlike sensor Y. */
internal fun ambientScreenDirection(direction: Offset, rotation: Int): Offset = clampAmbientLight(when (rotation) {
    1 -> Offset(direction.y, -direction.x)
    2 -> Offset(-direction.x, -direction.y)
    3 -> Offset(-direction.y, direction.x)
    else -> direction
})

/** The surface normal in the initial device frame, independent of Euler wrap or yaw. */
internal fun ambientDirection(baseline: FloatArray, matrix: FloatArray, rotation: Int): Offset? {
    if (baseline.size != 9 || matrix.size != 9 || baseline.any { !it.isFinite() } || matrix.any { !it.isFinite() }) return null
    val nx = baseline[0] * matrix[2] + baseline[3] * matrix[5] + baseline[6] * matrix[8]
    val ny = baseline[1] * matrix[2] + baseline[4] * matrix[5] + baseline[7] * matrix[8]
    val nz = baseline[2] * matrix[2] + baseline[5] * matrix[5] + baseline[8] * matrix[8]
    return ambientScreenDirection(Offset(
        atan2(nx, sqrt(ny * ny + nz * nz)) / AMBIENT_MAX_TILT,
        -atan2(ny, sqrt(nx * nx + nz * nz)) / AMBIENT_MAX_TILT,
    ), rotation)
}

internal fun smoothAmbientLight(current: Offset, target: Offset, seconds: Float): Offset {
    if (!seconds.isFinite() || seconds <= 0f) return clampAmbientLight(current)
    val alpha = 1f - exp(-seconds.coerceAtMost(0.1f) / 0.12f)
    val safeCurrent = clampAmbientLight(current)
    val safeTarget = clampAmbientLight(target)
    return clampAmbientLight(safeCurrent + (safeTarget - safeCurrent) * alpha)
}

internal fun ambientLightChanged(current: Offset, next: Offset): Boolean =
    maxOf(abs(current.x - next.x), abs(current.y - next.y)) >= AMBIENT_NOISE_THRESHOLD

internal fun ambientReflectionEnabled(active: Boolean, visibleRarity: Boolean, reduceMotion: Boolean): Boolean =
    active && visibleRarity && !reduceMotion

internal class AmbientTiltFilter(initial: Offset = Offset.Zero) {
    private var baseline: FloatArray? = null
    private var timestamp: Long? = null
    var direction: Offset = clampAmbientLight(initial)
        private set

    fun sample(matrix: FloatArray, rotation: Int, time: Long): Boolean {
        if (matrix.size != 9 || matrix.any { !it.isFinite() } || time < 0L) return false
        val last = timestamp
        if (last != null && time <= last) return false
        val reference = baseline ?: matrix.copyOf().also { baseline = it }
        val target = ambientDirection(reference, matrix, rotation) ?: return false
        if (last != null) direction = smoothAmbientLight(direction, target, (time - last) / 1_000_000_000f)
        timestamp = time
        return true
    }
}
