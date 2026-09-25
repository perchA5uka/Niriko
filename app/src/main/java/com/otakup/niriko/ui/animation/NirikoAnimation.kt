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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
//
// 弹簧规范（Apple Designing Fluid Interfaces 落地）：
// - 内容入场：damping 0.85（临界附近，几乎无回弹，优雅不打扰）——原 MediumBouncy(0.5) 偏弹已收敛；
// - 按压反馈：damping 0.75（轻微回弹，反馈"活着"但不弹跳过头）；
// - 仅手势携带动量的释放场景（如 Pager 翻页到位）保留更高回弹（见 NirikoBottomBar）。

/** 卡片按压缩放的弹簧动画规格（damping 0.75，轻微回弹）。 */
fun pressSpring() = spring<Float>(
    dampingRatio = 0.75f,
    stiffness = Spring.StiffnessMedium,
)

/** 列表项入场的弹簧动画规格（damping 0.85，临界附近无回弹）。 */
fun appearSpring() = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow,
)

/** 面板展开/收起的弹簧动画规格（damping 0.85）。 */
fun panelSpring() = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow,
)

// ==================== 减少动态效果 ====================

/**
 * 全局"减少动态效果"开关：系统 animator_duration_scale=0（开发者关动画）
 * 或应用内设置开启时，入场类动画应跳过/瞬时完成。
 * 由 NirikoTheme 提供（值 = 设置项 || 系统动画关闭）。
 */
val LocalReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

/** 动画是否应执行（读取 [LocalReduceMotion]）。 */
@androidx.compose.runtime.Composable
fun motionEnabled(): Boolean = !LocalReduceMotion.current

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

// ==================== M3 SharedAxis Z（P3） ====================

/**
 * 二级页进入：fade + scale 0.92→1（M3 SharedAxis Z）。
 * 与封面共享元素过渡叠加更自然，替代原先"右侧滑入"。
 */
fun enterSharedAxisZ(): EnterTransition = fadeIn(animationSpec = tween(AnimDurationNormal)) +
    scaleIn(
        initialScale = 0.92f,
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
    )

/**
 * 二级页退出：fade + scale 1→0.92。
 */
fun exitSharedAxisZ(): ExitTransition = fadeOut(animationSpec = tween(AnimDurationNormal)) +
    scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
    )
