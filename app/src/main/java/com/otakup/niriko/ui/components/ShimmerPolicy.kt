package com.otakup.niriko.ui.components

import com.otakup.niriko.data.settings.GlassEffectLevel

/**
 * 骨架屏扫光的降级策略（UI 升级计划 §五 B4）。
 *
 * 骨架扫光是**无限动画**，属于「减少动态效果」必须跳过的一类动效
 * （`ui/animation/RevealOnScroll.kt:43` 已为入场动画立下同款规矩）。
 * 下列任一条件命中即退化为**静态灰块**——形状与位置不变，只是不再动：
 *
 * 1. [reduceMotion]：应用内「减少动态效果」开启；
 * 2. [systemAnimatorOff]：系统开发者选项 `animator_duration_scale == 0`
 *    （由 `MainActivity.kt:184-191` 与 1 取并集后交给 `LocalReduceMotion`）；
 * 3. [glassEffect] 为 [GlassEffectLevel.OFF]：玻璃档位关闭意味着用户要省电 / 低开销，
 *    骨架的无限动画同样降级（计划 §五 风险 2「玻璃档位为 OFF 时骨架也同样降级」）。
 *
 * 纯函数、不依赖 Android / Compose，便于 JVM 单测
 * （风格对齐 [com.otakup.niriko.data.settings.FirstRunPolicy]）。
 */
object ShimmerPolicy {

    /**
     * @return true = 骨架可以跑扫光动画；false = 必须渲染为静态灰块。
     */
    fun shouldAnimate(
        reduceMotion: Boolean,
        systemAnimatorOff: Boolean,
        glassEffect: GlassEffectLevel,
    ): Boolean = !reduceMotion && !systemAnimatorOff && glassEffect != GlassEffectLevel.OFF
}
