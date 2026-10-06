package com.otakup.niriko.ui.animation

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect

@Stable
internal class AmbientTiltState(initial: Offset = Offset.Zero) {
    var direction by mutableStateOf(clampAmbientLight(initial))
        internal set
    var available by mutableStateOf(false)
        internal set
    internal var hasValidSample = false

    companion object {
        val Saver = Saver<AmbientTiltState, List<Float>>(
            save = { listOf(it.direction.x, it.direction.y, if (it.hasValidSample) 1f else 0f) },
            restore = { values ->
                AmbientTiltState(Offset(values[0], values[1])).also {
                    it.hasValidSample = values.getOrNull(2) == 1f
                }
            },
        )
    }
}

internal val LocalAmbientTilt = staticCompositionLocalOf<AmbientTiltState?> { null }

@Stable
internal class AmbientTiltDemand {
    var visibleRarity by mutableStateOf(false)
}

internal val LocalAmbientTiltDemand = staticCompositionLocalOf<AmbientTiltDemand?> { null }

/** One listener per active page, never one listener per card. */
internal class AmbientTiltSensor(
    private val manager: SensorManager,
    private val sensor: Sensor?,
    private val rotation: () -> Int,
    private val state: AmbientTiltState,
) : SensorEventListener {
    private val matrix = FloatArray(9)
    private var filter = AmbientTiltFilter()
    private var started = false
    private var lastPublished: Long? = null

    fun start(): Boolean {
        if (started) return true
        state.available = false
        val selected = sensor ?: return false
        filter = AmbientTiltFilter(state.direction)
        lastPublished = null
        started = runCatching {
            manager.registerListener(this, selected, 20_000, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)
        // Fresh sources wait for a valid sample; restored sources keep their displayed light.
        state.available = started && state.hasValidSample
        return started
    }

    fun stop() {
        if (started) manager.unregisterListener(this)
        started = false
        state.available = false
        lastPublished = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR || event.values.size < 3 ||
            event.values.any { !it.isFinite() }) return
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        if (!filter.sample(matrix, rotation(), event.timestamp)) return
        state.hasValidSample = true
        val last = lastPublished
        if (last != null && event.timestamp - last < AMBIENT_PUBLISH_INTERVAL_NS) return
        if (!state.available || ambientLightChanged(state.direction, filter.direction)) {
            state.direction = filter.direction
            state.available = true
            lastPublished = event.timestamp
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
internal fun rememberAmbientTilt(active: Boolean, visibleRarity: Boolean): AmbientTiltState {
    val state = rememberSaveable(saver = AmbientTiltState.Saver) { AmbientTiltState() }
    val application = LocalContext.current.applicationContext
    val view = LocalView.current
    val manager = remember(application) { application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val source = remember(manager, sensor, view, state) {
        manager?.let { AmbientTiltSensor(it, sensor, { view.display?.rotation ?: Surface.ROTATION_0 }, state) }
    }
    if (source != null && ambientReflectionEnabled(active, visibleRarity, LocalReduceMotion.current)) {
        LifecycleResumeEffect(source) {
            source.start()
            onPauseOrDispose { source.stop() }
        }
    }
    return state
}
