package com.otakup.niriko.ui.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.model.search.TrendingMode
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.subject.SubjectResultCard
import com.otakup.niriko.util.toCardDisplayModel

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
) {
    if (state.error != null && query.isBlank()) {
        ErrorContent(message = state.error ?: "", onRetry = onRetry)
    } else if (state.trendingResults.isNotEmpty()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                    if (st.trendingMode == TrendingMode.ALL_TIME && st.hasMore && !st.isLoadingMore && remainingPx < thresholdPx) {
                        onLoadMore()
                    }
                }
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                items(state.trendingResults, key = { it.subjectId }) { subject ->
                    SubjectResultCard(
                        model = subject.toCardDisplayModel(),
                        isInCollection = subject.subjectId in state.collectedSubjectIds,
                        onClick = { onSubjectClick(subject.subjectId) },
                        sharedElementKey = "cover_${subject.subjectId}",
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        modifier = Modifier.animateItem(),
                    )
                }
                item {
                    if (state.trendingMode == TrendingMode.ALL_TIME) {
                        when {
                            state.isLoadingMore -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                            !state.hasMore -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                Text("— 已经到底了 —", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Text("输入关键词搜索作品", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}