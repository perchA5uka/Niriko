package com.otakup.niriko.util

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

/**
 * 各顶层页共用的「回到顶部」实现（F11 底栏重选的第一层语义）。
 *
 * 为什么不直接 `animateScrollToItem(0)`：
 * - `animateScrollToItem` 把目标项直接摆到视口顶部再补动画，跳回顶部要经过的长距离会被压缩成
 *   一次布局，观感是一帧闪回；这里用 `animateScrollBy` 沿真实距离做带弹簧的滚动，
 *   与用户自己滑回去同一手感。
 * - 距离超过一屏时用 spring 一路滚到底耗时过长，因此只在**一屏以内**用弹簧，
 *   更远就直接落位（`scrollToItem(0)`）—— 与各系统 App 的常见取舍一致。
 *
 * 已经是顶部时**无条件空操作**：调用方不必自己先判一次（也避免两处判据漂移）。
 * 动画期间用户按下会取消协程 → 这里吞掉取消并**不强行回顶**，不会出现
 * 「用户往下滑、页面自己往上跑」。
 */
suspend fun LazyListState.beginScrollToTop() {
    if (isAtTop) return
    val distance = scrollDistanceToTop()
    if (distance != null && abs(distance) <= ONE_SCREEN_APPROX_PX) {
        try {
            animateScrollBy(-distance, SPRING)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        if (isAtTop) return
    }
    scrollToItem(0)
}

/**
 * [LazyGridState] 版的 [beginScrollToTop]（发现页海报视图用）。
 *
 * 与列表版同一取舍：近处用滚轮动画，远处直接落位。网格的 `firstVisibleItemIndex` 是**格**而非
 * **行**，把格换算成行需要一个稳定的列数 —— 布局信息里没有直接给出，所以这里只在**近处**
 * （第一行内）直接用 `animateScrollBy`；再远一点就落位，避免用错误的行高算出错误距离。
 */
suspend fun LazyGridState.beginScrollToTop() {
    if (isAtTop) return
    // 只有「还停在第 0 行以内」时距离才等于纵向偏移；跨行时列数拿不到，直接落位。
    val first = layoutInfo.visibleItemsInfo.firstOrNull()
    val distance = if (first != null && firstVisibleItemIndex * 2 <= layoutInfo.visibleItemsInfo.size) {
        firstVisibleItemScrollOffset.toFloat()
    } else {
        null
    }
    if (distance != null) {
        try {
            animateScrollBy(-distance, SPRING)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        if (isAtTop) return
    }
    scrollToItem(0)
}

/** 是否停在最顶部（首项 + 零偏移）。 */
val LazyListState.isAtTop: Boolean
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

/** 是否停在最顶部（首项 + 零偏移）。 */
val LazyGridState.isAtTop: Boolean
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

/**
 * 「要滚多少像素才回到顶部」：按首项实测高度推前面所有项，再加首项已滚过的偏移。
 *
 * 用实测高度而不是 dp 常量：同一 App 内卡片列表与海报网格的行高差好几倍，
 * 常量会算出错误距离。首项高度还没测出来时返回 null，调用方退化为直接落位。
 */
fun LazyListState.scrollDistanceToTop(): Float? {
    val index = firstVisibleItemIndex
    if (index == 0) return firstVisibleItemScrollOffset.toFloat()
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return null
    if (first.size <= 0) return null
    return index.toFloat() * first.size + firstVisibleItemScrollOffset
}

/** 一屏的近似像素：决定「弹簧滚」还是「直接落位」（1080p 手机约 2200px）。 */
private const val ONE_SCREEN_APPROX_PX = 2400f

private val SPRING = spring<Float>(stiffness = 380f, dampingRatio = 0.9f)
