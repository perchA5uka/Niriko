package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseRevealPolicyTest {
    @Test
    fun `首次组合不播放包络`() {
        // previous 为 null = 刚进页面：不能压暗，否则每次进页面都闪一下
        assertFalse(shouldPlayPhaseReveal(previous = null, next = false, reduceMotion = false))
    }

    @Test
    fun `阶段未变化不播放包络`() {
        assertFalse(shouldPlayPhaseReveal(previous = false, next = false, reduceMotion = false))
        assertTrue(shouldPlayPhaseReveal(previous = true, next = false, reduceMotion = false))
    }

    @Test
    fun `reduceMotion 下永不播放包络`() {
        assertFalse(shouldPlayPhaseReveal(previous = true, next = false, reduceMotion = true))
    }

    @Test
    fun `起始不透明度在 reduceMotion 下为全亮`() {
        assertEquals(1f, phaseRevealStartAlpha(VIEW_TOGGLE_DIM_ALPHA, reduceMotion = true), 0f)
        assertEquals(VIEW_TOGGLE_DIM_ALPHA, phaseRevealStartAlpha(VIEW_TOGGLE_DIM_ALPHA, reduceMotion = false), 0f)
        assertEquals(PHASE_REVEAL_DIM_ALPHA, phaseRevealStartAlpha(PHASE_REVEAL_DIM_ALPHA, reduceMotion = false), 0f)
    }
}
