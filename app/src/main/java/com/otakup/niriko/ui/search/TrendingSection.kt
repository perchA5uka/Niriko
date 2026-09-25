package com.otakup.niriko.ui.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.RevealOnScroll
import com.otakup.niriko.ui.common.reportBottomBarScroll
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.model.search.TrendingMode
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.subject.SubjectResultCard
import com.otakup.niriko.util.toCardDisplayModel

/**
 * 趋势区的「上次更新」一行 + 强制刷新入口。
 *
 * 下拉刷新是没有提示的隐藏手势；把陈旧度写出来，同时让这一行可点 = 一个可发现的强制刷新入口。
 */
@Composable
private fun TrendingFreshnessRow(
    lastUpdatedAt: Long,
    onForceRefresh: (() -> Unit)?,
) {
    if (lastUpdatedAt <= 0L && onForceRefresh == null) return
    // 相对时间随页面存活自己走字
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val text = if (lastUpdatedAt <= 0L) {
        "尚未刷新"
    } else {
        "上次更新 " + com.otakup.niriko.data.refresh.RefreshStatusLabels.formatAgo(lastUpdatedAt, nowMs)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onForceRefresh != null) {
                    Modifier.clickable(onClick = onForceRefresh)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onForceRefresh != null) {
            Text(
                text = " · 点此强制刷新",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}


/**
 * 趋势区（当季热门 / 历史排名）全屏列表。
 * 从旧 SubjectSearchScreen 迁移，专注滚动位置恢复与触底加载分页。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun TrendingSection(
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
) {
    // 第 6 轮 R6：删掉 !trendingMode.isTrend 早退。
    // 改造前 FIND / MONTHLY 是「功能入口」需要在这里提前返回；这两个模式已随模块删除，
    // 剩下的三个模式都是趋势列表，早退只会让空态分支再也走不到。
    if (state.error != null && query.isBlank()) {
        ErrorContent(message = state.error, onRetry = onRetry)
    } else if (state.trendingResults.isNotEmpty()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // 陈旧度 + 强制刷新入口：改造前用户无从判断屏幕上是新数据还是缓存
            TrendingFreshnessRow(lastUpdatedAt = lastUpdatedAt, onForceRefresh = onForceRefresh)
            // 第 6 轮返工（用户复核）：当季热门回到**纯卡片列表** ——
            // 头部计数行 / 排序 chips / 「第 N 话」副标题 / 尾部出口全部移除，
            // 数据仍是「类内人气序 + 质量门槛 + 多样性重排 → 前 30」。
            val listState = rememberLazyListState()
            // 恢复该类型上次的滚动位置（像素级 index+offset；trendingVersion 每次 +1 是可靠信号）
            // 仅历史排名（ALL_TIME）可翻页/切类型，需要恢复；SEASONAL 固定顶部。
            val latestInitialScrollPos = rememberUpdatedState(initialScrollPos)
            LaunchedEffect(state.trendingVersion) {
                val (idx, offset) = latestInitialScrollPos.value
                if (state.trendingMode == TrendingMode.ALL_TIME && state.trendingResults.isNotEmpty()) {
                    listState.scrollToItem(idx.coerceIn(0, state.trendingResults.lastIndex), offset)
                }
            }
            // 滚动时上报当前位置（index+offset 像素级，切类型时保存）
            LaunchedEffect(listState) {
                snapshotFlow {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }.collect { (index, offset) ->
                    onScrollPosChange(index, offset)
                }
            }
            // 滚动接近底部 → 触发加载下一页（像素距离触发 + 最新 state，单一 effect）
            val latestState = rememberUpdatedState(state)
            val thresholdPx = with(LocalDensity.current) { 200.dp.toPx() }
            LaunchedEffect(listState, thresholdPx) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()
                    if (last == null || info.totalItemsCount == 0) {
                        Double.MAX_VALUE
                    } else {
                        // 距底部像素 ≈ 剩余未组合项数 × 最后可见项高度
                        val remainingItems = info.totalItemsCount - 1 - last.index
                        remainingItems.toDouble() * last.size
                    }
                }.collect { remainingPx ->
                    val st = latestState.value
                    // 第 6 轮：hasMore 现在对「历史排名 · 全部」也成立（候选池还能扩大），
                    // 因此不再限定 ALL_TIME，只认 hasMore。
                    if (st.hasMore && !st.isLoadingMore && remainingPx < thresholdPx) {
                        onLoadMore()
                    }
                }
            }
            LazyColumn(state = listState, modifier = Modifier.reportBottomBarScroll(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                itemsIndexed(state.trendingResults, key = { _, subject -> subject.subjectId }) { index, subject ->
                    // 入场：进入视口淡入上移，同屏错峰 50ms（Apple：空间感知）
                    RevealOnScroll(staggerIndex = index % 5) {
                        SubjectResultCard(
                            model = subject.toCardDisplayModel(state.steamGames[subject.subjectId]),
                            isInCollection = subject.subjectId in state.collectedSubjectIds,
                            onClick = { onSubjectClick(subject.subjectId) },
                            sharedElementKey = "cover_" + subject.subjectId,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item {
                    when {
                        state.isLoadingMore -> Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        !state.hasMore -> Box(
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
        // Steam 榜单空态：区分「加载失败（可见错误 + 重试）」与「正常空态」。
        // 找不到顶端 state.error 区分（error 已被上面首个分支消费），这里直接给重试按钮，
        // 与 ViewModel 中「榜单拉取失败且无缓存 → error 置位」的逻辑互补：即使 error 为空
        // （比如本地有条目但榜单失败），重试也能重新触发 refreshTrending() 强制重拉榜单。
        ErrorContent(
            message = state.error ?: "这里展示 Steam 当前最活跃的游戏排行。网络不可用或暂无数据时为空；导入游戏库后也会显示你的 Steam 作品。",
            onRetry = onRetry,
            modifier = Modifier.padding(top = 72.dp),
        )
    } else if (state.isLoadingTrending) {
        // 首次加载中：转圈即可（改造前这里会显示「输入关键词搜索作品」这种错位文案）
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // 空态（第 6 轮 §4.2 R2）：**按模式给正确文案**。
        // 改造前所有模式共用「输入关键词搜索作品」—— 对榜单毫无意义，
        // 用户刷新后看到的就是「一片空白 + 一句驴唇不对马嘴的提示」。
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

/**
 * 趋势/榜单的**空态**（第 6 轮 §4.2 R2）。
 *
 * 「确实没有数据」与「网络失败」必须是两种界面：网络失败走 [ErrorContent]（带错误文案），
 * 这里只负责「请求成功但结果为空」的说明 + 重试入口（当季热门额外给历史排名的出口）。
 */
@Composable
private fun TrendingEmptyState(
    message: String,
    onRetry: () -> Unit,
    onOpenAllTime: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRetry) { Text("重试") }
        if (onOpenAllTime != null) {
            TextButton(onClick = onOpenAllTime) { Text("查看历史排名 ›") }
        }
    }
}