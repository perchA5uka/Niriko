package com.otakup.niriko.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.otakup.niriko.util.beginScrollToTop

/**
 * 底栏「选中项再次点按」的处置（计划 §8.3 · 清单 F11）。
 *
 * 语义（只有这一条规则，故意保持极简）：
 * - 目标页不是当前页 → [TabReselectAction.NONE]：交给既有的 Pager 切页路径，本规则不介入；
 * - 目标页就是当前页，且内容**不在顶部** → [TabReselectAction.SCROLL_TO_TOP]：先回到顶部；
 * - 目标页就是当前页，且内容**已经在顶部** → [TabReselectAction.REFRESH_AT_TOP]：这才真刷新。
 *
 * 「已经在顶部」必须由**真正持有滚动状态的那一层**判定（它才知道 firstVisibleItemIndex /
 * offset），因此这里只提供判决函数，不缓存任何状态 —— 纯函数（[decideTabReselect] 与各
 * decision 系列）可在 JVM 单测里穷举，**不触碰 Compose 运行时**。
 *
 * 只有 [TabReselectAction.REFRESH_AT_TOP] 的页（目前是发现页）会在顶部真正发起刷新；
 * 其他页在顶部重选是**无操作**（[TabReselectAction.NONE]），避免把「用户回到顶部」误当成
 * 「再发一轮请求」。
 */
enum class TabReselectAction {
    /** 不消费这次重选（非当前页 / 内容规模不足以滚动 / 该页没有刷新语义）。 */
    NONE,

    /** 内容不在顶部：滚回顶部（重选的第一层语义）。 */
    SCROLL_TO_TOP,

    /** 内容已在顶部且该页支持刷新：发起一次刷新（single-flight 由数据层保证）。 */
    REFRESH_AT_TOP,
}

/**
 * 一次「底栏选中项再次点按」事件。
 *
 * [id] 单调递增 —— 消费层用 `LaunchedEffect(id)` 只在**真的又点了一次**时触发，
 * 不会因为重组或状态变化被重复消费（事件式语义，不是电平式）。
 *
 * @param page 被重选的顶层页序号（= [TopLevelDestination.ordinal]）
 * @param action 该页声明自己想要的处置：回顶，或「已在顶部才刷新」
 */
data class TabReselectSignal(
    val id: Int,
    val page: Int,
    val action: TabReselectAction,
) {
    companion object {
        /** 初始/空信号：id = 0 表示从未发生过重选。 */
        val None = TabReselectSignal(id = 0, page = -1, action = TabReselectAction.NONE)
    }
}

/**
 * 顶层页声明自己想要的处置。
 *
 * 只有发现页在**已在顶部**时真的发请求（它的内容是远端榜单，重选是一个可发现的刷新入口 ——
 * 与 §8.3 保留的下拉刷新同义）；作品库/统计只回顶，已在顶部时静默不做事。
 * 设置页不是滚动内容页，重选不消费。
 *
 * 切页（点别的 tab）根本不进这条路径，因此这里的「刷新」不会与 Pager 动画打架。
 */
fun tabReselectActionFor(page: Int): TabReselectAction = when (page) {
    TopLevelDestination.Discover.ordinal -> TabReselectAction.REFRESH_AT_TOP
    TopLevelDestination.Library.ordinal,
    TopLevelDestination.Stats.ordinal,
    -> TabReselectAction.SCROLL_TO_TOP
    else -> TabReselectAction.NONE
}

/**
 * 一次重选在某个具体滚动容器里的处置判决。
 *
 * @param isCurrentPage 被点按的 tab 是否就是这一页（非当前页的事件不是给它的）
 * @param supportsRefreshAtTop 该页是否支持「已在顶部 → 刷新」
 * @param atTop 该滚动容器是否已在顶部
 * @param contentScrollable 该容器是否真的可滚动（不足一屏时回顶是空操作）
 */
fun decideTabReselect(
    isCurrentPage: Boolean,
    supportsRefreshAtTop: Boolean,
    atTop: Boolean,
    contentScrollable: Boolean,
): TabReselectAction {
    // 切页由 Pager 负责，内容层一律不消费
    if (!isCurrentPage) return TabReselectAction.NONE
    // 不可滚动：没有「回顶」这回事；只有在顶部且支持刷新的页才有事可做
    if (!contentScrollable) {
        return if (supportsRefreshAtTop && atTop) {
            TabReselectAction.REFRESH_AT_TOP
        } else {
            TabReselectAction.NONE
        }
    }
    if (!atTop) return TabReselectAction.SCROLL_TO_TOP
    return if (supportsRefreshAtTop) TabReselectAction.REFRESH_AT_TOP else TabReselectAction.NONE
}

/**
 * 便捷重载：只看 [TabReselectSignal] 与该容器状态。
 * [signal] 为 null（从未重选 / 不是给这一页的）时返回 [TabReselectAction.NONE]。
 */
fun decideTabReselect(
    signal: TabReselectSignal?,
    page: Int,
    supportsRefreshAtTop: Boolean,
    atTop: Boolean,
    contentScrollable: Boolean,
): TabReselectAction = decideTabReselect(
    isCurrentPage = signal != null && signal.id > 0 && signal.page == page,
    supportsRefreshAtTop = supportsRefreshAtTop,
    atTop = atTop,
    contentScrollable = contentScrollable,
)

/** [LazyListState] 版判决。 */
fun decideTabReselect(
    signal: TabReselectSignal?,
    page: Int,
    supportsRefreshAtTop: Boolean,
    listState: LazyListState,
): TabReselectAction {
    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    val scrollable = listState.layoutInfo.totalItemsCount > 0
    return decideTabReselect(
        signal = signal,
        page = page,
        supportsRefreshAtTop = supportsRefreshAtTop,
        atTop = atTop,
        contentScrollable = scrollable,
    )
}

/** [LazyGridState] 版判决（发现页海报视图用）。 */
fun decideTabReselect(
    signal: TabReselectSignal?,
    page: Int,
    supportsRefreshAtTop: Boolean,
    gridState: LazyGridState,
): TabReselectAction {
    val atTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
    val scrollable = gridState.layoutInfo.totalItemsCount > 0
    return decideTabReselect(
        signal = signal,
        page = page,
        supportsRefreshAtTop = supportsRefreshAtTop,
        atTop = atTop,
        contentScrollable = scrollable,
    )
}

/** 判决是否要求这个容器去发一次刷新（发现页在顶部重选时用）。 */
fun TabReselectAction.shouldRefreshAtTop(): Boolean = this == TabReselectAction.REFRESH_AT_TOP

/** 判决是否要求这个容器先滚回顶部。 */
fun TabReselectAction.shouldScrollToTop(): Boolean = this == TabReselectAction.SCROLL_TO_TOP

/** 名称直读版：判决是否有事要做（等价于 != NONE）。 */
fun TabReselectAction.isConsumed(): Boolean = this != TabReselectAction.NONE

// ==================== 各页共用的消费入口（避免四处复制同一段样板） ====================

/**
 * 列表容器消费一次重选：[supportsRefreshAtTop] 的页在顶部发 [onRefreshAtTop]，其余情况回顶。
 *
 * 事件式消费：只认 [TabReselectSignal.id]，同一信号不会重复触发；容器在顶部且该页不支持刷新时
 * 完全不动（[TabReselectAction.NONE]）。
 */
suspend fun applyTabReselectTo(
    signal: TabReselectSignal,
    page: Int,
    supportsRefreshAtTop: Boolean,
    listState: LazyListState,
    onRefreshAtTop: () -> Unit,
) {
    when (decideTabReselect(signal, page, supportsRefreshAtTop, listState)) {
        TabReselectAction.SCROLL_TO_TOP -> listState.beginScrollToTop()
        TabReselectAction.REFRESH_AT_TOP -> onRefreshAtTop()
        TabReselectAction.NONE -> Unit
    }
}

/** [LazyGridState] 版的 [applyTabReselectTo]。 */
suspend fun applyTabReselectTo(
    signal: TabReselectSignal,
    page: Int,
    supportsRefreshAtTop: Boolean,
    gridState: LazyGridState,
    onRefreshAtTop: () -> Unit,
) {
    when (decideTabReselect(signal, page, supportsRefreshAtTop, gridState)) {
        TabReselectAction.SCROLL_TO_TOP -> gridState.beginScrollToTop()
        TabReselectAction.REFRESH_AT_TOP -> onRefreshAtTop()
        TabReselectAction.NONE -> Unit
    }
}
