package com.otakup.niriko.ui.components

import kotlin.math.abs

// 双圆钮区间滑块的纯数学（[GlassRangeSlider] 用，JVM 单测覆盖，见同包 GlassRangeSliderMathTest）。
//
// 为什么单独抽：双圆钮滑块有两件事容易写错、且只能靠真机反复试——
// 1) 按下时该抓哪个圆钮；2) 两个圆钮相撞时是「互相推」还是「原地停」。
// 两条都是纯判定，抽出来才能穷举边界（重合、跨过中线、反向范围）。

/** 把值换算成 0..1 的轨道占比（宽度非法或跨度非法时回退 0）。 */
internal fun rangeSliderFraction(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    val span = range.endInclusive - range.start
    if (!span.isFinite() || span == 0f) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

/**
 * 按下位置抓哪个圆钮（true = 起始圆钮）。
 *
 * 判据是「离哪个圆钮更近」，不是「在区间内/外」：
 * - 两个圆钮重合（区间为 0 宽，筛选里的常见初值）时抓起点，避免一按就改上界；
 * - 正好在中点（距离相等）时也抓起点——手指微抖不会跳到另一头。
 */
internal fun rangeSliderGrabsStart(
    offsetX: Float,
    widthPx: Float,
    current: ClosedFloatingPointRange<Float>,
    range: ClosedFloatingPointRange<Float>,
): Boolean {
    if (widthPx <= 0f) return true
    val x = (offsetX / widthPx).coerceIn(0f, 1f)
    val startX = rangeSliderFraction(current.start, range)
    val endX = rangeSliderFraction(current.endInclusive, range)
    // 重合时两者距离相同 → 抓起点（只有离终点严格更近才抓终点）
    return abs(x - startX) <= abs(x - endX)
}

/**
 * 拖动一个圆钮后的新区间。
 *
 * 相撞语义：**原地停**，不推另一个圆钮。筛选面板的「评分区间」里，下界被拖到上界时若把上界
 * 一起推到顶，用户会以为自己改了上界（那一行的两个字都在变），实际只想把下界拉满；
 * 原地停让两个数字各自只响应自己那次拖动，行为可预期。
 *
 * @param draggingStart true = 正在拖起始圆钮
 * @param nextValue 已经过 [offsetToValue] 离散吸附的目标值
 */
internal fun rangeSliderDrag(
    current: ClosedFloatingPointRange<Float>,
    nextValue: Float,
    draggingStart: Boolean,
): ClosedFloatingPointRange<Float> {
    if (!nextValue.isFinite()) return current
    return if (draggingStart) {
        minOf(nextValue, current.endInclusive)..current.endInclusive
    } else {
        current.start..maxOf(nextValue, current.start)
    }
}
