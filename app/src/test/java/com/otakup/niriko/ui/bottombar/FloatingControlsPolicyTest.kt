package com.otakup.niriko.ui.bottombar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B11-Step1 纯策略单测：方向判定 / 反向清零 / 抖动 / jumpGuard / 复位。
 *
 * 阈值：向下 48dp 隐藏、向上 16dp 恢复、单事件 > 1600px 视为程序化跳转。
 * 全部用 density = 1 跑（1px = 1dp），另有一条 2.75 的 dp→px 边界。
 */
class FloatingControlsPolicyTest {

    private val cfg = FloatingBarConfig.Default

    private fun scroll(state: FloatingBarState, intent: ScrollIntent, px: Float, density: Float = 1f) =
        state.onScroll(intent, px, cfg, density)

    @Test
    fun downPastThresholdHides() {
        val s = scroll(FloatingBarState(), ScrollIntent.DOWN, 60f)
        assertFalse("向下 60dp 应立即隐藏", s.shown)
        assertEquals(0f, s.accumulatePx, 0.001f)
        assertEquals(ScrollIntent.DOWN, s.lastIntent)
    }

    @Test
    fun upPastThresholdShowsAgain() {
        val hidden = scroll(FloatingBarState(), ScrollIntent.DOWN, 60f)
        val shown = scroll(hidden, ScrollIntent.UP, 20f)
        assertTrue("向上 20dp 应立即恢复", shown.shown)
        assertEquals(0f, shown.accumulatePx, 0.001f)
    }

    @Test
    fun reverseDirectionStartsFromZeroInsteadOfPayingBack() {
        // 向下 30（还不到 48）后向上 10：不能先抵掉 30 才对，应该在新方向上重计 10
        val down = scroll(FloatingBarState(), ScrollIntent.DOWN, 30f)
        assertEquals(30f, down.accumulatePx, 0.001f)
        val up = scroll(down, ScrollIntent.UP, 10f)
        assertTrue(up.shown)
        assertEquals("换方向后累计从 0 重新计 10", 10f, up.accumulatePx, 0.001f)
        // 再向上 6 就凑满 16 → 但仍已显示（幂等，不应产生状态抖动）
        val upMore = scroll(up, ScrollIntent.UP, 6f)
        assertTrue(upMore.shown)
        // 隐藏状态下：向下 30 + 向上 10 还不恢复，再向上 6 也不够 16 → 仍隐藏
        val hidden = scroll(FloatingBarState(), ScrollIntent.DOWN, 60f)
        val hiddenUp10 = scroll(hidden, ScrollIntent.UP, 10f)
        assertFalse(hiddenUp10.shown)
        assertEquals(10f, hiddenUp10.accumulatePx, 0.001f)
        assertTrue("再向上 6dp（累计 16）应恢复", scroll(hiddenUp10, ScrollIntent.UP, 6f).shown)
    }

    @Test
    fun crossEventAccumulationStillHides() {
        // 事件粒度无关：30 + 30 = 60 ≥ 48
        val first = scroll(FloatingBarState(), ScrollIntent.DOWN, 30f)
        assertTrue(first.shown)
        val second = scroll(first, ScrollIntent.DOWN, 30f)
        assertFalse(second.shown)
    }

    @Test
    fun jitterDoesNotFlipState() {
        // 10 down / 10 up 反复抖：累计永远 ≤ 16，状态不得翻转
        var s = FloatingBarState()
        repeat(20) {
            s = scroll(s, ScrollIntent.DOWN, 10f)
            assertTrue("抖动中不得隐藏", s.shown)
            s = scroll(s, ScrollIntent.UP, 10f)
            assertTrue("抖动中不得隐藏", s.shown)
        }
    }

    @Test
    fun programmaticJumpOnlyClearsAccumulator() {
        // 向下 30 后程序化回顶（2000px 单事件）：状态不变、累计清零
        val down = scroll(FloatingBarState(), ScrollIntent.DOWN, 30f)
        val jumped = scroll(down, ScrollIntent.DOWN, 2000f)
        assertTrue("jumpGuard 不改变显隐", jumped.shown)
        assertEquals(0f, jumped.accumulatePx, 0.001f)

        // 已隐藏时的程序化跳转同样不改状态
        val hidden = scroll(FloatingBarState(), ScrollIntent.DOWN, 60f)
        assertFalse(scroll(hidden, ScrollIntent.UP, 2000f).shown)
    }

    @Test
    fun blankTapAndPageSwitchResetToShown() {
        val hidden = scroll(FloatingBarState(), ScrollIntent.DOWN, 60f)
        assertTrue(hidden.onBlankTap().shown)
        assertEquals(0f, hidden.onBlankTap().accumulatePx, 0.001f)
        assertTrue(hidden.onPageSwitch().shown)
        assertEquals(0f, hidden.onPageSwitch().accumulatePx, 0.001f)
        assertEquals("复位后方向也要清空", null, hidden.onPageSwitch().lastIntent)
    }

    @Test
    fun zeroDeltaIsNoOp() {
        val s = scroll(FloatingBarState(), ScrollIntent.DOWN, 30f)
        assertEquals(s, scroll(s, ScrollIntent.DOWN, 0f))
    }

    @Test
    fun thresholdsAreDpTimesDensity() {
        // 48dp @ 2.75 = 132px：恰好等于阈值 → 隐藏；少 0.1px 不隐藏
        assertEquals(132f, cfg.hideThresholdPx(2.75f), 0.001f)
        assertEquals(44f, cfg.showThresholdPx(2.75f), 0.001f)
        assertTrue("131.9px 未到阈值，应保持显示", scroll(FloatingBarState(), ScrollIntent.DOWN, 131.9f, 2.75f).shown)
        assertFalse("132px 恰好到阈值，应隐藏", scroll(FloatingBarState(), ScrollIntent.DOWN, 132f, 2.75f).shown)
    }
}
