package com.otakup.niriko.ui.components

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
