package com.otakup.niriko.ui.animation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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

/**
 * 非模态 sheet 的「回弹落座」弹簧（damping 0.85，临界附近无回弹）。
 *
 * 这是 v1.2.2 里唯一被保留的旧规格：`NirikoNonModalSheet` 松手后用它把面板弹回打开位置，
 * 手感比 spatialSlow 更"贴手"。v1.2.2 已把它真正接进 `NirikoNonModalSheet`
 * （原先它是零调用，见 D3 决策）。其余零调用规格（pressSpring / panelSpring /
 * countTween / tabFadeIn / tabFadeOut）已在同一批次删除，对应能力由
 * [NirikoMotionSpecs] 的六档 + [NirikoPressSpecs] 承担。
 */
fun appearSpring() = spring<Float>(
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

// ==================== 页面过渡规格 ==================

/**
 * 页面进入过渡：淡入 + 从右侧 1/4 屏宽滑入。
 * 用于详情页、搜索页等二级页面。
 * [specs] 由调用点从 MaterialTheme.motionScheme 取出（P0-B 动效统一）。
 */
fun enterFromRight(specs: NirikoMotionSpecs): EnterTransition = fadeIn(animationSpec = specs.effectsDefault()) +
    slideInHorizontally(
        animationSpec = specs.spatialDefault(),
        initialOffsetX = { it / 4 },
    )

/**
 * 页面进入过渡：淡入 + 从左侧 1/4 屏宽滑入（返回上一级时用）。
 */
fun enterFromLeft(specs: NirikoMotionSpecs): EnterTransition = fadeIn(animationSpec = specs.effectsDefault()) +
    slideInHorizontally(
        animationSpec = specs.spatialDefault(),
        initialOffsetX = { -it / 4 },
    )

/**
 * 页面退出过渡：淡出 + 向左侧滑出（下一级推入时，当前页让位）。
 */
fun exitToLeft(specs: NirikoMotionSpecs): ExitTransition = fadeOut(animationSpec = specs.effectsDefault()) +
    slideOutHorizontally(
        animationSpec = specs.spatialDefault(),
        targetOffsetX = { -it / 4 },
    )

/**
 * 页面退出过渡：淡出 + 向右侧滑出。
 */
fun exitToRight(specs: NirikoMotionSpecs): ExitTransition = fadeOut(animationSpec = specs.effectsDefault()) +
    slideOutHorizontally(
        animationSpec = specs.spatialDefault(),
        targetOffsetX = { it / 4 },
    )

// ==================== M3 SharedAxis Z（P3） ====================

/** Back returns to the previous depth instead of repeating the forward transition. */
fun popEnterSharedAxisZ(specs: NirikoMotionSpecs): EnterTransition = fadeIn(animationSpec = specs.effectsDefault()) +
    scaleIn(initialScale = 0.92f, animationSpec = specs.spatialDefault())

fun popExitSharedAxisZ(specs: NirikoMotionSpecs): ExitTransition = fadeOut(animationSpec = specs.effectsDefault()) +
    scaleOut(targetScale = 1.08f, animationSpec = specs.spatialDefault())

/**
 * 二级页进入：fade + scale 0.92→1（M3 SharedAxis Z）。
 * 与封面共享元素过渡叠加更自然，替代原先"右侧滑入"。
 */
fun enterSharedAxisZ(specs: NirikoMotionSpecs): EnterTransition = fadeIn(animationSpec = specs.effectsDefault()) +
    scaleIn(
        initialScale = 0.92f,
        animationSpec = specs.spatialDefault(),
    )

/**
 * 二级页退出：fade + scale 1→0.92。
 */
fun exitSharedAxisZ(specs: NirikoMotionSpecs): ExitTransition = fadeOut(animationSpec = specs.effectsDefault()) +
    scaleOut(
        targetScale = 0.92f,
        animationSpec = specs.spatialDefault(),
    )
