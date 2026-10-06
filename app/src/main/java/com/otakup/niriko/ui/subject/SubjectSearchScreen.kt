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
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.otakup.niriko.data.model.search.DiscoveryLayout
import com.otakup.niriko.data.model.search.SubjectSearchUiState
import com.otakup.niriko.data.model.search.TrendingMode
import com.otakup.niriko.navigation.LocalSearchGestureLock
import com.otakup.niriko.navigation.TabReselectSignal
import com.otakup.niriko.ui.components.topContentAlphaMask
import com.otakup.niriko.util.TopFadePolicy
import com.otakup.niriko.ui.common.SearchMode
import com.otakup.niriko.ui.common.SearchPhase
import com.otakup.niriko.ui.common.SearchToolbar
import com.otakup.niriko.ui.common.SearchViewModel
import com.otakup.niriko.ui.search.DiscoverGridPane
import com.otakup.niriko.ui.search.DiscoverPosterPane
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
    /**
     * F11：底栏重选发现页事件。转交给当前生效的那个滚动容器（滚动状态在它自己手里）。
     *
     * @param tabReselectEventId 单调递增的事件号（消费层只认它，重组不会重复触发）
     * @param tabReselect 最近一次重选的页号与处置
     */
    tabReselectEventId: Int = 0,
    tabReselect: TabReselectSignal = TabReselectSignal.None,
    windowTopInset: Dp = 0.dp,
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

    // F11：重选事件直接以「值 + 单调事件号」进入内容层 —— 不做一次性消费，
    // 因为同一事件必须能被**多个**候选容器（切换卡片/宫格/海报布局时旧容器还在组合中）
    // 以幂等方式各读一次，而不是被先到者吃掉。
    val reselectSignal = tabReselect.takeIf { tabReselectEventId > 0 }

    // 前端状态机（纯 UI 层，不触碰数据/业务）
    // rememberSaveable + Saver：navigate 覆盖 MainPager 后 pop 返回时恢复搜索态
    // （否则 phase 重建为 COLLAPSED → 回到发现页首页而非搜索结果页）
    val searchVm = rememberSaveable(saver = SearchViewModel.Saver) { SearchViewModel() }

    // 系统返回：IMMERSIVE 搜索态先收起回趋势区（清焦收键盘），菜单态收起菜单，
    // COLLAPSED 不拦截（默认路由返回）——避免"搜索结果页手势返回直接退出应用"
    val backPreview = remember { com.otakup.niriko.ui.animation.BackPreviewState() }
    val reduceMotion = com.otakup.niriko.ui.animation.LocalReduceMotion.current
    com.otakup.niriko.ui.animation.PredictiveDismissHandler(
        enabled = searchVm.phase != SearchPhase.COLLAPSED,
        preview = backPreview,
    ) {
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

    // Only the collapsed toolbar reserves initial space; every expanded surface is an overlay.
    val contentTopInset: Dp = windowTopInset + TopFadePolicy.searchContentPaddingDp(56f).dp

    // F13：顶部**连续**渐隐的输入。复用发现页已有的滚动上报（index+offset），不再新增一套回调；
    // 首项索引 > 0 时按「已滚很远」处理（TopFadePolicy.scrollOffsetFor），避免 offset 归零导致渐隐忽明忽暗。
    var topScrollOffsetPx by remember(state.discoveryLayout, state.trendingMode, state.selectedType) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val fadeDistancePx = with(LocalDensity.current) { TopFadePolicy.FADE_DISTANCE_DP.dp.toPx() }
    val topFadeAlpha = TopFadePolicy.alphaFor(
        scrollOffsetPx = if (searchVm.phase == SearchPhase.IMMERSIVE_SEARCH) {
            TopFadePolicy.scrollOffsetFor(resultListState.firstVisibleItemIndex, resultListState.firstVisibleItemScrollOffset)
        } else {
            topScrollOffsetPx?.let { TopFadePolicy.scrollOffsetFor(it.first, it.second) } ?: 0f
        },
        fadeDistancePx = fadeDistancePx,
    )

    val searchContentBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop { drawContent() }
    val searchWallpaperBackdrop = com.otakup.niriko.ui.components.LocalCardGlassBackdrop.current
    val searchBackdrop = if (searchWallpaperBackdrop != null) {
        com.kyant.backdrop.backdrops.rememberCombinedBackdrop(searchWallpaperBackdrop, searchContentBackdrop)
    } else searchContentBackdrop
    CompositionLocalProvider(com.otakup.niriko.ui.components.LocalSearchGlassBackdrop provides searchBackdrop) {
    Box(modifier = modifier.fillMaxSize().then(Modifier.graphicsLayer {
        val progress = if (reduceMotion) 0f else backPreview.progress
        scaleX = 1f - 0.04f * progress
        scaleY = 1f - 0.04f * progress
        alpha = 1f - 0.15f * progress
    })) {
    Box(modifier = Modifier.fillMaxSize().layerBackdrop(searchContentBackdrop).topContentAlphaMask(strength = { topFadeAlpha }, hiddenHeight = TopFadePolicy.WINDOW_MASK_HIDDEN_DP.dp)) {
        // ===== 内容区：按阶段切换 =====
        when (searchVm.phase) {
            SearchPhase.COLLAPSED,
            SearchPhase.MENU_EXPANDED,
            SearchPhase.DRAGGING,
            -> {
                // 宫格版首页（第 5 轮 D28）：点入口 → 切到对应 mode 并回到卡片视图看全屏列表。
                // 这正是 Bangumi-master 的结构：宫格是首页，条目是独立页面。
                if (state.discoveryLayout == DiscoveryLayout.GRID && query.isBlank()) {
                    DiscoverGridPane(
                        state = state,
                        onPickMode = { mode ->
                            onSetTrendingMode(mode)
                            viewModel.setDiscoveryLayout(DiscoveryLayout.CARD)
                        },
                        onSubjectClick = onSubjectClick,
                        modifier = Modifier.fillMaxSize(),
                        topInset = contentTopInset,
                        onScrollPosChange = { index, offset -> topScrollOffsetPx = index to offset },
                        tabReselectEventId = tabReselectEventId,
                        tabReselect = reselectSignal,
                        onRefreshAtTop = { if (query.isBlank()) viewModel.refreshTrending() },
                    )
                } else {
                // 趋势区全屏（下拉刷新 + 滚动位置恢复 + 触底分页）；
                // 菜单展开（MENU/DRAGGING）时顶部避让，趋势作品卡片不被浮动菜单遮挡
                Column(Modifier.fillMaxSize()) {
                    // 卡片视图与海报视图共用的两个回调（第 3 轮参考计划 P0）。
                    // 两种布局共用同一份 trendingScrollIndex/Offset —— 布局互切后回到同一位置。
                    val onRetryLoad: () -> Unit = {
                        if (query.isBlank()) {
                            scope.launch { viewModel.refreshTrending() }
                        } else {
                            viewModel.onQueryChanged(query)
                        }
                    }
                    val onScrollPos: (Int, Int) -> Unit = { index, offset ->
                        val st = latestState.value
                        if (st.trendingResults.isNotEmpty()) {
                            viewModel.saveTrendingScrollPos(st.selectedType, index, offset)
                        }
                        // F13：同一份上报同时喂给顶部渐隐 —— 不再为它单独加一套滚动监听
                        topScrollOffsetPx = index to offset
                    }
                    // 历史排名：BrowseFilter 的 9 维度筛选器（第 6 轮 §6.3，「改动即查询」）。
                    // 关键词搜索的筛选面板（SearchFilterPanel）仍归搜索态使用，两者状态源不同。
                    val feedHeader: @Composable () -> Unit = {
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
                        // 第 3 轮参考计划 P0（R1 animeko）：海报视图 = 封面优先的无容器网格。
                        // 仅在 query 为空时生效（与宫格同规则）；搜索态一律走原路径。
                        if (state.discoveryLayout == DiscoveryLayout.POSTER && query.isBlank()) {
                            DiscoverPosterPane(
                                state = state,
                                query = query,
                                onLoadMore = viewModel::loadNextTrendingPage,
                                onSubjectClick = onSubjectClick,
                                onRetry = onRetryLoad,
                                onScrollPosChange = onScrollPos,
                                initialScrollPos = viewModel.getTrendingScrollPos(state.selectedType),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                lastUpdatedAt = trendingLastUpdatedAt,
                                onForceRefresh = { viewModel.refreshTrending() },
                                onOpenAllTime = { onSetTrendingMode(TrendingMode.ALL_TIME) },
                                tabReselectEventId = tabReselectEventId,
                                tabReselect = reselectSignal,
                                topInset = contentTopInset,
                                headerContent = feedHeader,
                            )
                        } else {
                            TrendingSection(
                                state = state,
                                query = query,
                                onLoadMore = viewModel::loadNextTrendingPage,
                                onSubjectClick = onSubjectClick,
                                onRetry = onRetryLoad,
                                onScrollPosChange = onScrollPos,
                                initialScrollPos = viewModel.getTrendingScrollPos(state.selectedType),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                lastUpdatedAt = trendingLastUpdatedAt,
                                onForceRefresh = { viewModel.refreshTrending() },
                                // 当季热门空态时的出口：切到历史排名（列表本体的尾部出口已按第 6 轮返工移除）
                                onOpenAllTime = { onSetTrendingMode(TrendingMode.ALL_TIME) },
                                tabReselectEventId = tabReselectEventId,
                                tabReselect = reselectSignal,
                                topInset = contentTopInset,
                                headerContent = feedHeader,
                            )
                        }
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
            // Insets belong to the floating controls, never the feed viewport.
            modifier = Modifier.align(Alignment.TopStart).padding(top = windowTopInset + 8.dp).zIndex(10f),
        )


    }
    }
}
