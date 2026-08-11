package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

// ==================== 时长常量（ms） ====================

/** 极短动画：按压反馈、悬停状态切换。 */
const val AnimDurationShort = 200

/** 标准动画时长：列表项入场、面板展开、页面过渡。 */
const val AnimDurationNormal = 300

/** 较长动画：图表绘制、数字滚动。 */
const val AnimDurationLong = 500

// ==================== 缓动曲线 ====================

/** 默认缓动曲线 — 与 Material Motion 一致，Kazumi 也使用 easeInOut 风格。 */
val AnimEasingDefault: Easing = FastOutSlowInEasing

// ==================== 动画规格工厂 ==================

/** 卡片按压缩放的弹簧动画规格。 */
fun pressSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** 列表项入场的弹簧动画规格。 */
fun appearSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** 面板展开/收起的弹簧动画规格。 */
fun panelSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** 数字/值变化的 tween 规格——平滑计数。 */
fun countTween() = tween<Float>(durationMillis = AnimDurationLong, easing = AnimEasingDefault)

// ==================== 页面过渡规格 ==================

/**
 * 页面进入过渡：淡入 + 从右侧 1/4 屏宽滑入。
 * 用于详情页、搜索页等二级页面。
 */
fun enterFromRight(): EnterTransition = fadeIn(animationSpec = tween(AnimDurationNormal)) +
    slideInHorizontally(
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
        initialOffsetX = { it / 4 },
    )

/**
 * 页面退出过渡：淡出 + 向右侧滑出。
 */
fun exitToRight(): ExitTransition = fadeOut(animationSpec = tween(AnimDurationNormal)) +
    slideOutHorizontally(
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
        targetOffsetX = { it / 4 },
    )

/**
 * 底部 Tab 切换过渡：仅淡入淡出，无滑动。
 */
val tabFadeIn: EnterTransition = fadeIn(animationSpec = tween(AnimDurationShort))
val tabFadeOut: ExitTransition = fadeOut(animationSpec = tween(AnimDurationShort))
