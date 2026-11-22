package com.otakup.niriko.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 间距栅格（Niriko 27 · S0 空壳，见 docs/design-language-plan-2026-11-niriko27.md §3.3）。
 *
 * 4pt 栅格，另留一档 2dp 给发丝级间隙：2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 56。
 * S0 只建令牌与纯函数，**没有调用点**；S2 分三批页面迁移（设置类 → 列表 → 详情 / 统计），
 * 每批截图对照，显得挤的页面单页回退。
 *
 * 存量里最常见的非栅格值是 10dp 与 6dp，迁移时要看上下文：紧凑的行内间距往 8 / 4，
 * 块与块之间往 12。[nearestOnGrid] 只是工具，平局时取更小的一档，不代替这个判断。
 */
object NirikoSpacing {
    /** 发丝级间隙。 */
    val Xxs: Dp = 2.dp

    /** 紧贴：图标与文字、徽标内距。 */
    val Xs: Dp = 4.dp

    /** 紧凑：行内元素之间。 */
    val S: Dp = 8.dp

    /** 舒适：块与块之间。 */
    val M: Dp = 12.dp

    /** 页面级水平内距（列表）。 */
    val L: Dp = 16.dp

    /** 区块级水平内距。 */
    val Xl: Dp = 20.dp

    /** 区块之间。 */
    val Xxl: Dp = 24.dp

    /** 分组之间。 */
    val X3: Dp = 32.dp

    /** 大留白。 */
    val X4: Dp = 40.dp

    /** 顶栏 / 触控行的整档高度。 */
    val X5: Dp = 56.dp

    /** 栅格上的全部取值（dp，升序）；0 不算栅格档，它表示「没有间距」。 */
    val gridDp: List<Int> = listOf(2, 4, 8, 12, 16, 20, 24, 32, 40, 56)

    /** [valueDp] 是否落在栅格上（0 视为合法：没有间距）。 */
    fun isOnGrid(valueDp: Float): Boolean = valueDp == 0f || gridDp.any { it.toFloat() == valueDp }

    /** 离 [valueDp] 最近的栅格档；平局取更小的一档（偏紧凑），负值按 0 处理为最小档。 */
    fun nearestOnGrid(valueDp: Float): Int = gridDp.minWithOrNull(compareBy<Int> { abs(it - valueDp) }.thenBy { it }) ?: gridDp.first()
}
