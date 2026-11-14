package com.otakup.niriko.ui.components

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

// 「动画尺寸」的布局期读取（B-11 / 02 §A.9 帧阶段纪律）。
//
// 为什么需要这组修饰符：requiredWidth(state.value) 里的 state.value 在**组合期**就被求值——
// 弹簧每推进一帧都要重组读取它的整个 Composable，搜索卡展开时等于每帧把整张卡片
// （含 4 行子内容）重组一遍。改成布局期读取后，同一帧只有「测量 + 布局」，重组不再发生
// （几何与逐像素结果不变，只是读的位置从组合期挪到布局期）。
//
// 为什么不用 animateContentSize：它只在**测量之后**对尺寸做二次动画，起点来自当前测量结果，
// 与本工程「尺寸由弹簧值直接驱动」的既有行为不等价（展开首帧会多一次尺寸回跳）。
// 这里保持 requiredWidth/requiredHeight 的语义（忽略父约束、强制固定尺寸），只换读取时机。

/**
 * requiredWidth + requiredHeight 的布局期读取版本。
 *
 * @param width 尺寸动画的 [State]；**不要**在组合期读它的 value，否则又退回每帧重组
 */
fun Modifier.animatedRequiredSize(width: State<Dp>, height: State<Dp>): Modifier = layout { measurable, _ ->
    val w = width.value.roundToPx()
    val h = height.value.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(0, 0) }
}

/** 正方形（宽高同一个动画值）的布局期读取版本，等价于 requiredSize(state.value)。 */
fun Modifier.animatedRequiredSize(size: State<Dp>): Modifier = animatedRequiredSize(width = size, height = size)
