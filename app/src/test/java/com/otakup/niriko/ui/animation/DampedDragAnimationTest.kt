package com.otakup.niriko.ui.animation

import androidx.compose.runtime.MonotonicFrameClock
import com.otakup.niriko.ui.bottombar.utils.DampedDragAnimation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DampedDragAnimationTest {
    private val clock = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(System.nanoTime())
        }
    }

    @Test fun dragUpdatesUseSpringAndClampTarget() = runBlocking(clock) {
        val animation = DampedDragAnimation(this, 0f, 0f..3f, 0.001f, 1f, 1.3f, {}, {}, { _, _ -> })
        animation.updateValue(2.25f)
        assertEquals(2.25f, animation.targetValue, 0f)
        org.junit.Assert.assertTrue(animation.value < 2.25f)
        animation.updateValue(9f)
        assertEquals(3f, animation.targetValue, 0f)
        delay(1_500)
        assertEquals(3f, animation.value, 0.001f)
    }

    @Test fun programmaticSelectionShowsPressBeforeRecovering() = runBlocking(clock) {
        val animation = DampedDragAnimation(this, 0f, 0f..3f, 0.001f, 1f, 78f / 56f, {}, {}, { _, _ -> })
        animation.animateToValue(3f)
        // 不在固定 130ms 处断言：按压是 spring(1, 1000)，pressProgress 何时越过 0.9、
        // releaseAfterSettle 何时开始收，取决于帧调度与实际耗时（本测试曾在并行构建时偶发失败）。
        // 改成「轮询记录峰值」：语义是「程序化选中必须先有按压高峰，再回到静止」，而不是「第 130ms 时还在按」。
        var peakPress = 0f
        var peakScale = 1f
        withTimeout(2_000) {
            while (animation.isPressed || animation.pressProgress > 0.001f) {
                if (animation.pressProgress > peakPress) peakPress = animation.pressProgress
                if (animation.scaleX > peakScale) peakScale = animation.scaleX
                delay(8)
            }
        }
        org.junit.Assert.assertTrue("程序化选中应出现按压反馈（峰值 " + peakPress + "）", peakPress > 0.2f)
        org.junit.Assert.assertTrue("程序化选中应出现放大反馈（峰值 " + peakScale + "）", peakScale > 1.05f)
        delay(1_500)
        assertEquals(3f, animation.value, 0.001f)
        assertEquals(0f, animation.pressProgress, 0.002f)
        assertEquals(1f, animation.scaleX, 0.002f)
    }

    @Test fun singleTabRangeHasFiniteProgress() = runBlocking(clock) {
        val animation = DampedDragAnimation(this, 0f, 0f..0f, 0.001f, 1f, 1.3f, {}, {}, { _, _ -> })
        animation.updateValue(1f)
        assertEquals(0f, animation.progress, 0f)
    }

    @Test fun regrabCancelsOldSettleAndReleaseRestoresShape() = runBlocking(clock) {
        withTimeout(5_000) {
            val animation = DampedDragAnimation(this, 0f, 0f..3f, 0.001f, 1f, 1.3f, {}, {}, { _, _ -> })
            animation.press()
            animation.updateValue(2f)
            animation.animateToValue(3f)
            animation.press()
            animation.updateValue(0.25f)
            animation.release()
            delay(1_500)
            assertEquals(0.25f, animation.value, 0.001f)
            assertEquals(0f, animation.pressProgress, 0.002f)
            assertEquals(1f, animation.scaleX, 0.002f)
            assertEquals(1f, animation.scaleY, 0.002f)
            assertEquals(0f, animation.velocity, 0.05f)
        }
    }
}
