package com.otakup.niriko.ui.animation

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AmbientTiltSensorTest {
    private val manager = RuntimeEnvironment.getApplication().getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR).also { shadowOf(manager).addSensor(it) }
    private fun event(degrees: Float, time: Long): SensorEvent {
        val event = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
            isAccessible = true
        }.newInstance(4)
        val half = Math.toRadians(degrees.toDouble() / 2.0).toFloat()
        event.values[1] = sin(half)
        event.values[3] = cos(half)
        event.sensor = sensor
        event.timestamp = time
        return event
    }
    @Test fun registrationPauseDisposeAndResumeAreIdempotent() {
        val state = AmbientTiltState()
        val source = AmbientTiltSensor(manager, sensor, { 0 }, state)
        assertTrue(source.start())
        assertTrue(source.start())
        assertFalse(state.available)
        assertTrue(shadowOf(manager).hasListener(source))
        source.onSensorChanged(event(0f, 0L))
        assertTrue(state.available)
        source.onSensorChanged(event(10f, 40_000_000L))
        assertTrue(state.direction.x > 0f)
        val old = state.direction
        source.stop()
        source.stop()
        assertFalse(state.available)
        assertFalse(shadowOf(manager).hasListener(source))
        source.onSensorChanged(event(-20f, 80_000_000L))
        assertEquals(old, state.direction)
        assertTrue(source.start())
        assertTrue(state.available)
        assertEquals(old, state.direction)
        source.onSensorChanged(event(-20f, 100_000_000L))
        assertEquals(old, state.direction)
        source.stop()
    }
    @Test fun absentOrFailedSensorNeverLeavesAListener() {
        val state = AmbientTiltState()
        val absent = AmbientTiltSensor(manager, null, { 0 }, state)
        assertFalse(absent.start())
        assertFalse(state.available)
        absent.stop()
        shadowOf(manager).setForceListenersToFail(true)
        val failing = AmbientTiltSensor(manager, sensor, { 0 }, state)
        assertFalse(failing.start())
        assertFalse(state.available)
        assertFalse(shadowOf(manager).hasListener(failing))
        failing.stop()
    }
    @Test fun publicationIsRateLimitedAndStaticSamplesDoNotDrift() {
        val state = AmbientTiltState()
        val source = AmbientTiltSensor(manager, sensor, { 0 }, state)
        source.start()
        source.onSensorChanged(event(0f, 0L))
        source.onSensorChanged(event(20f, 10_000_000L))
        assertEquals(Offset.Zero, state.direction)
        source.onSensorChanged(event(20f, 40_000_000L))
        assertTrue(state.direction.x > 0f)
        source.stop()
        val static = AmbientTiltSensor(manager, sensor, { 0 }, AmbientTiltState())
        static.start()
        repeat(100) { static.onSensorChanged(event(20f, it * 40_000_000L)) }
        static.stop()
    }
    @Test fun rotationVectorAxesMatchEveryDisplayRotation() {
        val expected = listOf(Offset(0.5f, 0f), Offset(0f, -0.5f), Offset(-0.5f, 0f), Offset(0f, 0.5f))
        expected.forEachIndexed { rotation, value ->
            val state = AmbientTiltState()
            val source = AmbientTiltSensor(manager, sensor, { rotation }, state)
            source.start()
            source.onSensorChanged(event(0f, 0L))
            repeat(100) { source.onSensorChanged(event(10f, (it + 1L) * 40_000_000L)) }
            assertEquals(value.x, state.direction.x, 0.006f)
            assertEquals(value.y, state.direction.y, 0.006f)
            source.stop()
        }
    }
    @Test fun restoredLightSurvivesRegistrationButNotRegistrationFailure() {
        val original = AmbientTiltState()
        val source = AmbientTiltSensor(manager, sensor, { 0 }, original)
        source.start()
        source.onSensorChanged(event(0f, 0L))
        source.onSensorChanged(event(15f, 40_000_000L))
        source.stop()
        val saved = with(AmbientTiltState.Saver) {
            with(object : androidx.compose.runtime.saveable.SaverScope {
                override fun canBeSaved(value: Any) = true
            }) { save(original)!! }
        }
        val restored = AmbientTiltState.Saver.restore(saved)!!
        assertFalse(restored.available)
        assertEquals(original.direction, restored.direction)
        val next = AmbientTiltSensor(manager, sensor, { 1 }, restored)
        assertTrue(next.start())
        assertTrue(restored.available)
        assertEquals(original.direction, restored.direction)
        next.onSensorChanged(event(15f, 100_000_000L))
        assertEquals(original.direction, restored.direction)
        next.stop()
        shadowOf(manager).setForceListenersToFail(true)
        assertFalse(next.start())
        assertFalse(restored.available)
        assertEquals(original.direction, restored.direction)
        assertFalse(shadowOf(manager).hasListener(next))
        assertFalse(AmbientTiltSensor(manager, null, { 0 }, restored).start())
        assertFalse(restored.available)
    }

    @Test fun pageDemandOrReducedMotionStopsRegistration() {
        val state = AmbientTiltState()
        val source = AmbientTiltSensor(manager, sensor, { 0 }, state)
        for (enabled in listOf(true, false, true, false)) {
            if (ambientReflectionEnabled(enabled, true, false)) source.start() else source.stop()
            assertEquals(enabled, shadowOf(manager).hasListener(source))
        }
        if (!ambientReflectionEnabled(true, true, true)) source.stop()
        assertFalse(shadowOf(manager).hasListener(source))
    }
}
