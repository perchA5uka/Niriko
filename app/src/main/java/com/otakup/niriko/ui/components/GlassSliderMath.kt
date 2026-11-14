package com.otakup.niriko.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * 液态玻璃滑块的取值数学（纯函数，不依赖 Compose，可在 JVM 单测中直接覆盖）。
 *
 * 语义（与从 GlassControls.kt 抽出前逐字一致，勿改）：
 * - [widthPx] <= 0（尚未测量 / 宽度为 0）⇒ 返回 [range] 起点；
 * - x 先按宽度归一化为 0..1（越界钳制），再线性映射到 [range]；
 * - [steps] <= 0 ⇒ 连续取值；否则按 `span / (steps + 1)` 对齐到离散档位
 *   （0..10 且 steps = 19 ⇒ 步长 0.5，共 21 档），最后钳回 [range]；
 * - [range] 允许反向（如 10f..0f）：此时 x = 0 对应 range.start、x = width 对应 range.endInclusive，
 *   吸附按 |span| 对称（修复前此处会抛 "Cannot coerce value to an empty range"，本批次改为钳到
 *   实际的 [min, max]；正向范围的取值逐点不变）。
 *
 * 滑块的手势（detectTapGestures + detectDragGestures）与 0.5 步进语义属于既有公开行为；
 * 本批次只替换轨道 / 圆钮 / 高光的**绘制方式**，数学与手势保持原样。
 */
internal fun offsetToValue(
    xPx: Float,
    widthPx: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    if (widthPx <= 0f) return range.start
    val fraction = (xPx / widthPx).coerceIn(0f, 1f)
    val span = range.endInclusive - range.start
    val raw = range.start + fraction * span
    if (steps <= 0) return raw
    val step = span / (steps + 1)
    val snapped = (raw / step).roundToInt() * step
    // coerceIn 需要 [min, max]；反向范围直接传 (start, endInclusive) 会在运行时抛异常。
    return snapped.coerceIn(minOf(range.start, range.endInclusive), maxOf(range.start, range.endInclusive))
}

/** 圆钮相对轨道的图形对比度下限（WCAG 非文本图形元素 3:1，不用正文的 4.5:1）。 */
internal const val GLASS_SLIDER_THUMB_MIN_CONTRAST = 3.0f

/**
 * 滑块圆钮的底色（P2-3，纯函数便于离线断言对比度）。
 *
 * 深色主题：圆钮用**纯白**而不是 surfaceContainerHighest —— 后者与轨道同源（轨道也是
 * surfaceContainerHighest α0.55），同源色只剩 0.5dp 描边能区分，深色玻璃上圆钮等于消失。
 * 浅色主题：沿用 surfaceContainerHighest（浅面上白圆钮会与玻璃高光糊在一起，原观感是对的）。
 *
 * 为什么返回不透明色而不是 α0.92：半透明会让「圆钮 ∘ 轨道」的实际对比度随轨道颜色漂移，
 * 而这里要在单测里用一个确切值断言 ≥ [GLASS_SLIDER_THUMB_MIN_CONTRAST]。
 */
internal fun glassSliderThumbColor(isDark: Boolean, lightThumb: Color): Color = if (isDark) Color.White else lightThumb
