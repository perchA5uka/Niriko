package com.otakup.niriko.ui.icon

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 图标规范（Niriko 27 · S0 空壳，见 docs/design-language-plan-2026-11-niriko27.md §3.7）。
 *
 * 目标：线形图标统一四档尺寸 16 / 20 / 24 / 32；返回、关闭、更多等 12 个核心图标在 S3 自绘后
 * 也放进本对象，Filled 只用于「已选中 / 已收藏」状态。S0 只定尺寸档与取档函数，没有调用点。
 */
object NirikoIcons {
    /** 行内小图标（徽标、辅助文字旁）。 */
    val Small: Dp = 16.dp

    /** 控件内图标（胶囊、列表行）。 */
    val Medium: Dp = 20.dp

    /** 默认图标（顶栏、工具栏）。 */
    val Large: Dp = 24.dp

    /** 大图标（空态、引导）。 */
    val XLarge: Dp = 32.dp

    /** 四档尺寸（dp，升序）。 */
    val sizesDp: List<Int> = listOf(16, 20, 24, 32)

    /** 离 [requestedDp] 最近的一档；平局取更大的一档（图标宁大勿小，保证可辨认）。 */
    fun nearestSizeDp(requestedDp: Float): Int = sizesDp.minWithOrNull(compareBy<Int> { abs(it - requestedDp) }.thenByDescending { it }) ?: sizesDp.first()
}
