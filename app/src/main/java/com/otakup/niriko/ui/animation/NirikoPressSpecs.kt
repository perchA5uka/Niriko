package com.otakup.niriko.ui.animation

import androidx.compose.animation.core.FiniteAnimationSpec

/**
 * 按压 / 触感 / 滚动边界这一族"手感常量"（v1.2.2 新增）。
 *
 * 为什么单独一个文件：这些值**不是弹簧档**。弹簧档只有 [NirikoMotionSpecs] 那六档，
 * 多一档就破坏了"逐字等于 M3 StandardMotionTokens"的性质；而「按下时整行底色盖多少 alpha」
 * 「thumb 放大几个百分点」是纯粹的皮肤参数，不应该混进去。
 *
 * 口径来源：HyperLight 设置页所用的 MiuiX 控件手感（近临界阻尼 ≈200ms、进快出慢、
 * 开关 thumb 按下时放大约 8–13%）。Niriko 的旧值是 spring(0.6, 400) ≈383ms / 过冲 9.5%，
 * 慢了 1.5–2.2 倍——这是「设置页点了没感觉」的主因之一。
 */
object NirikoPressSpecs {

    /** 设置行 / 列表行按下时整行覆盖的不透明度（替代 M3 矩形水波）。 */
    const val ROW_PRESS_ALPHA = 0.08f

    /**
     * 开关 thumb 按下时的放大倍数。
     *
     * 刻意用 1.08 而不是 MiuiX 的 1.127：本工程的 thumb 只有 20–22dp，
     * 轨道高 30dp，放大过头会顶出轨道边缘（露头）。1.08 × 22dp ≈ 23.8dp，刚好在轨道内。
     */
    const val THUMB_PRESS_SCALE = 1.08f

    /** 滑块 thumb 按下时的放大倍数（比开关略大：滑块没有轨道边界问题）。 */
    const val SLIDER_THUMB_PRESS_SCALE = 1.10f

    /** 滚动到边界触发触感的位移阈值（dp）：向下拉出 1dp 且仍在同一位置才算"到底"。 */
    const val SCROLL_EDGE_OFFSET_DP = 1.0f

    /** 滚动到边界触发触感的速度阈值（dp/s）：快速甩到顶/底时也算边界。 */
    const val SCROLL_EDGE_VELOCITY_DP = 25.0f

    /** 按压"进"：快（spatialDefault，≈193ms 稳定）。手指按下必须立刻有反应。 */
    fun <T> pressIn(): FiniteAnimationSpec<T> = NirikoMotionSpecs.spatialDefault()

    /** 按压"出"：慢一档（spatialSlow）。抬起后收得温柔，避免"啪"一下弹回。 */
    fun <T> pressOut(): FiniteAnimationSpec<T> = NirikoMotionSpecs.spatialSlow()
}
