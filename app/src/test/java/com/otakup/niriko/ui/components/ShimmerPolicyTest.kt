package com.otakup.niriko.ui.components

import com.otakup.niriko.data.settings.GlassEffectLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ShimmerPolicy] 的 JVM 单测（UI 升级计划 §五 B4）。
 *
 * 关键回归：骨架扫光是无限动画，只要「应用内减少动态效果」「系统关闭动画」
 * 「玻璃档位 OFF」任一命中，就必须退化为静态灰块 —— 不许再出现扫光/呼吸。
 */
class ShimmerPolicyTest {

    @Test
    fun allClear_animatesShimmer() {
        assertTrue(
            ShimmerPolicy.shouldAnimate(
                reduceMotion = false,
                systemAnimatorOff = false,
                glassEffect = GlassEffectLevel.FULL,
            ),
        )
    }

    @Test
    fun reduceMotion_disablesShimmer() {
        assertFalse(
            ShimmerPolicy.shouldAnimate(
                reduceMotion = true,
                systemAnimatorOff = false,
                glassEffect = GlassEffectLevel.FULL,
            ),
        )
    }

    @Test
    fun systemAnimatorOff_disablesShimmer() {
        assertFalse(
            ShimmerPolicy.shouldAnimate(
                reduceMotion = false,
                systemAnimatorOff = true,
                glassEffect = GlassEffectLevel.FULL,
            ),
        )
    }

    @Test
    fun bothMotionSwitchesOff_disablesShimmer() {
        assertFalse(
            ShimmerPolicy.shouldAnimate(
                reduceMotion = true,
                systemAnimatorOff = true,
                glassEffect = GlassEffectLevel.FULL,
            ),
        )
    }

    @Test
    fun glassFull_keepsShimmer() {
        assertTrue(
            ShimmerPolicy.shouldAnimate(
                reduceMotion = false,
                systemAnimatorOff = false,
                glassEffect = GlassEffectLevel.FULL,
            ),
        )
    }

    @Test
    fun glassReduced_keepsShimmer() {
        assertTrue(
            ShimmerPolicy.shouldAnimate(
                reduceMotion = false,
                systemAnimatorOff = false,
                glassEffect = GlassEffectLevel.REDUCED,
            ),
        )
    }

    @Test
    fun glassOff_disablesShimmer() {
        assertFalse(
            ShimmerPolicy.shouldAnimate(
                reduceMotion = false,
                systemAnimatorOff = false,
                glassEffect = GlassEffectLevel.OFF,
            ),
        )
    }

    @Test
    fun glassOffAndReduceMotion_disablesShimmer() {
        assertFalse(
            ShimmerPolicy.shouldAnimate(
                reduceMotion = true,
                systemAnimatorOff = true,
                glassEffect = GlassEffectLevel.OFF,
            ),
        )
    }
}
