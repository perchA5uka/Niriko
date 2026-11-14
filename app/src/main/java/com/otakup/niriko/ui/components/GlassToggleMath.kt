package com.otakup.niriko.ui.components

import kotlin.math.abs

// 玻璃开关的拖拽数学（纯函数，JVM 单测可直接覆盖，见同包 GlassToggleMathTest）。
//
// 为什么需要它：v1.2.2 之前开关只有 animateDpAsState + clickable —— 手指一旦拖出 touch slop，
// 松手时的 tap 就被取消，表现出来就是「怎么拖都不切换」。修复要引入跟手拖拽，而拖拽一旦开始
// 就必须回答「松手落在哪一边」，这条判定必须可测，否则只能靠真机反复试（旧版连算式都埋在
// animateDpAsState 的 targetValue 里）。
//
// 为什么全部用 px：拖拽 delta 与 velocity 天生是 px，若换算回 dp 再比较，每帧都有一次舍入，
// 斜着拖长距离时 thumb 会慢慢漂；dp→px 只在控件入口用 LocalDensity 做一次。
// （写成行注释而不是 KDoc：文件头 KDoc 后面接的是另一段 KDoc，ktlint 判定为 dangling toplevel KDoc。）

/** 松手时「按速度结算」的速度阈值（px/s）。低于它只看位置：慢拖到一半以上才翻面。 */
internal const val GLASS_TOGGLE_SETTLE_VELOCITY_PX_PER_SEC = 900f

/**
 * 圆钮的目标位移（px）。
 *
 * @param trackWidthPx **内缩后**的可移动轨道宽度（= 轨道外宽 − 两侧内缩），不是控件外宽：
 *   圆钮左边缘对齐内缩区起点，所以行程 = 可移动宽度 − 圆钮自身宽度。
 * @param thumbWidthPx 圆钮宽度；轨道比圆钮还窄（异常尺寸 / 竖排误用）时夹到 0，
 *   不能返回负值——负偏移会把圆钮推出玻璃胶囊左侧。
 */
internal fun glassToggleTargetOffset(
    trackWidthPx: Float,
    thumbWidthPx: Float,
    on: Boolean,
): Float {
    if (!on) return 0f
    if (!trackWidthPx.isFinite() || !thumbWidthPx.isFinite()) return 0f
    return (trackWidthPx - thumbWidthPx).coerceAtLeast(0f)
}

/**
 * 拖动中的跟手位移：起点 + 累计 drag delta，并夹在 0..[travelPx] 之间。
 *
 * 为什么必须逐次夹紧而不是只在松手时夹：拖动中若不夹，thumb 会被拖出轨道（视觉上脱离玻璃），
 * 且松手结算时 drag/travel 会远大于 1，反方向甩不回来。
 */
internal fun glassToggleDragOffset(
    startOffsetPx: Float,
    dragAmountPx: Float,
    travelPx: Float,
): Float {
    val travel = if (travelPx.isFinite()) travelPx.coerceAtLeast(0f) else 0f
    if (travel <= 0f) return 0f
    val base = if (startOffsetPx.isFinite()) startOffsetPx else 0f
    val delta = if (dragAmountPx.isFinite()) dragAmountPx else 0f
    return (base + delta).coerceIn(0f, travel)
}

/**
 * 松手结算：给定「离关闭位的距离」（即当前位移，0 = 关、[travelPx] = 开），返回新的开关值。
 *
 * 规则（与 MiuiX / iOS 开关一致的两段式）：
 * 1. 速度优先：|v| ≥ [settleVelocityPxPerSec] 时按甩动方向结算，即使手指其实只挪了几个 px
 *    —— 这是「快速轻扫」能切换的唯一来源；
 * 2. 否则按位置：超过行程一半（严格 > 0.5）才翻面，正好 0.5 保持关闭
 *    （半程是边界，不是「过半」，这样轻点右半边拖回原位的抖动不会翻面）。
 *
 * @param velocityPxPerSec 松手速度，向右为正（开的方向）
 */
internal fun glassToggleValueForOffset(
    dragPxFromOff: Float,
    travelPx: Float,
    velocityPxPerSec: Float,
    settleVelocityPxPerSec: Float = GLASS_TOGGLE_SETTLE_VELOCITY_PX_PER_SEC,
): Boolean {
    val settle = if (settleVelocityPxPerSec.isFinite() && settleVelocityPxPerSec > 0f) {
        settleVelocityPxPerSec
    } else {
        GLASS_TOGGLE_SETTLE_VELOCITY_PX_PER_SEC
    }
    val speed = if (velocityPxPerSec.isFinite()) velocityPxPerSec else 0f
    if (abs(speed) >= settle) return speed > 0f
    val travel = if (travelPx.isFinite()) travelPx else 0f
    // 行程为 0（异常尺寸）时位置没有任何信息量，只能维持关闭。
    if (travel <= 0f) return false
    val drag = if (dragPxFromOff.isFinite()) dragPxFromOff else 0f
    return drag / travel > 0.5f
}
