package com.otakup.niriko.ui.bottombar

import com.otakup.niriko.ui.common.FloatingControlsState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B11-Step2 的运行时状态测试（Compose snapshot state 在 JVM 上可直接实例化）。
 *
 * task-25 方向裁决（真机反馈后翻转）：
 * - `dy > 0`（手指上滑、内容往后翻）→ ScrollIntent.UP → **显示**；
 * - `dy < 0`（手指下滑）→ ScrollIntent.DOWN → **隐藏**。
 * 测试里的正负号就是这条裁决的钉子：如果哪天又翻回去，前两条会立刻红。
 */
class FloatingControlsStateTest {

    @Test
    fun directionMatchesRealDeviceRuling() {
        val state = FloatingControlsState()
        state.dispatch(60f) // 上滑 → UP → 显示（初始就是显示，不应被隐藏）
        assertTrue("上滑不得隐藏（task-25 方向翻转）", state.shown)
        state.dispatch(-60f) // 下滑 → DOWN → 隐藏
        assertFalse("下滑 60dp 应隐藏", state.shown)
        state.dispatch(20f) // 上滑 → UP → 恢复
        assertTrue("上滑 20dp 应恢复", state.shown)
    }

    @Test
    fun continuousDownScrollStaysHidden() {
        // 详情页真机 bug：第二项之后就不隐藏。这里模拟连续 1000dp+ 的下滑事件，
        // 每个事件都远小于 jumpGuard，状态必须保持隐藏（且不会被「显示」误触发）。
        val state = FloatingControlsState()
        repeat(40) { state.dispatch(-30f) }
        assertFalse("连续下滑后必须保持隐藏", state.shown)
        // 已经隐藏后继续下滑不应翻转
        repeat(20) { state.dispatch(-30f) }
        assertFalse(state.shown)
    }

    @Test
    fun forcedShownBlocksScrollDrivenHiding() {
        val state = FloatingControlsState()
        state.forceShown(true)
        assertTrue(state.isForcedShown)
        state.dispatch(-200f)
        assertTrue("搜索表面展开期间不得被滚走", state.shown)

        // 即使已经隐藏，force(true) 也要立刻复位（展开那一刻）
        state.forceShown(false)
        state.dispatch(-200f)
        assertFalse(state.shown)
        state.forceShown(true)
        assertTrue(state.shown)
    }

    @Test
    fun pageSwitchResetShowsAndClearsAccumulation() {
        val state = FloatingControlsState()
        state.dispatch(-30f) // 不到 48dp 阈值
        state.reset()
        assertTrue(state.shown)
        // 复位清了累计：再 -30dp 仍不应隐藏（否则就是「跨页残留」）
        state.dispatch(-30f)
        assertTrue(state.shown)
    }

    @Test
    fun blankTapRestoreShowsAfterHidden() {
        val state = FloatingControlsState()
        state.dispatch(-200f)
        assertFalse(state.shown)
        state.restore()
        assertTrue(state.shown)
    }

    @Test
    fun forceShownIsIdempotent() {
        val state = FloatingControlsState()
        state.forceShown(true)
        state.forceShown(true)
        assertTrue(state.shown)
        state.forceShown(false)
        assertFalse(state.isForcedShown)
    }
}
