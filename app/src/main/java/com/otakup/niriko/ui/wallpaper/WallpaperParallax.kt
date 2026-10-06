package com.otakup.niriko.ui.wallpaper

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.otakup.niriko.ui.animation.LocalReduceMotion
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

internal const val WALLPAPER_PARALLAX_SCALE = 1.08f
private const val MAX_TRAVEL_DP = 18f
private const val MAX_TILT_RADIANS = 0.31415927f

internal fun wallpaperParallaxTravelPx(sizePx: Float, density: Float): Float =
    min(MAX_TRAVEL_DP * density, (sizePx * (WALLPAPER_PARALLAX_SCALE - 1f) / 2f - 1f).coerceAtLeast(0f))

/** Relative surface normal avoids Euler-angle wrapping and supports an upright or flat phone. */
internal class WallpaperParallaxFilter {
    private val baseline = FloatArray(9)
    private var calibrated = false
    private var displayRotation = 0
    private var lastTimestamp = 0L
    var x = 0f
        private set
    var y = 0f
        private set

    fun reset() {
        calibrated = false
        lastTimestamp = 0L
        x = 0f
        y = 0f
    }

    fun sample(matrix: FloatArray, rotation: Int, timestamp: Long): Boolean {
        if (matrix.size != 9 || matrix.any { !it.isFinite() }) return false
        if (!calibrated || rotation != displayRotation) {
            matrix.copyInto(baseline)
            calibrated = true
            displayRotation = rotation
            lastTimestamp = timestamp
            x = 0f
            y = 0f
            return true
        }
        if (timestamp <= lastTimestamp) return false
        val seconds = ((timestamp - lastTimestamp) / 1_000_000_000f).coerceAtMost(0.1f)
        lastTimestamp = timestamp
        // Third column of baseline-transpose * current: normal in the initial display sensor frame.
        val nx = baseline[0] * matrix[2] + baseline[3] * matrix[5] + baseline[6] * matrix[8]
        val ny = baseline[1] * matrix[2] + baseline[4] * matrix[5] + baseline[7] * matrix[8]
        val nz = baseline[2] * matrix[2] + baseline[5] * matrix[5] + baseline[8] * matrix[8]
        val targetX = (-atan2(nx, sqrt(ny * ny + nz * nz)) / MAX_TILT_RADIANS).coerceIn(-1f, 1f)
        // Sensor Y points up, while graphics-layer Y points down.
        val targetY = (atan2(ny, sqrt(nx * nx + nz * nz)) / MAX_TILT_RADIANS).coerceIn(-1f, 1f)
        val alpha = 1f - exp(-seconds / 0.12f)
        x = (x + alpha * (targetX - x)).coerceIn(-1f, 1f)
        y = (y + alpha * (targetY - y)).coerceIn(-1f, 1f)
        return true
    }
}

@Stable
internal class WallpaperParallaxState {
    var available by mutableStateOf(false)
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)

    fun reset() {
        x = 0f
        y = 0f
    }
}

internal class WallpaperParallaxSensor(
    private val manager: SensorManager,
    private val sensor: Sensor?,
    private val rotation: () -> Int,
    private val state: WallpaperParallaxState,
) : SensorEventListener {
    private val matrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val filter = WallpaperParallaxFilter()
    private var started = false

    fun start(): Boolean {
        if (started) return true
        filter.reset()
        state.reset()
        val selected = sensor ?: return false
        started = runCatching {
            manager.registerListener(this, selected, SensorManager.SENSOR_DELAY_GAME, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)
        state.available = started
        return started
    }

    fun stop() {
        if (started) manager.unregisterListener(this)
        started = false
        state.available = false
        filter.reset()
        state.reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR || event.values.size < 3) return
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val displayRotation = rotation()
        val axisX: Int
        val axisY: Int
        when (displayRotation) {
            Surface.ROTATION_90 -> { axisX = SensorManager.AXIS_Y; axisY = SensorManager.AXIS_MINUS_X }
            Surface.ROTATION_180 -> { axisX = SensorManager.AXIS_MINUS_X; axisY = SensorManager.AXIS_MINUS_Y }
            Surface.ROTATION_270 -> { axisX = SensorManager.AXIS_MINUS_Y; axisY = SensorManager.AXIS_X }
            else -> { axisX = SensorManager.AXIS_X; axisY = SensorManager.AXIS_Y }
        }
        if (!SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remapped)) return
        if (!filter.sample(remapped, displayRotation, event.timestamp)) return
        if (abs(state.x - filter.x) >= 0.001f) state.x = filter.x
        if (abs(state.y - filter.y) >= 0.001f) state.y = filter.y
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
internal fun rememberWallpaperParallax(enabled: Boolean, calibrationKey: Any): WallpaperParallaxState? {
    if (!enabled || LocalReduceMotion.current) return null
    val application = LocalContext.current.applicationContext
    val display = LocalView.current.display
    val manager = remember(application) { application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
        ?: return null
    val sensor = remember(manager) { manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) } ?: return null
    val state = remember(calibrationKey, display) { WallpaperParallaxState() }
    val source = remember(manager, sensor, display, state) {
        WallpaperParallaxSensor(manager, sensor, { display?.rotation ?: Surface.ROTATION_0 }, state)
    }
    LifecycleResumeEffect(source) {
        source.start()
        onPauseOrDispose { source.stop() }
    }
    return state
}
