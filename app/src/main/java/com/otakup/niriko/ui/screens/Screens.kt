@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.otakup.niriko.data.model.CardRarity
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.cardRarityFromRating
import com.otakup.niriko.data.model.collection.CollectionListUiState
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.model.collection.TagInfo
import com.otakup.niriko.navigation.TopLevelDestination
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.adaptive.NirikoGridColumns
import com.otakup.niriko.ui.adaptive.currentNirikoWindowLayout
import com.otakup.niriko.ui.animation.AnimDurationNormal
import com.otakup.niriko.ui.animation.LocalAmbientTilt
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.orSnapFinite
import com.otakup.niriko.ui.animation.rememberAmbientTilt
import com.otakup.niriko.ui.bottombar.LibraryControlsState
import com.otakup.niriko.ui.common.LocalNirikoFeedback
import com.otakup.niriko.ui.common.SearchPhase
import com.otakup.niriko.ui.common.SearchViewModel
import com.otakup.niriko.ui.common.reportBottomBarScroll
import com.otakup.niriko.ui.components.NirikoFilterPill
import com.otakup.niriko.ui.components.NirikoMenu
import com.otakup.niriko.ui.components.NirikoMenuItem
import com.otakup.niriko.ui.components.NirikoTextField
import com.otakup.niriko.ui.components.SkeletonSubjectCard
import com.otakup.niriko.ui.components.StateHost
import com.otakup.niriko.ui.components.skeletonBaseColor
import com.otakup.niriko.ui.components.skeletonShimmer
import com.otakup.niriko.ui.components.topContentAlphaMask
import com.otakup.niriko.ui.library.CollectionCard
import com.otakup.niriko.ui.library.FolderNavBar
import com.otakup.niriko.ui.library.LibraryGalleryCard
import com.otakup.niriko.ui.library.LibraryViewStyle
import com.otakup.niriko.ui.library.PersonCollectionSection
import com.otakup.niriko.ui.library.PosterGridCard
import com.otakup.niriko.ui.library.PosterGridSkeletonCard
import com.otakup.niriko.ui.library.folderDisplayName
import com.otakup.niriko.ui.library.isUsableFolderName
import com.otakup.niriko.ui.theme.NirikoShapes
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.util.PHASE_REVEAL_DURATION_MS
import com.otakup.niriko.util.TopFadePolicy
import com.otakup.niriko.util.VIEW_TOGGLE_DIM_ALPHA
import com.otakup.niriko.util.beginScrollToTop
import com.otakup.niriko.util.phaseRevealStartAlpha
import com.otakup.niriko.util.shouldPlayPhaseReveal
import com.otakup.niriko.viewmodel.CollectionViewModel
import kotlinx.coroutines.launch

// ==================== 顶部页面指示（R9：Pager / Indicator 形态，自研实现） ====================

/** 指示器短段宽度（dp）：非当前页。 */
internal const val PAGE_INDICATOR_MIN_WIDTH_DP = 6f

/** 指示器长段宽度（dp）：当前页。 */
internal const val PAGE_INDICATOR_MAX_WIDTH_DP = 18f

/** 段间距（dp）：与改造前 Row 的 spacedBy(4.dp) 等值。 */
internal const val PAGE_INDICATOR_GAP_DP = 4f

/** 指示器高度（dp）：与改造前 Box 的 height = 3.dp 等值。 */
internal const val PAGE_INDICATOR_HEIGHT_DP = 3f

/**
 * 第 [index] 段的高亮权重（0..1）。
 * [pagePosition] = 当前页索引 + 拖动偏移小数，滑动过程中连续变化（跟手、无跳变）。
 */
internal fun pageIndicatorWeight(index: Int, pagePosition: Float): Float = (1f - kotlin.math.abs(index - pagePosition)).coerceIn(0f, 1f)

/** 第 [index] 段的宽度（dp）：高亮权重把短段拉到长段，形成「胶囊拉长」的连续反馈。 */
internal fun pageIndicatorWidthDp(index: Int, pagePosition: Float): Float = PAGE_INDICATOR_MIN_WIDTH_DP +
    (PAGE_INDICATOR_MAX_WIDTH_DP - PAGE_INDICATOR_MIN_WIDTH_DP) * pageIndicatorWeight(index, pagePosition)

/**
 * 顶层页面指示：4 段小胶囊，当前页被拉长并提亮。
 *
 * A.9-4：改造前在**组合期**读 [PagerState.currentPageOffsetFraction] 并按它算 4 个 Box 的 width
 * —— 拖动时每帧都会整段重组 + 4 次布局。现在整行交给一个 Canvas：pagePosition 只在
 * **绘制阶段**读（快照读取触发重绘而非重组），段宽/透明度在 draw 里算，观感不变。
 */
@Composable
fun TopLevelPageIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    segmentCount: Int = TopLevelDestination.entries.size,
) {
    val primary = MaterialTheme.colorScheme.primary
    // 总宽度是常量：拖动任意时刻相邻两段的权重之和恒为 1，所以
    // n*MIN + (MAX-MIN) + (n-1)*GAP 与改造前 Row 的实测宽度一致（4 段 = 48dp）。
    val totalWidth = (
        segmentCount * PAGE_INDICATOR_MIN_WIDTH_DP +
            (PAGE_INDICATOR_MAX_WIDTH_DP - PAGE_INDICATOR_MIN_WIDTH_DP) +
            (segmentCount - 1) * PAGE_INDICATOR_GAP_DP
        ).dp
    Canvas(
        modifier = modifier.size(width = totalWidth, height = PAGE_INDICATOR_HEIGHT_DP.dp),
    ) {
        val minWidthPx = PAGE_INDICATOR_MIN_WIDTH_DP.dp.toPx()
        val extraWidthPx = (PAGE_INDICATOR_MAX_WIDTH_DP - PAGE_INDICATOR_MIN_WIDTH_DP).dp.toPx()
        val gapPx = PAGE_INDICATOR_GAP_DP.dp.toPx()
        // A.9-4：故意在 draw 里读 pagerState —— 拖动只触发重绘，不重组、不重新测量。
        val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
        var left = 0f
        repeat(segmentCount) { index ->
            val weight = pageIndicatorWeight(index, pagePosition)
            val segmentWidth = minWidthPx + extraWidthPx * weight
            drawRoundRect(
                color = primary.copy(alpha = 0.28f + 0.72f * weight),
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
            left += segmentWidth + gapPx
        }
    }
}

/** 作品库 Tab。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    viewModel: CollectionViewModel,
    /** B8：与 pager 外层的悬浮顶栏共享的 UI 状态（由 MainPager rememberSaveable 持有）。 */
    controls: LibraryControlsState,
    /** B8：搜索态由 MainPager 提升，顶栏与页面共用同一个实例。 */
    searchVm: SearchViewModel,
    ambientActive: Boolean = false,
    onNavigateToSearch: () -> Unit = {},
    onSubjectClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    /** F11：底栏重选作品库事件（不在顶部则回顶；顶部重选无操作）。 */
    tabReselectEventId: Int = 0,
    tabReselect: com.otakup.niriko.navigation.TabReselectSignal =
        com.otakup.niriko.navigation.TabReselectSignal.None,
    windowTopInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val statusCounts by viewModel.statusCounts.collectAsState()
    // 合并放送通知点击（计划 B1-3）：切到「在看」筛选后清空请求
    LaunchedEffect(com.otakup.niriko.data.notification.AiringListFilterRequest.pending) {
        if (com.otakup.niriko.data.notification.AiringListFilterRequest.pending) {
            viewModel.updateFilter { it.copy(status = WatchStatus.WATCHING) }
            com.otakup.niriko.data.notification.AiringListFilterRequest.pending = false
        }
    }
    // 观察人物收藏（独立于作品收藏）
    val context = androidx.compose.ui.platform.LocalContext.current
    val personCollections by context.nirikoApp.personCollectionDao.observeAll()
        .collectAsState(initial = emptyList())
    // F09：分区导航条数据（含成员数与折叠态）
    val folders by viewModel.folders.collectAsState()

    com.otakup.niriko.ui.animation.CompletionCelebrationHost(
        events = viewModel.completionEvents,
        modifier = modifier,
    ) {
        LibraryScreenContent(
            state = state,
            statusCounts = statusCounts,
            controls = controls,
            searchVm = searchVm,
            windowTopInset = windowTopInset,
            ambientActive = ambientActive,
            personCollections = personCollections,
            onQueryChanged = { query -> viewModel.updateFilter { it.copy(keyword = query) } },
            onStatusFilter = { status -> viewModel.updateFilter { it.copy(status = status) } },
            onSortChange = { sort -> viewModel.updateFilter { it.copy(sort = sort) } },
            onTagFilter = { tags -> viewModel.updateFilter { it.copy(selectedTags = tags) } },
            onSubjectClick = onSubjectClick,
            onPersonClick = onPersonClick,
            onBatchUpdateStatus = viewModel::batchUpdateStatus,
            onBatchDelete = viewModel::batchDelete,
            onBatchAddToFolder = { folderId, ids -> viewModel.addToFolder(folderId, ids) },
            onNavigateToSearch = onNavigateToSearch,
            // F09：分区（作品库自定义分区）
            folders = folders,
            onSelectFolder = viewModel::selectFolder,
            onCreateFolder = { name, onCreated -> viewModel.createFolder(name, onCreated) },
            onRenameFolder = viewModel::renameFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onSetFolderCollapsed = viewModel::setFolderCollapsed,
            onMoveFolder = viewModel::moveFolder,
            onAddToFolder = { folderId, subjectId -> viewModel.addToFolder(folderId, subjectId) },
            onRemoveFromFolder = viewModel::removeFromFolder,
            onQueryFolderMembership = { subjectId, callback -> viewModel.folderIdsOf(subjectId, callback) },
            onStatusQuickSet = viewModel::updateStatus,
            onProgressSet = viewModel::setProgress,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            tabReselectEventId = tabReselectEventId,
            tabReselect = tabReselect,
            modifier = Modifier,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryScreenContent(
    state: CollectionListUiState,
    statusCounts: Map<WatchStatus, Int> = emptyMap(),
    /** B8：与顶栏共享的状态（写穿透到同一份 state）。 */
    controls: LibraryControlsState,
    /** B8：搜索态（由 MainPager 提升）。 */
    searchVm: SearchViewModel,
    windowTopInset: Dp = 0.dp,
    ambientActive: Boolean = false,
    personCollections: List<com.otakup.niriko.data.local.entity.PersonCollectionEntity>,
    onQueryChanged: (String) -> Unit,
    onStatusFilter: (WatchStatus?) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onTagFilter: (Set<String>) -> Unit = {},
    onSubjectClick: (Long) -> Unit,
    onPersonClick: (Long) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onStatusQuickSet: (Long, WatchStatus) -> Unit = { _, _ -> },
    onProgressSet: (subjectId: Long, value: Int, volume: Boolean) -> Unit = { _, _, _ -> },
    onBatchUpdateStatus: ((List<Long>, WatchStatus) -> Unit)? = null,
    onBatchDelete: ((List<Long>) -> Unit)? = null,
    /** F09：批量加入分区（§10.2 第 4 条：全局批量选择按 subjectId 去重由 ViewModel 保证）。 */
    onBatchAddToFolder: (Long, List<Long>) -> Unit = { _, _ -> },
    /** F09：分区列表（含成员数与折叠态）。 */
    folders: List<com.otakup.niriko.data.model.collection.LibraryFolderSummary> = emptyList(),
    onSelectFolder: (Long?) -> Unit = {},
    onCreateFolder: (String, (Long) -> Unit) -> Unit = { _, _ -> },
    onRenameFolder: (Long, String) -> Unit = { _, _ -> },
    onDeleteFolder: (Long) -> Unit = {},
    onSetFolderCollapsed: (Long, Boolean) -> Unit = { _, _ -> },
    /** F09：调整分区顺序（offset = -1 上移 / +1 下移）。 */
    onMoveFolder: (Long, Int) -> Unit = { _, _ -> },
    onAddToFolder: (Long, Long) -> Unit = { _, _ -> },
    onRemoveFromFolder: (Long, Long) -> Unit = { _, _ -> },
    /** F09：查询某作品所属分区（加入分区弹层显示当前勾选状态）。 */
    onQueryFolderMembership: (Long, (Set<Long>) -> Unit) -> Unit = { _, callback -> callback(emptySet()) },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    tabReselectEventId: Int = 0,
    tabReselect: com.otakup.niriko.navigation.TabReselectSignal =
        com.otakup.niriko.navigation.TabReselectSignal.None,
    modifier: Modifier = Modifier,
) {
    // B8：这些状态已提升到 LibraryControlsState（顶栏搬到了 pager 外层，必须共用同一份）。
    // 用 `by controls.xxxState` 委托：读写写法与改造前一致，写穿透到同一个 state，
    // 因此内容层业务零改动；toggleSelect 也委托到 holder（内容与顶栏同一套选中集）。
    var showSortMenu by controls.showSortMenuState
    var showViewMenu by controls.showViewMenuState
    var viewStyle by controls.viewStyleState
    var multiSelect by controls.multiSelectState
    var showDeleteConfirm by controls.showDeleteConfirmState
    var selectedIds by controls.selectedIdsState
    fun toggleSelect(id: Long) = controls.toggleSelect(id)
    var showTagFilters by controls.showTagFiltersState
    var showBatchActions by controls.showBatchActionsState
    val gestureLock = com.otakup.niriko.navigation.LocalSearchGestureLock.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // F03：全局反馈宿主（挂在 MainActivity 窗口层，能盖住 dock 与详情页之外的页面）。
    // 无人提供时是 null —— 所有调用点都用 `?.show(...)` 跳过，预览/单测里不会崩。
    val feedback = LocalNirikoFeedback.current
    // V-14：长按浮层的状态提到页面级 —— 菜单是「从卡片长出来」的锚定浮层，必须与正文同级。
    // target 保留到菜单退场结束（onDismiss 只把 expanded 置 false），否则退场那一帧内容会突然空掉。
    var longPressTargetId by remember { mutableStateOf<Long?>(null) }
    var longPressAnchor by remember { mutableStateOf<Rect?>(null) }
    var longPressMenuExpanded by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = searchVm.phase != SearchPhase.COLLAPSED) {
        focusManager.clearFocus()
        keyboard?.hide()
        searchVm.collapse()
    }
    LaunchedEffect(state.filter.keyword) { searchVm.syncQuery(state.filter.keyword) }
    // F09：分区对话框状态（新建 / 长按菜单 / 改名 / 删除 / 加入分区）
    var folderPickerSubjectId by remember { mutableStateOf<Long?>(null) }
    var folderPickerMembership by remember { mutableStateOf(emptySet<Long>()) }
    // 批量加入分区弹层（多选状态下的「加入分区」）
    var showBatchFolderPicker by remember { mutableStateOf(false) }
    var batchFolderName by remember { mutableStateOf("") }
    // 分区对话框同样由 holder 持有：顶栏里的 FolderNavBar 要能打开这些对话框
    var showCreateFolder by controls.showCreateFolderState
    var folderMenuTarget by controls.folderMenuTargetState
    var renameFolderTarget by controls.renameFolderTargetState
    var deleteFolderTarget by controls.deleteFolderTargetState
    var newFolderName by controls.newFolderNameState
    // 批次 C · 8.5：分区海报入口。数据 = 当前筛选结果（选中分区时就是该分区）+ 分区名。
    val posterContext = androidx.compose.ui.platform.LocalContext.current
    val posterScope = rememberCoroutineScope()
    val shareFolderPoster: () -> Unit = {
        val folderId = state.filter.folderId
        val folderName = folders.firstOrNull { it.id == folderId }?.name ?: "我的作品库"
        val coverUrls = state.items.mapNotNull { it.subject.coverUrl }
        val total = state.items.size
        posterScope.launch {
            com.otakup.niriko.ui.share.SharePosterExport.shareFolderPoster(
                context = posterContext,
                folderName = folderName,
                coverUrls = coverUrls,
                totalCount = total,
            )
        }
    }
    // B2 窗口尺寸类别：海报网格列数按宽度档位取 3 / 4 / 6。
    // COMPACT 绑定现状的 3 列（手机端回归零变化是硬要求）。
    val windowLayout = currentNirikoWindowLayout()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val galleryState = rememberLazyGridState()
    var visibleRarity by remember { mutableStateOf(false) }
    LaunchedEffect(viewStyle, state.items) {
        snapshotFlow {
            val indices = when (viewStyle) {
                LibraryViewStyle.LIST -> listState.layoutInfo.visibleItemsInfo.map { it.index }
                LibraryViewStyle.GRID -> gridState.layoutInfo.visibleItemsInfo.map { it.index }
                LibraryViewStyle.GALLERY -> galleryState.layoutInfo.visibleItemsInfo.map { it.index }
            }
            indices.any { index ->
                state.items.getOrNull(index)?.let { cardRarityFromRating(it.collection.rating) != CardRarity.STANDARD } == true
            }
        }.collect { visibleRarity = it }
    }
    val scrollToTopReduceMotion = LocalReduceMotion.current
    // F11：底栏重选作品库 —— 按当前视图样式选中的那个滚动容器回顶。
    // 作品库在顶部重选是**无操作**（没有「重选即刷新」语义，不误发请求）。
    LaunchedEffect(tabReselectEventId) {
        if (tabReselectEventId <= 0 || tabReselect.id <= 0) return@LaunchedEffect
        if (tabReselect.page != TopLevelDestination.Library.ordinal) return@LaunchedEffect
        when (viewStyle) {
            LibraryViewStyle.LIST -> listState.beginScrollToTop(reduceMotion = scrollToTopReduceMotion)
            LibraryViewStyle.GRID -> gridState.beginScrollToTop(reduceMotion = scrollToTopReduceMotion)
            LibraryViewStyle.GALLERY -> galleryState.beginScrollToTop(reduceMotion = scrollToTopReduceMotion)
        }
    }
    // B-7：列表 ↔ 网格 ↔ 海报墙换视图时给内容层一次 alpha 包络（呼吸式替换）。
    // 不用 AnimatedContent 的原因见 util/PhaseRevealPolicy.kt 顶部注释：那是双份 Lazy 组合，踩性能红线。
    val viewToggleReduceMotion = LocalReduceMotion.current
    var previousViewStyle by remember { mutableStateOf<LibraryViewStyle?>(null) }
    val viewToggleAlpha = remember { Animatable(1f) }
    LaunchedEffect(viewStyle) {
        if (shouldPlayPhaseReveal(previousViewStyle, viewStyle, viewToggleReduceMotion)) {
            val start = phaseRevealStartAlpha(VIEW_TOGGLE_DIM_ALPHA, viewToggleReduceMotion)
            viewToggleAlpha.snapTo(start)
            viewToggleAlpha.animateTo(1f, tween(PHASE_REVEAL_DURATION_MS))
        }
        previousViewStyle = viewStyle
    }
    val ambient = rememberAmbientTilt(ambientActive, visibleRarity)
    val libraryContentBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop { drawContent() }
    val wallpaperBackdrop = com.otakup.niriko.ui.components.LocalCardGlassBackdrop.current
    val searchBackdrop = if (wallpaperBackdrop != null) {
        com.kyant.backdrop.backdrops.rememberCombinedBackdrop(wallpaperBackdrop, libraryContentBackdrop)
    } else {
        libraryContentBackdrop
    }

    CompositionLocalProvider(
        LocalAmbientTilt provides ambient,
        com.otakup.niriko.ui.components.LocalSearchGlassBackdrop provides searchBackdrop,
    ) {
        // Expanded search and selector overlays never alter this initial safe space.
        val headerTopPadding = windowTopInset + TopFadePolicy.searchContentPaddingDp(56f).dp
        val showPersons = personCollections.isNotEmpty() && state.filter.keyword.isBlank() &&
            state.filter.status == null && state.filter.folderId == null
        // F13：顶部**连续**渐隐的输入 —— 按当前视图样式取对应滚动状态的 index+offset，
        // 首项索引 > 0 时视为「已滚很远」（TopFadePolicy.scrollOffsetFor），避免渐隐忽明忽暗。
        val fadeDistancePx = with(LocalDensity.current) { TopFadePolicy.FADE_DISTANCE_DP.dp.toPx() }
        val headerFadeAlpha = when (viewStyle) {
            LibraryViewStyle.LIST -> TopFadePolicy.alphaFor(
                TopFadePolicy.scrollOffsetFor(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset),
                fadeDistancePx,
            )

            LibraryViewStyle.GRID -> TopFadePolicy.alphaFor(
                TopFadePolicy.scrollOffsetFor(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset),
                fadeDistancePx,
            )

            LibraryViewStyle.GALLERY -> TopFadePolicy.alphaFor(
                TopFadePolicy.scrollOffsetFor(galleryState.firstVisibleItemIndex, galleryState.firstVisibleItemScrollOffset),
                fadeDistancePx,
            )
        }
        Box(modifier = modifier.fillMaxSize()) {
            // Only the feed is masked; the existing header stays a sibling overlay.
            // 渐隐上边界 = **窗口原点（屏幕顶部，含状态栏区域）**，与 v1.2.0 发现页一致。
            // 不再用 inset + 56dp 的「实心台」：那是内容避让量（headerTopPadding 仍在用它），不是遮罩起点。
            Box(Modifier.fillMaxSize().layerBackdrop(libraryContentBackdrop).topContentAlphaMask(strength = { headerFadeAlpha }, hiddenHeight = 0.dp)) {
                // 列表 / 空状态 / 首屏加载骨架（B4）
                // B-13：首屏骨架 / 空态 / 正文三态换页走 StateHost 的 alpha 包络（单实例、按 key 变化播放）。
                // 正文是三棵 Lazy 之一：这里只能做 alpha 包络，AnimatedContent 会同时组合两棵 Lazy。
                val libraryPhaseKey = when {
                    state.isLoading && state.items.isEmpty() -> "loading"
                    state.items.isEmpty() && !showPersons -> "empty"
                    else -> "content"
                }
                StateHost(stateKey = libraryPhaseKey) {
                    if (state.isLoading && state.items.isEmpty()) {
                        // B2：骨架与正式列表共用同一顶部内距，加载首帧不再顶到状态栏、也不会被悬浮行压住。
                        LibrarySkeleton(
                            viewStyle = viewStyle,
                            topPadding = TopFadePolicy.feedTopPaddingDp(windowTopInset.value).dp,
                        )
                    } else if (state.items.isEmpty() && !showPersons) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (state.filter.keyword.isNotBlank()) {
                                    Icon(Icons.Outlined.SearchOff, contentDescription = "无搜索结果", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text("没有找到匹配的作品", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("试试其他关键词", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = "暂无收藏", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    if (state.filter.status == null) {
                                        Text("还没有收藏作品", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("探索你喜欢的动画，它们会出现在这里", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                        Spacer(Modifier.height(16.dp))
                                        Button(onClick = onNavigateToSearch) { Text("开始探索") }
                                    } else {
                                        Text("没有符合条件的收藏", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    } else {
                        when (viewStyle) {
                            LibraryViewStyle.LIST -> LazyColumn(
                                state = listState,
                                modifier = Modifier.reportBottomBarScroll().graphicsLayer { alpha = viewToggleAlpha.value },
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = headerTopPadding, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (showPersons) {
                                    item(key = "person_collections") { PersonCollectionSection(personCollections, onPersonClick) }
                                }
                                items(state.items, key = { it.collection.id }) { item ->
                                    val haptic = LocalHapticFeedback.current
                                    // V-14：卡片本体就是长按浮层的锚点（菜单已提到页面级，见下方 anchorInRoot）。
                                    var cardBounds by remember { mutableStateOf<Rect?>(null) }
                                    Box(
                                        Modifier.animateItem()
                                            .onGloballyPositioned { cardBounds = it.boundsInRoot() },
                                    ) {
                                        CollectionCard(
                                            subject = item.subject,
                                            status = item.collection.status,
                                            watchedEpisodes = item.collection.watchedEpisodes,
                                            totalEpisodes = item.subject.totalEpisodes,
                                            watchedVolumes = item.collection.watchedVolumes,
                                            totalVolumes = item.subject.volumes,
                                            myRating = item.collection.rating,
                                            updateTime = item.collection.updateTime,
                                            personalTags = item.collection.personalTags,
                                            steam = state.steamGames[item.subject.subjectId],
                                            onClick = { if (multiSelect) toggleSelect(item.subject.subjectId) else onSubjectClick(item.subject.subjectId) },
                                            // F13：多选态下长按不再弹状态浮层 —— 那会把整屏遮住、且与「点卡片切换选中」
                                            // 两套语义打架。触感只留给真正会弹浮层的那次长按，否则是假反馈。
                                            onLongClick = if (multiSelect) {
                                                null
                                            } else {
                                                (
                                                    {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        longPressAnchor = cardBounds
                                                        longPressTargetId = item.subject.subjectId
                                                        longPressMenuExpanded = true
                                                    }
                                                    )
                                            },
                                            // F01：列表视图此前点卡片能切换选中却没有任何视觉反馈（栅格/画廊早就有 selected）
                                            selected = multiSelect && item.subject.subjectId in selectedIds,
                                            sharedElementKey = "cover_${item.subject.subjectId}",
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            modifier = Modifier.animateContentSize(animationSpec = tween<IntSize>(durationMillis = AnimDurationNormal).orSnapFinite(LocalReduceMotion.current)),
                                        )
                                    }
                                }
                            }

                            LibraryViewStyle.GRID -> LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(NirikoGridColumns.fromLayout(windowLayout)),
                                modifier = Modifier.reportBottomBarScroll().graphicsLayer { alpha = viewToggleAlpha.value },
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = headerTopPadding, bottom = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (showPersons) {
                                    item(key = "person_collections", span = { GridItemSpan(maxLineSpan) }) {
                                        PersonCollectionSection(personCollections, onPersonClick)
                                    }
                                }
                                gridItems(state.items, key = { it.collection.id }) { item ->
                                    val haptic = LocalHapticFeedback.current
                                    // V-14：卡片本体就是长按浮层的锚点（菜单已提到页面级，见下方 anchorInRoot）。
                                    var cardBounds by remember { mutableStateOf<Rect?>(null) }
                                    Box(
                                        Modifier.animateItem()
                                            .onGloballyPositioned { cardBounds = it.boundsInRoot() },
                                    ) {
                                        PosterGridCard(
                                            subject = item.subject,
                                            collection = item.collection,
                                            totalEpisodes = item.subject.totalEpisodes,
                                            onClick = { if (multiSelect) toggleSelect(item.subject.subjectId) else onSubjectClick(item.subject.subjectId) },
                                            // F13：同列表视图 —— 多选态下长按只保留「切换选中」这一种含义，不弹浮层、不给触感。
                                            onLongClick = if (multiSelect) {
                                                null
                                            } else {
                                                (
                                                    {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        longPressAnchor = cardBounds
                                                        longPressTargetId = item.subject.subjectId
                                                        longPressMenuExpanded = true
                                                    }
                                                    )
                                            },
                                            selected = item.subject.subjectId in selectedIds,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            LibraryViewStyle.GALLERY -> LazyVerticalGrid(
                                state = galleryState,
                                columns = GridCells.Fixed(1),
                                modifier = Modifier.reportBottomBarScroll().graphicsLayer { alpha = viewToggleAlpha.value },
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = headerTopPadding, bottom = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (showPersons) {
                                    item(key = "person_collections", span = { GridItemSpan(maxLineSpan) }) {
                                        PersonCollectionSection(personCollections, onPersonClick)
                                    }
                                }
                                gridItems(state.items, key = { it.collection.id }) { item ->
                                    LibraryGalleryCard(
                                        subject = item.subject,
                                        collection = item.collection,
                                        onClick = { if (multiSelect) toggleSelect(item.subject.subjectId) else onSubjectClick(item.subject.subjectId) },
                                        selected = item.subject.subjectId in selectedIds,
                                        sharedElementKey = "cover_" + item.subject.subjectId,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // V-14：长按浮层（状态 / 进度快捷）放在这一层 = 与正文同级：它是从卡片长出来的锚定浮层，
            // 塞进 Lazy item 里会随 item 回收一起消失，也会被 item 的宽度约束裁掉。
            // 数据按 targetId 回查（不复制 item 快照），这样筛选 / 刷新后菜单永远对得上当前数据。
            val longPressItem = longPressTargetId?.let { id ->
                state.items.firstOrNull { it.subject.subjectId == id }
            }
            LibraryLongPressMenu(
                expanded = longPressMenuExpanded && longPressItem != null,
                anchorInRoot = longPressAnchor,
                subjectType = longPressItem?.subject?.type,
                watchedEpisodes = longPressItem?.collection?.watchedEpisodes,
                watchedVolumes = longPressItem?.collection?.watchedVolumes,
                totalEpisodes = longPressItem?.subject?.totalEpisodes,
                totalVolumes = longPressItem?.subject?.volumes,
                currentStatus = longPressItem?.collection?.status,
                onDismiss = { longPressMenuExpanded = false },
                onStatusSet = { status ->
                    longPressMenuExpanded = false
                    longPressItem?.let { onStatusQuickSet(it.subject.subjectId, status) }
                },
                onProgressSet = { value, volume ->
                    longPressItem?.let { onProgressSet(it.subject.subjectId, value, volume) }
                },
                // F09：长按 → 加入分区
                onAddToFolder = {
                    longPressMenuExpanded = false
                    longPressItem?.let { folderPickerSubjectId = it.subject.subjectId }
                },
                onShareFolderPoster = {
                    longPressMenuExpanded = false
                    shareFolderPoster()
                },
            )
            // B8：顶栏（左分区胶囊 + 右搜索按钮）已搬到 pager 外层的 LibraryTopBar（ui/screens/LibraryTopBar.kt），
            // 这里不再渲染 —— 翻页时按钮 x/y 不动，只有内容切换。
            if (showTagFilters) {
                AlertDialog(
                    onDismissRequest = { showTagFilters = false },
                    title = { Text("个人标签") },
                    text = {
                        TagFilterBar(state.availableTags, state.filter.selectedTags, onTagClick = { tag ->
                            onTagFilter(
                                if (tag in state.filter.selectedTags) {
                                    state.filter.selectedTags - tag
                                } else {
                                    state.filter.selectedTags + tag
                                }
                            )
                        })
                    },
                    confirmButton = { TextButton(onClick = { showTagFilters = false }) { Text("完成") } },
                    dismissButton = { TextButton(onClick = { onTagFilter(emptySet()) }) { Text("清除") } },
                )
            }
            if (showBatchActions) {
                AlertDialog(
                    onDismissRequest = { showBatchActions = false },
                    title = { Text("管理所选 " + selectedIds.size + " 个收藏") },
                    text = {
                        Column {
                            TextButton(onClick = { selectedIds = state.items.map { it.subject.subjectId }.toSet() }) { Text("全选当前结果") }
                            WatchStatus.entries.forEach { status ->
                                TextButton(enabled = selectedIds.isNotEmpty(), onClick = {
                                    val ids = selectedIds.toList()
                                    onBatchUpdateStatus?.invoke(ids, status)
                                    // F03：批量动作此前收尾只清选中集，用户看不到任何回执。
                                    // 注意 CollectionViewModel.batchUpdateStatus 是 fire-and-forget、
                                    // 不回传成功/失败，所以这里只能给「已提交」口径的乐观提示。
                                    feedback?.show("已把 " + ids.size + " 个收藏设为「" + status.label + "」")
                                    selectedIds = emptySet()
                                    multiSelect = false
                                    showBatchActions = false
                                }) { Text(status.label) }
                            }
                            TextButton(enabled = selectedIds.isNotEmpty(), onClick = {
                                showBatchActions = false
                                showBatchFolderPicker = true
                            }) { Text("加入分区") }
                            TextButton(enabled = selectedIds.isNotEmpty(), onClick = {
                                showBatchActions = false
                                showDeleteConfirm = true
                            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showBatchActions = false }) { Text("完成") } },
                )
            }
            // 批量删除二次确认（计划 B2-2）：说明数量与不可撤销
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("删除所选收藏") },
                    text = {
                        Text(
                            "将删除所选 " + selectedIds.size +
                                " 个收藏（含进度与评分），删除后不可撤销。",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            val ids = selectedIds.toList()
                            onBatchDelete?.invoke(ids)
                            // F03：删除不可撤销（弹窗已明说），所以只给「已删除」回执，不给 Undo
                            // （提供撤销入口但没有恢复能力比没提示更糟）。
                            feedback?.show("已删除 " + ids.size + " 个收藏")
                            selectedIds = emptySet()
                            multiSelect = false
                        }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                    },
                )
            }
        }
    }

    // ===== F09：分区对话框（新建 / 长按菜单 / 改名 / 删除） =====

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false },
            title = { Text("新建分区") },
            text = {
                NirikoTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("分区名") },
                    placeholder = { Text("例如：本季新番") },
                )
            },
            confirmButton = {
                TextButton(
                    // 空名不可确认：与 ViewModel 的落库前校验同一条规则（isUsableFolderName）
                    enabled = isUsableFolderName(newFolderName),
                    onClick = {
                        val name = newFolderName
                        showCreateFolder = false
                        // 新建后立刻切到该分区：用户的意图就是「去我刚建的分区里加东西」
                        onCreateFolder(name) { id -> onSelectFolder(id) }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolder = false }) { Text("取消") } },
        )
    }

    folderMenuTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { folderMenuTarget = null },
            title = { Text(folderDisplayName(target.name)) },
            text = { Text("这个分区里有 ${target.memberCount} 部作品。") },
            confirmButton = {
                TextButton(onClick = {
                    folderMenuTarget = null
                    renameFolderTarget = target
                    newFolderName = target.name
                }) { Text("重命名") }
            },
            dismissButton = {
                Column {
                    // 排序（§10.2）：整表重排，不交换相邻两行 —— 中途失败也不会留下相同的顺序值
                    val index = folders.indexOfFirst { it.id == target.id }
                    Row {
                        TextButton(
                            enabled = index > 0,
                            onClick = {
                                onMoveFolder(target.id, -1)
                                folderMenuTarget = null
                            },
                        ) { Text("上移") }
                        TextButton(
                            enabled = index >= 0 && index < folders.lastIndex,
                            onClick = {
                                onMoveFolder(target.id, 1)
                                folderMenuTarget = null
                            },
                        ) { Text("下移") }
                    }
                    // 折叠状态持久化（§10.1）：收起的分区在导航条上只留名字
                    TextButton(onClick = {
                        onSetFolderCollapsed(target.id, !target.isCollapsed)
                        folderMenuTarget = null
                    }) { Text(if (target.isCollapsed) "展开分区" else "收起分区") }
                    TextButton(onClick = {
                        folderMenuTarget = null
                        deleteFolderTarget = target
                    }) { Text("删除分区") }
                }
            },
        )
    }

    renameFolderTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameFolderTarget = null },
            title = { Text("重命名分区") },
            text = {
                NirikoTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("分区名") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = isUsableFolderName(newFolderName),
                    onClick = {
                        val name = newFolderName
                        renameFolderTarget = null
                        onRenameFolder(target.id, name)
                    },
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameFolderTarget = null }) { Text("取消") } },
        )
    }

    deleteFolderTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteFolderTarget = null },
            title = { Text("删除分区「${target.name}」？") },
            // 措辞必须准确：删的是分区，不是作品（§14：删除作品分区只删除关系表记录）
            text = { Text("只会删除这个分区与它的分组关系，收藏里的作品不会受到影响。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteFolderTarget = null
                    onDeleteFolder(target.id)
                }) { Text("删除分区") }
            },
            dismissButton = { TextButton(onClick = { deleteFolderTarget = null }) { Text("取消") } },
        )
    }

    // 批量加入分区弹层：列出全部分区，点一下把当前选中的全部作品加进去
    if (showBatchFolderPicker) {
        AlertDialog(
            onDismissRequest = { showBatchFolderPicker = false },
            title = { Text("把所选的 " + selectedIds.size + " 部作品加入分区") },
            text = {
                Column {
                    if (folders.isEmpty()) {
                        Text("还没有分区。先在作品库顶部的分区条里新建一个。")
                    }
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onBatchAddToFolder(folder.id, selectedIds.toList())
                                    selectedIds = emptySet()
                                    multiSelect = false
                                    showBatchFolderPicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(folder.name, modifier = Modifier.weight(1f))
                            Text(
                                text = "${folder.memberCount} 部",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBatchFolderPicker = false }) { Text("取消") } },
        )
    }

    // 加入分区弹层：列出全部分区，点一下在「属于 / 不属于」之间切换（加入 / 移出）
    folderPickerSubjectId?.let { subjectId ->
        androidx.compose.runtime.LaunchedEffect(subjectId) {
            onQueryFolderMembership(subjectId) { ids -> folderPickerMembership = ids }
        }
        AlertDialog(
            onDismissRequest = { folderPickerSubjectId = null },
            title = { Text("加入分区") },
            text = {
                Column {
                    if (folders.isEmpty()) {
                        Text("还没有分区。先在作品库顶部的分区条里新建一个。")
                    }
                    folders.forEach { folder ->
                        val isMember = folder.id in folderPickerMembership
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (isMember) {
                                        onRemoveFromFolder(folder.id, subjectId)
                                        folderPickerMembership = folderPickerMembership - folder.id
                                    } else {
                                        onAddToFolder(folder.id, subjectId)
                                        folderPickerMembership = folderPickerMembership + folder.id
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(folder.name, modifier = Modifier.weight(1f))
                            if (isMember) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "已加入",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { folderPickerSubjectId = null }) { Text("完成") } },
        )
    }
}

/**
 * 作品库首屏骨架（B4）：三种视图各有对应骨架，几何与真实卡片一致
 * （网格：3 列 × 10.dp 间距 × 2:3 海报；列表：横向玻璃卡；画廊：340.dp 大卡），
 * 因此数据到达替换为真实内容时不跳版。
 */
@Composable
private fun LibrarySkeleton(
    viewStyle: LibraryViewStyle,
    /** 与正式列表相同的顶部内距（inset + 控件行 + 呼吸）；骨架因此不会顶到状态栏或被悬浮行盖住。 */
    topPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    when (viewStyle) {
        LibraryViewStyle.GRID -> Column(
            modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = topPadding + 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(3) {
                        PosterGridSkeletonCard(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        LibraryViewStyle.LIST -> Column(
            modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = topPadding + 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) { SkeletonSubjectCard() }
        }

        LibraryViewStyle.GALLERY -> Column(
            modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = topPadding + 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(NirikoShapes.SheetShape)
                        .background(skeletonBaseColor())
                        .skeletonShimmer(),
                )
            }
        }
    }
}

/**
 * 收藏页长按浮层（计划 B1-2）：状态快捷 + 进度 − / +。
 *
 * 进度行只在 [ProgressBumpPolicy] 支持的类型上出现（动画/三次元=集、书籍漫画=卷、游戏=分钟）；
 * 音乐等类型只显示状态项。加到上限不会自动切换「看过」（用户确认：不自动）。
 */
@Composable
private fun LibraryLongPressMenu(
    expanded: Boolean,
    /** V-14：锚点（根坐标）。null = 还没量到 / 目标已失效，NirikoMenu 内部据此不组合内容。 */
    anchorInRoot: Rect?,
    subjectType: com.otakup.niriko.data.model.SubjectType?,
    watchedEpisodes: Int?,
    watchedVolumes: Int?,
    totalEpisodes: Int?,
    /** 总卷数：给了之后「卷」的 + 才会停在卷数上限（B11 与进度条同口径）。 */
    totalVolumes: Int? = null,
    currentStatus: WatchStatus?,
    onDismiss: () -> Unit,
    onStatusSet: (WatchStatus) -> Unit,
    onProgressSet: (value: Int, volume: Boolean) -> Unit,
    /**
     * F09：点「加入分区…」。null = 当前装配没有分区能力（入口不显示，而不是点了没反应）。
     */
    onAddToFolder: (() -> Unit)? = null,
    /** 批次 C · 8.5：把一个分区渲染成多页海报并分享。null = 不显示入口。 */
    onShareFolderPoster: (() -> Unit)? = null,
) {
    val snapshot = subjectType?.let { type ->
        com.otakup.niriko.util.ProgressBumpPolicy.snapshot(
            type = type,
            watchedEpisodes = watchedEpisodes,
            watchedVolumes = watchedVolumes,
            totalEpisodes = totalEpisodes,
            totalVolumes = totalVolumes,
        )
    }
    // V-14：换成共享锚定菜单（玻璃面 + 从锚点缩放 + 内建退场），不再用 M3 DropdownMenu。
    NirikoMenu(
        expanded = expanded,
        anchorInRoot = anchorInRoot,
        onDismissRequest = onDismiss,
        // 列表卡片靠左排，菜单左缘对齐卡片左缘：看起来像从卡片里长出来。
        alignRight = false,
    ) {
        if (snapshot != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "进度 " + com.otakup.niriko.util.ProgressBumpPolicy.label(snapshot),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        onProgressSet(
                            com.otakup.niriko.util.ProgressBumpPolicy.next(snapshot, -1),
                            snapshot.unit == com.otakup.niriko.util.ProgressUnit.VOLUME,
                        )
                    },
                    enabled = snapshot.value > 0,
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "进度减一", modifier = Modifier.size(18.dp))
                }
                // F04：到总集数/总卷数上限后禁掉加号（M3 IconButton 禁用态自带 0.38 透明度，
                // 与减号侧一致）；total 为 null 表示「未知总数」，不设上限。
                IconButton(
                    onClick = {
                        onProgressSet(
                            com.otakup.niriko.util.ProgressBumpPolicy.next(snapshot, 1),
                            snapshot.unit == com.otakup.niriko.util.ProgressUnit.VOLUME,
                        )
                    },
                    enabled = snapshot.total?.let { snapshot.value < it } ?: true,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "进度加一", modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        }
        WatchStatus.entries.forEach { status ->
            // V-14：NirikoMenuItem 只有前置图标槽，所以「当前状态」的勾由行首图标体现。
            NirikoMenuItem(
                text = status.label,
                onClick = { onStatusSet(status) },
                icon = if (currentStatus == status) Icons.Filled.Check else null,
            )
        }
        // F09：加入分区（弹层，选择分区后加入）
        if (onAddToFolder != null) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            NirikoMenuItem(
                text = "加入分区…",
                onClick = onAddToFolder,
                icon = Icons.Filled.Add,
            )
        }
        if (onShareFolderPoster != null) {
            NirikoMenuItem(
                text = "生成本分区海报",
                onClick = onShareFolderPoster,
                icon = Icons.Filled.Share,
            )
        }
    }
}

@Composable
private fun TagFilterBar(
    tags: List<TagInfo>,
    selectedTags: Set<String>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.forEach { tagInfo ->
            // V-12：M3 FilterChip 与页面其它胶囊不是一族（8dp 方角 + 实心 primaryContainer），
            // 而且把「名字 / 计数」拆成两段 labelSmall；换成共享筛选胶囊，计数拼进同一段文字。
            NirikoFilterPill(
                text = tagInfo.name + " " + tagInfo.count,
                selected = tagInfo.name in selectedTags,
                onClick = { onTagClick(tagInfo.name) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: com.otakup.niriko.viewmodel.StatsViewModel? = null,
    onSubjectClick: (Long) -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    /** F11：底栏重选统计页事件，透传给真正的统计页（不在顶部则回顶）。 */
    tabReselectEventId: Int = 0,
    tabReselect: com.otakup.niriko.navigation.TabReselectSignal =
        com.otakup.niriko.navigation.TabReselectSignal.None,
) {
    if (viewModel != null) {
        com.otakup.niriko.ui.stats.StatsScreen(
            viewModel = viewModel,
            onSubjectClick = onSubjectClick,
            onNavigateToDiscover = onNavigateToDiscover,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            tabReselectEventId = tabReselectEventId,
            tabReselect = tabReselect,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("统计", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    NirikoTheme {
        LibraryScreenContent(
            state = CollectionListUiState(),
            controls = remember { LibraryControlsState() },
            searchVm = remember { SearchViewModel() },
            personCollections = emptyList(),
            onQueryChanged = {},
            onStatusFilter = {},
            onSortChange = {},
            onSubjectClick = {},
        )
    }
}
