package com.otakup.niriko.ui.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.model.search.TrendingMode
import com.otakup.niriko.navigation.TabReselectSignal
import com.otakup.niriko.navigation.TopLevelDestination
import com.otakup.niriko.navigation.applyTabReselectTo
import com.otakup.niriko.ui.adaptive.NirikoGridColumns
import com.otakup.niriko.ui.adaptive.currentNirikoWindowLayout
import com.otakup.niriko.ui.animation.RevealOnScroll
import com.otakup.niriko.ui.common.reportBottomBarScroll
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.library.PosterGridSkeletonCard

/**
 * 发现页 · 海报视图（第 3 轮参考计划 §3.4，P0）。
 *
 * 形态参考 open-ani/animeko（AGPL-3.0，**只读设计、不抄代码**）的探索页海报网格：
 * 封面优先的无容器网格 + 图下最多两行标题 + 自适应列宽 + 每格骨架占位。
 *
 * 与 [TrendingSection]（卡片列表视图）共用同一份数据、同一份滚动位置、同一套触底分页语义；
 * 差别只在「怎么摆」：
 * - 列数：[NirikoGridColumns]（手机 3 / 平板 4 / 大屏 6），不抄 animeko 的 `GridCells.Adaptive`；
 * - 封面 2:3（与作品库海报网格同源），不用 animeko 的 9:16 —— 那会裁掉 Bangumi 封面的上下信息；
 * - 圆角 16dp / 间距 10dp / 页边距 12dp，与作品库网格一致。
 *
 * 载 / 错 / 空三态与卡片视图逐条对应（§3.10），文案取自同一分支，保证两种布局说法一致。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DiscoverPosterPane(
    state: SubjectSearchUiState,
    query: String,
    onLoadMore: () -> Unit = {},
    onSubjectClick: (Long) -> Unit,
    onRetry: () -> Unit = {},
    onScrollPosChange: (Int, Int) -> Unit = { _, _ -> },
    initialScrollPos: Pair<Int, Int> = 0 to 0,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    /** 当前趋势视图上次成功刷新时间（0 = 从未）。 */
    lastUpdatedAt: Long = 0L,
    /** 点「上次更新」即强制刷新（下拉手势之外的可发现入口）。 */
    onForceRefresh: (() -> Unit)? = null,
    /** 当季热门空态时的出口：切到历史排名。null = 不显示该入口。 */
    onOpenAllTime: (() -> Unit)? = null,
    /** F11：发现页重选信号与事件号（海报视图的滚动状态在本地，因此在这里消费）。 */
    tabReselectEventId: Int = 0,
    tabReselect: TabReselectSignal? = null,
    topInset: Dp = 0.dp,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val columns = NirikoGridColumns.fromLayout(currentNirikoWindowLayout())

    if (state.error != null && query.isBlank()) {
        ErrorContent(message = state.error, onRetry = onRetry)
    } else if (state.trendingResults.isNotEmpty()) {
        val gridState = rememberLazyGridState()
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            // 陈旧度 + 强制刷新入口（与卡片视图同一行，左右与网格首列对齐）

            // 恢复该类型上次的滚动位置：index 语义与 LazyListState 相同（像素 offset 允许 ≤1 行误差）
            // 仅历史排名（ALL_TIME）可翻页/切类型，需要恢复；SEASONAL 固定顶部。
            val latestInitialScrollPos = rememberUpdatedState(initialScrollPos)
            LaunchedEffect(state.trendingVersion) {
                val (idx, offset) = latestInitialScrollPos.value
                if (state.trendingMode == TrendingMode.ALL_TIME && state.trendingResults.isNotEmpty()) {
                    gridState.scrollToItem(idx.coerceIn(0, state.trendingResults.size), offset)
                }
            }
            // 滚动时上报当前位置（与卡片视图共用 trendingScrollIndex/Offset）
            LaunchedEffect(gridState) {
                snapshotFlow {
                    gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
                }.collect { (index, offset) ->
                    onScrollPosChange(index, offset)
                }
            }
            // F11：底栏重选发现页（海报视图）—— 不在顶部先回顶；已在顶部才刷新。key 是事件号。
            LaunchedEffect(tabReselectEventId) {
                if (tabReselectEventId <= 0) return@LaunchedEffect
                val signal = tabReselect ?: return@LaunchedEffect
                applyTabReselectTo(
                    signal = signal,
                    page = TopLevelDestination.Discover.ordinal,
                    supportsRefreshAtTop = true,
                    gridState = gridState,
                ) { onForceRefresh?.invoke() }
            }
            // 触底加载：与 TrendingSection 同一算法（剩余未组合项数 × 最后可见项高度 < 200dp）
            val latestState = rememberUpdatedState(state)
            val thresholdPx = with(LocalDensity.current) { 200.dp.toPx() }
            LaunchedEffect(gridState, thresholdPx) {
                snapshotFlow {
                    val info = gridState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    if (last == null || info.totalItemsCount == 0) {
                        Double.MAX_VALUE
                    } else {
                        val remainingItems = info.totalItemsCount - 1 - last.index
                        remainingItems.toDouble() * last.size.height
                    }
                }.collect { remainingPx ->
                    val st = latestState.value
                    if (st.hasMore && !st.isLoadingMore && remainingPx < thresholdPx) {
                        onLoadMore()
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxWidth().reportBottomBarScroll(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topInset + 4.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "feed_header", span = { GridItemSpan(maxLineSpan) }) {
                    Column { headerContent?.invoke(); TrendingFreshnessRow(lastUpdatedAt, onForceRefresh) }
                }
                itemsIndexed(state.trendingResults, key = { _, subject -> subject.subjectId }) { index, subject ->
                    // 入场：进入视口淡入上移，同屏按**列**错峰（3 列 → 3 档）
                    RevealOnScroll(staggerIndex = index % columns) {
                        DiscoverPosterCard(
                            subject = subject,
                            isCollected = subject.subjectId in state.collectedSubjectIds,
                            onClick = { onSubjectClick(subject.subjectId) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (state.isLoadingMore) {
                    // 加载更多：追加一整行骨架（几何与真卡一致，不跳版）
                    items(count = columns) { PosterGridSkeletonCard() }
                } else if (!state.hasMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "— 已经到底了 —",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    } else if (state.trendingMode == TrendingMode.STEAM && query.isBlank()) {
        // Steam 榜单空态：与卡片视图同文案、同重试入口
        ErrorContent(
            message = state.error ?: "这里展示 Steam 当前最活跃的游戏排行。网络不可用或暂无数据时为空；导入游戏库后也会显示你的 Steam 作品。",
            onRetry = onRetry,
            modifier = Modifier.padding(top = 72.dp),
        )
    } else if (state.isLoadingTrending) {
        // 首次加载中：整屏海报骨架（3 列 × 4 行 = 12 格，几何与真卡一致）
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topInset + 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(count = columns * 4) { PosterGridSkeletonCard() }
        }
    } else {
        // 空态：与卡片视图同文案（按模式），当季热门额外给历史排名出口
        TrendingEmptyState(
            message = when (state.trendingMode) {
                TrendingMode.SEASONAL ->
                    "本季暂无可展示的人气作品：候选要么还没开播，要么都不足 100 人评分。"
                TrendingMode.ALL_TIME ->
                    "榜单暂无数据：当前筛选条件下没有可展示的条目。"
                TrendingMode.STEAM ->
                    "Steam 排行暂无数据：导入游戏库或检查网络后再试。"
            },
            onRetry = onRetry,
            onOpenAllTime = if (state.trendingMode == TrendingMode.SEASONAL) onOpenAllTime else null,
        )
    }
}
