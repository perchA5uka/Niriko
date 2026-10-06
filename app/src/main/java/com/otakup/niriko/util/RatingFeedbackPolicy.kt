package com.otakup.niriko.util

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 评分控件的触感与「满分确认」规则（F15 / F16，计划 §11.1）。
 *
 * 这两条反馈最容易做错的不是动画，而是**触发条件**：
 * - 触感要在「跨到新刻度」时响一次，**同一刻度不重复** —— 否则手指在整数刻度上抖动会连响；
 * - 满分闪光只能在**松手**且「这一拖把自己推到满分」时响一次 ——
 *   否则打开编辑面板（评分本来就是 10）就会自己闪一下。
 *
 * 因此判定全部抽成纯函数（不依赖 Compose），每一条边界都能在 JVM 单测里钉住；
 * 界面只负责把结果变成触感、缩放与颜色。
 */
object RatingFeedbackPolicy {

    /** 评分步进（与 GlassSlider 的 steps=19 ⇒ 0.5 一致）。 */
    const val STEP = 0.5f

    /** 评分上限。 */
    const val MAX_RATING = 10f

    /** 满分确认的视觉时长（约 0.6s，与「短促但不闪一下就没」的手感平衡）。 */
    const val CELEBRATE_DURATION_MS = 600

    /** 满分确认的数字放大倍数。 */
    const val CELEBRATE_SCALE = 1.18f

    /** 刻度的整数索引：value = 0.5 ⇒ 1、value = 10 ⇒ 20。 */
    fun tickIndex(value: Float, step: Float = STEP): Int =
        if (step <= 0f) 0 else (value / step).roundToInt()

    /**
     * 按步进对齐（并把结果夹在 0..10）——用于把任意浮点比较统一到刻度上。
     *
     * 非法步长直接返回 0：不写 `tickIndex * step`，因为 step 为负时会算出 `-0.0f`，
     * 而 `-0.0f != 0.0f`（按位比较）—— 这种值传出去会让调用方的相等判断莫名其妙地失败。
     */
    fun snapped(value: Float, step: Float = STEP): Float {
        if (step <= 0f) return 0f
        return (tickIndex(value, step) * step).coerceIn(0f, MAX_RATING)
    }

    /**
     * 从 [from] 到 [to] 是否**跨到了新的刻度**。
     *
     * 「同一刻度不重复」就落在这一条上：只要刻度索引没变（手指在同一格里来回），就返回 false。
     */
    fun crossesTick(from: Float, to: Float, step: Float = STEP): Boolean =
        tickIndex(from, step) != tickIndex(to, step)

    /** Already-quantized indices must never be interpreted as rating values again. */
    fun crossesTickIndex(fromIndex: Int, toIndex: Int): Boolean = fromIndex != toIndex

    /** 一次变化跨过了几格（快速拖动可能一次跨多格）。 */
    fun ticksCrossed(from: Float, to: Float, step: Float = STEP): Int =
        abs(tickIndex(to, step) - tickIndex(from, step))

    /**
     * 是否触发「满分确认」。
     *
     * 两个条件同时成立：松手时的值在满分刻度上，**且这一拖开始时不在满分**。
     * 后者是关键：打开编辑面板时评分本来就可能是 10，那不是用户「刚做到」的事。
     */
    fun shouldCelebratePerfect(
        valueAtRelease: Float,
        valueAtDragStart: Float,
        step: Float = STEP,
    ): Boolean = tickIndex(valueAtRelease, step) == tickIndex(MAX_RATING, step) &&
        tickIndex(valueAtDragStart, step) < tickIndex(MAX_RATING, step)

    /**
     * 一次拖动里应该发几声触感：跨了 n 格也只发 **1** 声。
     *
     * 快速甩动滑块会一次跨好几格，按格数连响会变成一串噪音；
     * 而「跟手时不响、跨格才响」正是 iOS 评分控件的手感。
     */
    fun hapticCountFor(from: Float, to: Float, step: Float = STEP): Int =
        if (crossesTick(from, to, step)) 1 else 0

    /** 是否应该暂停动效（减少动态效果）：暂停时仍保留静态反馈（颜色变化），但不缩放。 */
    fun shouldAnimateScale(reduceMotion: Boolean): Boolean = !reduceMotion
}
