package com.otakup.niriko.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.2.2 K1：开关拖拽数学（[glassToggleTargetOffset] / [glassToggleDragOffset] /
 * [glassToggleValueForOffset]）的纯断言。
 *
 * 为什么这些用例值得写：修「拖不动」时最容易写对的只有「拖到底会开」，真正会出错的是
 * 三个边界 —— ①半程正好 0.5 该不该翻面；②甩动速度只过阈值一点点；③行程被夹紧后反向甩。
 * 这三条在真机上都能「看起来正常」，只有单测能钉住。
 */
class GlassToggleMathTest {

    // 真实尺寸：内缩后可移动区 46dp、圆钮 24dp ⇒ 行程 22dp（density 3 时约 66px）。
    private val innerTrackPx = 138f
    private val thumbPx = 72f
    private val travelPx = 66f

    // ── 目标位移 ─────────────────────────────────────────────

    @Test
    fun targetOffsetMovesExactlyTheInnerTrackMinusThumb() {
        assertEquals(66f, glassToggleTargetOffset(innerTrackPx, thumbPx, on = true), 0f)
        assertEquals(0f, glassToggleTargetOffset(innerTrackPx, thumbPx, on = false), 0f)
    }

    @Test
    fun targetOffsetNeverGoesNegativeWhenTrackIsNarrowerThanThumb() {
        // 轨道比圆钮还窄：负位移会把圆钮推出玻璃胶囊左侧
        assertEquals(0f, glassToggleTargetOffset(60f, 72f, on = true), 0f)
        assertEquals(0f, glassToggleTargetOffset(0f, 0f, on = true), 0f)
    }

    @Test
    fun targetOffsetTreatsUnmeasuredWidthAsZero() {
        // 首帧 width 还没测量到（NaN）时不能把 NaN 传进 Animatable/translationX
        assertEquals(0f, glassToggleTargetOffset(Float.NaN, thumbPx, on = true), 0f)
    }

    // ── 跟手位移 ─────────────────────────────────────────────

    @Test
    fun dragOffsetAddsDeltaButStaysInsideTravel() {
        assertEquals(20f, glassToggleDragOffset(0f, 20f, travelPx), 0f)
        assertEquals(66f, glassToggleDragOffset(60f, 20f, travelPx), 0f)
        assertEquals(0f, glassToggleDragOffset(10f, -40f, travelPx), 0f)
    }

    @Test
    fun dragOffsetKeepsStartWhenDeltaIsDegenerate() {
        // 拖拽中偶尔会来 NaN delta（事件坐标系切换），此时保持原位比抖回 0 更不刺眼
        assertEquals(30f, glassToggleDragOffset(30f, Float.NaN, travelPx), 0f)
        assertEquals(5f, glassToggleDragOffset(Float.NaN, 5f, travelPx), 0f)
    }

    @Test
    fun dragOffsetCollapsesToZeroWhenThereIsNoTravel() {
        assertEquals(0f, glassToggleDragOffset(30f, 10f, travelPx = 0f), 0f)
        assertEquals(0f, glassToggleDragOffset(30f, 10f, travelPx = Float.NaN), 0f)
    }

    // ── 松手结算：位置 ───────────────────────────────────────

    @Test
    fun slowReleaseSettlesByPosition() {
        assertFalse(glassToggleValueForOffset(travelPx * 0.2f, travelPx, 0f))
        assertTrue(glassToggleValueForOffset(travelPx * 0.8f, travelPx, 0f))
    }

    @Test
    fun exactlyHalfWayStaysOff() {
        // 半程是边界：> 0.5 才翻面，正好一半保持关闭（避免手指抖一下就翻面）
        assertFalse(glassToggleValueForOffset(travelPx / 2f, travelPx, 0f))
        assertTrue(glassToggleValueForOffset(travelPx * 0.5001f, travelPx, 0f))
    }

    // ── 松手结算：速度优先 ───────────────────────────────────

    @Test
    fun fastFlickWinsEvenWhenPositionIsAlmostClosed() {
        assertTrue(glassToggleValueForOffset(0f, travelPx, 1000f))
        assertFalse(glassToggleValueForOffset(travelPx, travelPx, -1000f))
    }

    @Test
    fun thresholdItselfCountsAsFlick() {
        // 阈值是闭区间：刚到 900 px/s 就按方向结算，否则「快速轻扫」会时灵时不灵
        assertTrue(glassToggleValueForOffset(0f, travelPx, 900f))
        assertFalse(glassToggleValueForOffset(travelPx, travelPx, -900f))
        assertTrue(glassToggleValueForOffset(travelPx * 0.8f, travelPx, 899f))
    }

    @Test
    fun callerCanOverrideSettleVelocity() {
        // 行程很短（小屏 / 大字号缩放）时控件可以调高阈值；纯函数支持覆盖，不必复制逻辑
        assertFalse(glassToggleValueForOffset(0f, travelPx, 1000f, settleVelocityPxPerSec = 2000f))
        assertTrue(glassToggleValueForOffset(travelPx * 0.6f, travelPx, 1000f, settleVelocityPxPerSec = 2000f))
    }

    @Test
    fun degenerateInputsNeverFlipTheSwitch() {
        assertFalse(glassToggleValueForOffset(0f, travelPx = 0f, velocityPxPerSec = 0f))
        assertFalse(glassToggleValueForOffset(Float.NaN, travelPx, 0f))
        // NaN 速度按 0 处理：位置占 80% 仍然翻面，说明 NaN 没有把整条判定短路
        assertTrue(glassToggleValueForOffset(travelPx * 0.8f, travelPx, Float.NaN))
        // 非正的 settle 阈值回落到常量 900，不能变成「永远按速度结算」或除零
        assertTrue(glassToggleValueForOffset(travelPx * 0.8f, travelPx, 0f, settleVelocityPxPerSec = 0f))
    }
}
