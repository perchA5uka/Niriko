package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评分触感与满分确认规则（F15 / F16）的单测。
 *
 * 这一批真正容易出错的是**触发条件**（不是动画本身）：
 * 同一刻度重复响、恢复已保存的满分自己闪一下、快速拖动连响一串。
 */
class RatingFeedbackPolicyTest {
    @Test
    fun indicesAreComparedWithoutQuantizingAgain() {
        assertFalse(RatingFeedbackPolicy.crossesTickIndex(15, RatingFeedbackPolicy.tickIndex(7.5f)))
        assertFalse(RatingFeedbackPolicy.crossesTickIndex(15, RatingFeedbackPolicy.tickIndex(7.6f)))
        assertTrue(RatingFeedbackPolicy.crossesTickIndex(15, RatingFeedbackPolicy.tickIndex(8f)))
        assertFalse(RatingFeedbackPolicy.crossesTickIndex(20, RatingFeedbackPolicy.tickIndex(10f)))
    }


    @Test
    fun `跨到新刻度才触发_同一刻度抖动不重复`() {
        // 1.0 → 1.5：跨了一格
        assertTrue(RatingFeedbackPolicy.crossesTick(1.0f, 1.5f))
        // 1.5 → 1.5：没动
        assertFalse(RatingFeedbackPolicy.crossesTick(1.5f, 1.5f))
        // 1.5 → 1.6：还是同一格（滑块本身按 0.5 吸附，这一条防的是浮点抖动）
        assertFalse(RatingFeedbackPolicy.crossesTick(1.5f, 1.6f))
        // 1.5 → 1.76：四舍五入到 2.0，跨了一格
        assertTrue(RatingFeedbackPolicy.crossesTick(1.5f, 1.76f))
    }

    @Test
    fun `快速拖动跨多格也只发一声触感`() {
        assertEquals(5, RatingFeedbackPolicy.ticksCrossed(0f, 2.5f))
        assertEquals(1, RatingFeedbackPolicy.hapticCountFor(0f, 2.5f))
        assertEquals(0, RatingFeedbackPolicy.hapticCountFor(2.5f, 2.5f))
        assertEquals(1, RatingFeedbackPolicy.hapticCountFor(9.5f, 10f))
    }

    @Test
    fun `刻度索引与吸附对齐`() {
        assertEquals(0, RatingFeedbackPolicy.tickIndex(0f))
        assertEquals(1, RatingFeedbackPolicy.tickIndex(0.5f))
        assertEquals(20, RatingFeedbackPolicy.tickIndex(10f))
        assertEquals("1.1 吸附到 1.0（round(2.2)=2 格）", 1.0f, RatingFeedbackPolicy.snapped(1.1f), 0.001f)
        assertEquals(2.0f, RatingFeedbackPolicy.snapped(1.8f), 0.001f)
        // 越界被夹住
        assertEquals(10f, RatingFeedbackPolicy.snapped(12f))
        assertEquals(0f, RatingFeedbackPolicy.snapped(-3f))
    }

    @Test
    fun `满分确认_只在松手且这一拖把自己推到满分时触发`() {
        // 9.5 → 10：应该庆祝
        assertTrue(RatingFeedbackPolicy.shouldCelebratePerfect(valueAtRelease = 10f, valueAtDragStart = 9.5f))
        // 本来就是 10（打开面板）：不该庆祝
        assertFalse(RatingFeedbackPolicy.shouldCelebratePerfect(valueAtRelease = 10f, valueAtDragStart = 10f))
        // 松手在 9.5：不该庆祝
        assertFalse(RatingFeedbackPolicy.shouldCelebratePerfect(valueAtRelease = 9.5f, valueAtDragStart = 8f))
        // 从 10 往回拖再松手在 10（拖回满分）：起点是满分 → 不庆祝（避免来回蹭反复闪）
        assertFalse(RatingFeedbackPolicy.shouldCelebratePerfect(valueAtRelease = 10f, valueAtDragStart = 10f))
    }

    @Test
    fun `刻度的浮点边界稳定`() {
        // 9.75 → 10（四舍五入）：起点 9.75 的刻度是 10… 因此不算「从非满分进入」
        assertEquals(20, RatingFeedbackPolicy.tickIndex(9.75f))
        assertFalse(RatingFeedbackPolicy.shouldCelebratePerfect(10f, 9.75f))
        // 但 9.74 的刻度是 19 → 从非满分进入满分
        assertEquals(19, RatingFeedbackPolicy.tickIndex(9.74f))
        assertTrue(RatingFeedbackPolicy.shouldCelebratePerfect(10f, 9.74f))
    }

    @Test
    fun `减少动态效果时只保留静态反馈`() {
        assertTrue(RatingFeedbackPolicy.shouldAnimateScale(reduceMotion = false))
        assertFalse("减少动态效果时不缩放（颜色仍会变，反馈不会只剩触感）", RatingFeedbackPolicy.shouldAnimateScale(reduceMotion = true))
        assertTrue(RatingFeedbackPolicy.CELEBRATE_SCALE > 1f)
        assertTrue(RatingFeedbackPolicy.CELEBRATE_DURATION_MS in 300..900)
    }

    @Test
    fun `非法步长不会崩`() {
        assertEquals(0, RatingFeedbackPolicy.tickIndex(5f, step = 0f))
        assertFalse(RatingFeedbackPolicy.crossesTick(1f, 2f, step = 0f))
        assertEquals(0f, RatingFeedbackPolicy.snapped(5f, step = -1f))
    }
}
