package com.otakup.niriko.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * 抵消外层容器已经施加的顶部内边距：内容整体上移 [top]，并把测量高度放大同样的值，
 * 于是内容重新铺满整个窗口（含状态栏后的区域）。
 *
 * 为什么需要「反向抵消」：NavHost 的内容内边距由 MainActivity 统一施加，且必须
 * **按正在被组合的目的地**而不是 `currentRoute` 计算 —— 预测性返回预览父页时
 * `currentRoute` 仍是子页（详情页），若按路由区分，父页会被套上详情页的 `top = 0`
 * 而整体上移一个状态栏高度（作品库搜索框被顶出屏幕）。
 * 详情页反过来需要 `top = 0`（背景墙延伸到状态栏后，顶栏各自 `statusBarsPadding` 避让），
 * 因此由这条修饰符在详情页内抵消统一施加的那一份，其余路由逐字不变。
 *
 * 与 [com.otakup.niriko.ui.adaptive.verticalDockStartInset] 同款写法：
 * 用布局 lambda 在测量阶段完成，不引入额外的重组。
 */
internal fun Modifier.negateTopInset(top: Dp): Modifier = layout { measurable, constraints ->
    val inset = top.toPx().roundToInt()
    if (inset <= 0 || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    } else {
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minWidth,
                maxWidth = constraints.maxWidth,
                minHeight = constraints.minHeight,
                maxHeight = constraints.maxHeight + inset,
            ),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(0, -inset)
        }
    }
}
