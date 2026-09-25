@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)

package com.otakup.niriko.ui.subject

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.model.search.TrendingMode
import com.otakup.niriko.navigation.LocalSearchGestureLock
import com.otakup.niriko.ui.common.SearchMode
import com.otakup.niriko.ui.common.SearchPhase
import com.otakup.niriko.ui.common.SearchToolbar
import com.otakup.niriko.ui.common.SearchViewModel
import com.otakup.niriko.ui.search.SearchResultsPane
import com.otakup.niriko.ui.search.TrendingSection
import com.otakup.niriko.viewmodel.SubjectSearchViewModel
import kotlinx.coroutines.launch

/**
 * 作品搜索屏幕 —— 薄壳。
 *
 * 职责：
 * - 持有前端状态机 [SearchViewModel]（阶段/镜像，纯 UI 层）
 * - 将 SubjectSearchViewModel 数据镜像同步进 SearchViewModel（单向数据流）
 * - 顶栏 [SearchToolbar]（趋势标签 + 一体玻璃表面）
 * - 内容区按 [SearchPhase] 切换：COLLAPSED/MENU → 趋势区；SEARCH → 搜索结果
 * - Pager 手势锁转发（onGestureLockChange → LocalSearchGestureLock）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SubjectSearchScreen(
    viewModel: SubjectSearchViewModel,
    onSubjectClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    onSetTrendingMode: (TrendingMode) -> Unit = viewModel::setTrendingMode,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    // P0-3：query 独立流——击键只重组搜索框，不触发大 uiState 全量重组
    val query by viewModel.query.collectAsState()
    // 当前趋势视图的上次成功刷新时间（用于「上次更新 X 分钟前」与点按强制刷新）
    val trendingLastUpdatedAt by viewModel.trendingLastUpdatedAt.collectAsState()
    val latestState = rememberUpdatedState(state)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 前端状态机（纯 UI 层，不触碰数据/业务）
    // rememberSaveable + Saver：navigate 覆盖 MainPager 后 pop 返回时恢复搜索态
    // （否则 phase 重建为 COLLAPSED → 回到发现页首页而非搜索结果页）
    val searchVm = rememberSaveable(saver = SearchViewModel.Saver) { SearchViewModel() }

    // 系统返回：IMMERSIVE 搜索态先收起回趋势区（清焦收键盘），菜单态收起菜单，
    // COLLAPSED 不拦截（默认路由返回）——避免"搜索结果页手势返回直接退出应用"
    BackHandler(enabled = searchVm.phase != SearchPhase.COLLAPSED) {
        when (searchVm.phase) {
            SearchPhase.IMMERSIVE_SEARCH -> {
                focusManager.clearFocus()
                keyboardController?.hide()
                searchVm.collapse()
                // 清空已输入内容，避免下次展开残留上次搜索
                viewModel.clearResults()
            }
            SearchPhase.MENU_EXPANDED, SearchPhase.DRAGGING -> searchVm.setMenuExpanded(false)
            else -> Unit
        }
    }

    // 搜索结果列表滚动状态：上划离开顶部 → 折叠类型行；下拉回顶部 → 拉长
    val resultListState = rememberLazyListState()
    LaunchedEffect(resultListState) {
        snapshotFlow { resultListState.firstVisibleItemIndex to resultListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val atTop = index == 0 && offset == 0
                searchVm.markFilterRowExpanded(atTop)
            }
    }

    // ===== 镜像同步（SubjectSearchViewModel → SearchViewModel，单向数据流） =====
    LaunchedEffect(state.isPersonSearch) { searchVm.syncMode(state.isPersonSearch) }
    LaunchedEffect(state.selectedType) { searchVm.syncContentType(state.selectedType) }
    LaunchedEffect(query) { searchVm.syncQuery(query) }

    // Pager 手势锁：搜索交互按下/拖拽期间锁 Pager
    val gestureLock = LocalSearchGestureLock.current

    // 内容区顶部避让（浮动搜索卡片高度 + 8dp 间距，防遮挡；编辑态菜单强制展开也算展开）
    val contentTopInset: Dp = when {
        searchVm.phase == SearchPhase.COLLAPSED -> 0.dp
        searchVm.phase == SearchPhase.IMMERSIVE_SEARCH &&
            (searchVm.editing || searchVm.filterRowExpanded) -> 208.dp
        searchVm.phase == SearchPhase.IMMERSIVE_SEARCH -> 76.dp // 折叠：68 菜单 + 8
        else -> 208.dp // MENU_EXPANDED / DRAGGING
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ===== 顶栏：趋势标签 + 搜索轨道容器 =====
        SearchToolbar(
            viewModel = searchVm,
            trendingMode = state.trendingMode,
            onSetTrendingMode = onSetTrendingMode,
            onModeSelected = { mode ->
                focusManager.clearFocus()
                viewModel.setPersonSearch(mode == SearchMode.CHARACTERS)
            },
            onTypeSelected = { type ->
                focusManager.clearFocus()
                viewModel.setType(type.bangumiType)
            },
            onQueryChanged = { q ->
                searchVm.syncQuery(q)
                viewModel.onQueryChanged(q)
            },
            onSearchSubmit = { viewModel.onSearchSubmit(searchVm.query) },
            onGestureLockChange = { locked -> gestureLock.value = locked },
            // 第 5 轮 D28：卡片 / 宫格切换
            layout = state.discoveryLayout,
            onToggleLayout = viewModel::toggleDiscoveryLayout,
            // zIndex：展开的搜索卡片（200dp）溢出工具栏 Box，需绘制在内容区之上并优先命中
            // （用户确认 innerPadding 已生效 → 恢复贴任务栏底部布局，不再额外让位状态栏）
            modifier = Modifier.padding(top = 8.dp).zIndex(10f),
        )

        // ===== 内容区：按阶段切换 =====
        when (searchVm.phase) {
            SearchPhase.COLLAPSED,
            SearchPhase.MENU_EXPANDED,
            SearchPhase.DRAGGING,
            -> {
                // 宫格版首页（第 5 轮 D28）：点入口 → 切到对应 mode 并回到卡片视图看全屏列表。
                // 这正是 Bangumi-master 的结构：宫格是首页，条目是独立页面。
                if (state.discoveryLayout == com.otakup.niriko.data.model.search.DiscoveryLayout.GRID &&
                    query.isBlank()
                ) {
                    com.otakup.niriko.ui.search.DiscoverGridPane(
                        state = state,
                        onPickMode = { mode ->
                            onSetTrendingMode(mode)
                            viewModel.setDiscoveryLayout(
                                com.otakup.niriko.data.model.search.DiscoveryLayout.CARD,
                            )
                        },
                        onSubjectClick = onSubjectClick,
                        modifier = Modifier.fillMaxSize().padding(top = contentTopInset),
                    )
                } else {
                // 趋势区全屏（下拉刷新 + 滚动位置恢复 + 触底分页）；
                // 菜单展开（MENU/DRAGGING）时顶部避让，趋势作品卡片不被浮动菜单遮挡
                Column(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                    // 历史排名：BrowseFilter 的 9 维度筛选器（第 6 轮 §6.3，「改动即查询」）。
                    // 关键词搜索的筛选面板（SearchFilterPanel）仍归搜索态使用，两者状态源不同。
                    if (query.isBlank() && state.trendingMode == TrendingMode.ALL_TIME) {
                        BrowseFilterPanel(
                            filter = state.browseFilter,
                            resultCount = state.trendingResults.size,
                            poolCount = state.browsePoolSize,
                            isExpanded = state.isFilterPanelExpanded,
                            nowYear = java.time.Year.now().value,
                            onToggleExpand = viewModel::toggleFilterPanel,
                            onArea = viewModel::setBrowseArea,
                            onVersion = viewModel::setBrowseVersion,
                            onYear = viewModel::setBrowseYear,
                            onQuarter = viewModel::setBrowseQuarter,
                            onStatus = viewModel::setBrowseStatus,
                            onToggleTag = viewModel::toggleBrowseTag,
                            onStudio = viewModel::setBrowseStudio,
                            onSort = viewModel::setBrowseSort,
                            onHideCollected = viewModel::setBrowseHideCollected,
                            onRatingRange = viewModel::setBrowseRatingRange,
                            onRatingCountRange = viewModel::setBrowseRatingCountRange,
                            onRankRange = viewModel::setBrowseRankRange,
                            onNsfw = viewModel::setBrowseNsfw,
                            onClear = viewModel::clearBrowseFilter,
                        )
                    }
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = state.isLoadingTrending,
                        onRefresh = {
                            if (query.isBlank()) {
                                scope.launch { viewModel.refreshTrending() }
                            } else if (state.isPersonSearch) {
                                viewModel.setPersonSearch(true)
                            } else {
                                viewModel.onQueryChanged(query)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        TrendingSection(
                            state = state,
                            query = query,
                            onLoadMore = viewModel::loadNextTrendingPage,
                            onSubjectClick = onSubjectClick,
                            onRetry = {
                                if (query.isBlank()) {
                                    scope.launch { viewModel.refreshTrending() }
                                } else {
                                    viewModel.onQueryChanged(query)
                                }
                            },
                            onScrollPosChange = { index, offset ->
                                val st = latestState.value
                                if (st.trendingResults.isNotEmpty()) {
                                    viewModel.saveTrendingScrollPos(st.selectedType, index, offset)
                                }
                            },
                            initialScrollPos = viewModel.getTrendingScrollPos(state.selectedType),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            lastUpdatedAt = trendingLastUpdatedAt,
                            onForceRefresh = { viewModel.refreshTrending() },
                            // 当季热门空态时的出口：切到历史排名（列表本体的尾部出口已按第 6 轮返工移除）
                            onOpenAllTime = { onSetTrendingMode(TrendingMode.ALL_TIME) },
                        )
                    }
                }
                }
            }
            SearchPhase.IMMERSIVE_SEARCH -> {
                // 搜索态：历史 / 建议 / 结果
                // 点击空白处取消输入框聚焦并收起键盘（不拦截列表滚动与子项点击）
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                ) {
                    SearchResultsPane(
                        state = state,
                        query = query,
                        listState = resultListState,
                        onHistoryClick = { keyword ->
                            focusManager.clearFocus()
                            viewModel.onHistoryClick(keyword)
                        },
                        onDeleteHistoryItem = viewModel::deleteHistoryItem,
                        onClearSearchHistory = viewModel::clearSearchHistory,
                        onSubjectClick = onSubjectClick,
                        onPersonClick = onPersonClick,
                        onRetry = { viewModel.onQueryChanged(query) },
                        onQueryChange = { q ->
                            searchVm.syncQuery(q)
                            viewModel.onQueryChanged(q)
                        },
                        onClearSuggestions = viewModel::clearSuggestions,
                        filterDimensions = viewModel.getFilterDimensions(),
                        onSetSortMode = viewModel::setSortMode,
                        onSetFilter = { dimKey, option -> viewModel.setFilter(dimKey, option) },
                        onSetNsfw = viewModel::setNsfw,
                        onToggleFilterPanel = viewModel::toggleFilterPanel,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        // 顶部避让浮动搜索卡片（历史/建议/筛选/结果不遮挡）
                        topInset = contentTopInset,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
