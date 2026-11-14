package com.otakup.niriko.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 区间滑块（[GlassRangeSlider]）的纯数学回归（V-13）。
 *
 * 双圆钮滑块有两件事只能靠穷举边界证明：按下抓谁、相撞怎么处理。
 * 这两个函数从 GlassRangeSliderMath.kt 抽出就是为了可测（同 [GlassToggleMathTest] 的做法）。
 */
class GlassRangeSliderMathTest {

    private val ten = 0f..10f

    // ── 抓取（rangeSliderGrabsStart） ──────────────────────────

    @Test
    fun grabsTheNearerThumb() {
        val current = 2f..8f
        assertTrue("按在起点附近该抓起点", rangeSliderGrabsStart(60f, 300f, current, ten))
        assertFalse("按在终点附近该抓终点", rangeSliderGrabsStart(240f, 300f, current, ten))
    }

    @Test
    fun coilapsedRangeAlwaysGrabsStart() {
        // 区间为 0 宽（筛选的常见初值：未设置上下界）时抓起点，避免一按就改上界
        val collapsed = 5f..5f
        assertTrue(rangeSliderGrabsStart(0f, 300f, collapsed, ten))
        assertTrue(rangeSliderGrabsStart(300f, 300f, collapsed, ten))
        assertTrue(rangeSliderGrabsStart(150f, 300f, collapsed, ten))
    }

    @Test
    fun exactMidpointBetweenThumbsGrabsStart() {
        // 两圆钮位 2 与 8，中点 5 → 距离相等，按约定抓起点（手指微抖不会跳到另一头）
        assertTrue(rangeSliderGrabsStart(150f, 300f, 2f..8f, ten))
    }

    @Test
    fun zeroWidthTrackFallsBackToStart() {
        // 尚未测量：退回起点，不产 NaN 判定
        assertTrue(rangeSliderGrabsStart(120f, 0f, 2f..8f, ten))
        assertTrue(rangeSliderGrabsStart(120f, -5f, 2f..8f, ten))
    }

    @Test
    fun reversedValueRangeIsNotASupportedConfigurationButNeverNaNs() {
        // 业务里 valueRange 恒为升序（筛选的 0..10）；这里只钉「即使被传入反向量程也不会 NaN/
        // 除零崩溃，抓取判定仍有确定结果」，不承诺反向量程的视觉语义（Compose Slider 本身
        // 也要求 start <= end）。若将来真需要反向区间，应先拍板语义再改这三条纯函数。
        val reversed = 10f..0f
        // 占比按原始算术给出（起点映射到 80%、终点 20%），判定因此左右互换——这是「配置不合法」的
        // 确定结果，而非 NaN/除零。若将来要支持反向区间，应在这里先改语义断言。
        assertFalse(rangeSliderGrabsStart(30f, 300f, 2f..8f, reversed))
        assertTrue(rangeSliderGrabsStart(270f, 300f, 2f..8f, reversed))
    }

    // ── 结算（rangeSliderDrag） ────────────────────────────────

    @Test
    fun draggingStartBelowEndMovesOnlyStart() {
        val next = rangeSliderDrag(current = 2f..8f, nextValue = 5f, draggingStart = true)
        assertEquals(5f, next.start, 0f)
        assertEquals(8f, next.endInclusive, 0f)
    }

    @Test
    fun draggingStartPastEndStopsAtEnd() {
        // 相撞语义：原地停，不推另一个圆钮（否则用户会以为上界被自己改了）
        val next = rangeSliderDrag(current = 2f..8f, nextValue = 9.5f, draggingStart = true)
        assertEquals(8f, next.start, 0f)
        assertEquals(8f, next.endInclusive, 0f)
    }

    @Test
    fun draggingEndBelowStartStopsAtStart() {
        val next = rangeSliderDrag(current = 2f..8f, nextValue = 0f, draggingStart = false)
        assertEquals(2f, next.start, 0f)
        assertEquals(2f, next.endInclusive, 0f)
    }

    @Test
    fun draggingEndAboveStartMovesOnlyEnd() {
        val next = rangeSliderDrag(current = 2f..8f, nextValue = 6f, draggingStart = false)
        assertEquals(2f, next.start, 0f)
        assertEquals(6f, next.endInclusive, 0f)
    }

    @Test
    fun nonFiniteTargetKeepsCurrentRange() {
        // 手指在测量前落下 / 尺寸为 0：offsetToValue 可能给出 NaN 或 ±Infinity，
        // 必须原样退回而不是把区间写坏（NaN 写进 valueRange 会让后续 coerceIn 抛异常）
        val current = 2f..8f
        assertEquals(current, rangeSliderDrag(current, Float.NaN, draggingStart = true))
        assertEquals(current, rangeSliderDrag(current, Float.POSITIVE_INFINITY, draggingStart = false))
        assertEquals(current, rangeSliderDrag(current, Float.NEGATIVE_INFINITY, draggingStart = true))
    }

    // ── 占比（rangeSliderFraction） ────────────────────────────

    @Test
    fun fractionMapsEndpointsAndClamps() {
        assertEquals(0f, rangeSliderFraction(0f, ten), 0f)
        assertEquals(1f, rangeSliderFraction(10f, ten), 0f)
        assertEquals(0.5f, rangeSliderFraction(5f, ten), 0f)
        assertEquals(0f, rangeSliderFraction(-3f, ten), 0f)
        assertEquals(1f, rangeSliderFraction(30f, ten), 0f)
    }

    @Test
    fun fractionOfDegenerateRangeIsZero() {
        // 跨度 0：任何值都在同一处，返回 0 而不是除零得 Infinity
        assertEquals(0f, rangeSliderFraction(3f, 3f..3f), 0f)
        assertEquals(0f, rangeSliderFraction(3f, 2f..2f), 0f)
    }
}
