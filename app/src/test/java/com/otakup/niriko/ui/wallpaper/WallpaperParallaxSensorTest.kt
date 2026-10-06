package com.otakup.niriko.ui.wallpaper

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WallpaperParallaxSensorTest {
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

    @Test fun registerStopAndResumeAreIdempotentAndResetState() {
        val state = WallpaperParallaxState()
        val source = WallpaperParallaxSensor(manager, sensor, { Surface.ROTATION_0 }, state)
        assertTrue(source.start())
        assertTrue(source.start())
        assertTrue(shadowOf(manager).hasListener(source))
        source.onSensorChanged(event(0f, 0L))
        source.onSensorChanged(event(10f, 20_000_000L))
        assertTrue(state.x < 0f)
        source.stop()
        source.stop()
        assertFalse(shadowOf(manager).hasListener(source))
        assertFalse(state.available)
        assertEquals(0f, state.x, 0f)
        source.onSensorChanged(event(10f, 40_000_000L))
        assertEquals(0f, state.x, 0f)
        assertTrue(source.start())
        source.onSensorChanged(event(10f, 60_000_000L))
        assertEquals(0f, state.x, 0f)
        source.stop()
    }

    @Test fun missingSensorFallsBackToStaticWallpaper() {
        val state = WallpaperParallaxState()
        val source = WallpaperParallaxSensor(manager, null, { 0 }, state)
        assertFalse(source.start())
        assertFalse(state.available)
        assertFalse(shadowOf(manager).hasListener(source))
        source.stop()
    }

    @Test fun failedRegistrationFallsBackWithoutLeavingAListener() {
        shadowOf(manager).setForceListenersToFail(true)
        val state = WallpaperParallaxState()
        val source = WallpaperParallaxSensor(manager, sensor, { 0 }, state)
        assertFalse(source.start())
        assertFalse(state.available)
        assertFalse(shadowOf(manager).hasListener(source))
        source.stop()
    }

    @Test fun deviceAxesRemapCorrectlyForEveryScreenRotation() {
        for (rotation in 0..3) {
            val state = WallpaperParallaxState()
            val source = WallpaperParallaxSensor(manager, sensor, { rotation }, state)
            assertTrue(source.start())
            source.onSensorChanged(event(0f, 0L))
            repeat(100) { source.onSensorChanged(event(9f, (it + 1L) * 20_000_000L)) }
            when (rotation) {
                0 -> { assertEquals(-0.5f, state.x, 0.002f); assertEquals(0f, state.y, 0.002f) }
                1 -> { assertEquals(0f, state.x, 0.002f); assertEquals(0.5f, state.y, 0.002f) }
                2 -> { assertEquals(0.5f, state.x, 0.002f); assertEquals(0f, state.y, 0.002f) }
                3 -> { assertEquals(0f, state.x, 0.002f); assertEquals(-0.5f, state.y, 0.002f) }
            }
            source.stop()
        }
    }
}
