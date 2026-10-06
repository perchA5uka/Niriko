package com.otakup.niriko.ui.adaptive

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * B2（5a 落点 → B2b 提升）：EXPANDED 宽屏左侧竖向玻璃 dock 展开时占用的起始内边距
 * （计划书 §三 宿主落点：约 96dp，与 ui/bottombar/LiquidVerticalDock.kt 的宽度对齐）。
 *
 * 原为 MainActivity 的私有常量。B2b 接管 5a 留下的缺口（详情页只抵消顶部 inset，
 * 96dp 的 dock 内边距对详情页不生效，dock 会与内容重叠），详情页也要吃同一份内边距，
 * 因此提升到 ui/adaptive/ 共享，而不是在详情页复制一份。
 */
internal const val VERTICAL_DOCK_INSET_DP = 96f

/**
 * dock 收起（bottomBarHideFraction = 1）时同步收回的宽度，与 NirikoNavSuite 的横向滑出位移一致。
 */
internal const val VERTICAL_DOCK_HIDE_INSET_DP = 24f

/**
 * 给内容加左侧起始内边距，并随竖向 dock 收起（[hideFraction]）同步收缩：
 * dock 滑出 24dp 时内容同步多出 24dp 可用宽度（计划书 §三 通用验收 2）。
 *
 * 用布局 lambda 延迟读取状态 —— 滚动收起时只触发重新测量，不触发重组。
 * 只有 EXPANDED 宽屏才会应用它；手机端仍是原来的 `padding(innerPadding)`。
 */
internal fun Modifier.verticalDockStartInset(hideFraction: () -> Float): Modifier =
    layout { measurable, constraints ->
        val inset = if (constraints.hasBoundedWidth) {
            val target = VERTICAL_DOCK_INSET_DP -
                VERTICAL_DOCK_HIDE_INSET_DP * hideFraction().coerceIn(0f, 1f)
            (target * density).roundToInt().coerceAtLeast(0)
        } else {
            0
        }
        val placeable = measurable.measure(
            Constraints(
                minWidth = (constraints.minWidth - inset).coerceAtLeast(0),
                maxWidth = if (constraints.hasBoundedWidth) {
                    (constraints.maxWidth - inset).coerceAtLeast(0)
                } else {
                    constraints.maxWidth
                },
                minHeight = constraints.minHeight,
                maxHeight = constraints.maxHeight,
            )
        )
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        layout(width, height) {
            placeable.placeRelative(inset, 0)
        }
    }
