package com.otakup.niriko.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.otakup.niriko.ui.adaptive.NirikoNavSuite
import com.otakup.niriko.ui.adaptive.NirikoWindowLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.screens.LibraryScreen
import com.otakup.niriko.ui.screens.LibraryTopBar
import com.otakup.niriko.ui.subject.DiscoverTopBar
import com.otakup.niriko.ui.bottombar.LibraryControlsState
import com.otakup.niriko.ui.bottombar.PagerTopBar
import com.otakup.niriko.ui.bottombar.topBarSlotFor
import com.otakup.niriko.ui.common.SearchViewModel
import com.otakup.niriko.ui.components.LocalSearchGlassBackdrop
import com.otakup.niriko.ui.common.LocalFloatingControls
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.otakup.niriko.ui.common.SearchPhase
import com.otakup.niriko.ui.bottombar.floatingBarRestoreOnBlankTap
import com.otakup.niriko.ui.screens.StatsScreen
import com.otakup.niriko.ui.settings.SettingsScreen
import com.otakup.niriko.ui.subject.SubjectSearchScreen
import com.otakup.niriko.viewmodel.CollectionViewModel
import com.otakup.niriko.viewmodel.CollectionViewModelFactory
import com.otakup.niriko.viewmodel.SubjectSearchViewModel
import com.otakup.niriko.viewmodel.SubjectSearchViewModelFactory
import kotlinx.coroutines.launch

/**
 * 顶层四页 HorizontalPager（对齐 SukiSU-Ultra MainPagerState 架构）。
 * - 手势左右滑动切换页面，指示器通过 [pagerState] 连续跟手
 * - tab 点击 = pagerState.animateScrollToPage（Pager 弹簧滚动）
 * - ViewModel owner = main backStackEntry → 跨页滑动存活，不重建
 * - [sharedTransitionScope]/[animatedVisibilityScope] 下传给列表页，实现封面共享元素过渡
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainPager(
    pagerState: PagerState,
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    backdrop: Backdrop,
    windowLayout: NirikoWindowLayout,
    onDestinationSelected: (Int) -> Unit = {},
    onTabReselected: (Int) -> Unit = {},
    /**
     * 底栏「重选当前 tab」事件（F11）。
     *
     * @param tabReselectEventId 单调递增的事件号（0 = 尚未重选过）；内容层用
     *   `LaunchedEffect(tabReselectEventId)` 消费，重组不会重复触发。
     * @param tabReselect 最近一次重选的处置（页号 + 回顶/刷新），由 [tabReselectActionFor] 决定。
     */
    tabReselectEventId: Int = 0,
    tabReselect: TabReselectSignal = TabReselectSignal.None,
    modifier: Modifier = Modifier,
    contentTopInset: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    val navigationContext = LocalContext.current
    val posterNavigationScope = rememberCoroutineScope()
    val openSubject: (Long) -> Unit = { id ->
        posterNavigationScope.launch {
            val subject = withContext(Dispatchers.IO) {
                navigationContext.nirikoApp.subjectRepository.getById(id)
                    ?: com.otakup.niriko.util.SubjectNavigationSeed.peekSubject(id)
            }
            com.otakup.niriko.util.SubjectNavigationSeed.prepare(subject)
            navController.navigate("subject_detail/$id")
        }
    }

    // Pager 手势锁：搜索交互按下/拖拽期间禁用翻页，根治左划冲突
    val searchGestureLock = LocalSearchGestureLock.current
    // 底栏折射源 = 壁纸层（MainActivity 传入）+ 本页内容层。内容层由下面的 Box 捕获，
    // 底栏本身在捕获层之外 —— 因此不会出现「捕获层包含自己」的 RenderNode 自引用环。
    val pageBackdrop = rememberLayerBackdrop { drawContent() }
    val barBackdrop = rememberCombinedBackdrop(backdrop, pageBackdrop)

    // ===== B8：状态提升（最小集）=====
    // 两个页面的 ViewModel 提到 pager 顶层（owner 仍是 MAIN backStackEntry，实例与改造前相同），
    // 让「页面内容」与「pager 外层的悬浮顶栏」共用同一份数据。
    val app = navigationContext.nirikoApp
    val libraryViewModel = viewModel<CollectionViewModel>(
        factory = CollectionViewModelFactory(
            collectionRepository = app.collectionRepository,
            steamRepository = app.steamRepository,
            libraryFolderDao = app.database.libraryFolderDao(),
        ),
    )
    val discoverViewModel = viewModel<SubjectSearchViewModel>(
        factory = SubjectSearchViewModelFactory(
            subjectRepository = app.subjectRepository,
            collectionRepository = app.collectionRepository,
            searchHistoryDao = app.searchHistoryDao,
            steamRepository = app.steamRepository,
            subjectDao = app.subjectDao,
            settingsDataStore = app.settingsDataStore,
            refreshCoordinator = app.refreshCoordinator,
            seasonalTrendingRepository = app.seasonalTrendingRepository,
        ),
    )
    val libraryControls = rememberSaveable(saver = LibraryControlsState.Saver) { LibraryControlsState() }
    val librarySearchVm = rememberSaveable(saver = SearchViewModel.Saver) { SearchViewModel() }
    val discoverSearchVm = rememberSaveable(saver = SearchViewModel.Saver) { SearchViewModel() }
    // 切页：收起离开页的搜索卡 + 收键盘，避免「在发现页残留作品库展开卡」
    val chromeFocusManager = LocalFocusManager.current
    val chromeKeyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(pagerState.settledPage) {
        librarySearchVm.collapse()
        discoverSearchVm.collapse()
        chromeFocusManager.clearFocus()
        chromeKeyboard?.hide()
    }

    // ===== B11-Step2/3：所有悬浮控件共用一个隐藏进度与一个空白点击恢复入口 =====
    // task-25：真机反馈「底栏藏了顶栏没走」。顶栏不再依赖窗口层的 fraction（跨 CompositionLocal
    // 读取在重组时序上不稳），改为直接由**同一个** FloatingControlsState.shown 驱动本地位移动画；
    // 源仍是同一份状态，只多一个 220ms 的呈现动画（与 MainActivity 的同参数，视觉同步）。
    val floatingControls = LocalFloatingControls.current
    val topBarHideProgress by animateFloatAsState(
        targetValue = if (floatingControls?.shown != false) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "pagerTopBarHide",
    )
    val searchExpanded = librarySearchVm.phase != SearchPhase.COLLAPSED ||
        discoverSearchVm.phase != SearchPhase.COLLAPSED
    // 搜索表面展开期间强制显示并暂停自动隐藏（否则 200dp 卡会被移出屏幕）
    LaunchedEffect(searchExpanded) { floatingControls?.forceShown(searchExpanded) }

    Box(modifier = modifier.fillMaxSize()) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Step3：点空白恢复（Final-pass 观察，不消费事件、无全屏透明层）
            .floatingBarRestoreOnBlankTap { floatingControls?.restore() }
            .layerBackdrop(pageBackdrop),
    ) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !searchGestureLock.value,
        // 预组合相邻页（=1）：滑动时目标页已组合好，避免滑动中实时全量组合（更卡）。
        // 预组合成本已通过 Stats 图表静态化 + crossfade 移除 + Paint 复用降低。
        beyondViewportPageCount = 1,
    ) { page ->
        val destination = TopLevelDestination.entries[page]
        val context = LocalContext.current
        val app = context.nirikoApp
        // 共享元素仅当前可见页生效：非当前页 scope=null → 卡片不加 sharedElement，
        // 避免 Pager 页间同 key 冲突（闪烁/overlay 残留）
        val pageSharedScope = if (page == pagerState.currentPage) sharedTransitionScope else null
        when (destination) {
            TopLevelDestination.Library -> {
                LibraryScreen(
                    windowTopInset = contentTopInset,
                    viewModel = libraryViewModel,
                    controls = libraryControls,
                    searchVm = librarySearchVm,
                    ambientActive = pagerState.settledPage == page && !pagerState.isScrollInProgress,
                    onNavigateToSearch = { navController.navigate("subject_search") },
                    onSubjectClick = openSubject,
                    onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                    sharedTransitionScope = pageSharedScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    // F11：作品库在顶部重选 = 无操作，不在顶部则回顶
                    tabReselectEventId = tabReselectEventId,
                    tabReselect = tabReselect,
                )
            }
            TopLevelDestination.Discover -> {
                SubjectSearchScreen(
                    windowTopInset = contentTopInset,
                    viewModel = discoverViewModel,
                    hoistedSearchVm = discoverSearchVm,
                    showOwnTopBar = false,
                    onSubjectClick = openSubject,
                    onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                    // 第 6 轮 §5/§6：DiscoverViewModel（找条目 + 评分月刊）已整体删除
                    sharedTransitionScope = pageSharedScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    // F11：发现页在顶部重选 = 刷新（single-flight 由 ViewModel/协调器保证）
                    tabReselectEventId = tabReselectEventId,
                    tabReselect = tabReselect,
                )
            }
            TopLevelDestination.Stats -> {
                val statsViewModel = viewModel<com.otakup.niriko.viewmodel.StatsViewModel>(
                    factory = com.otakup.niriko.viewmodel.StatsViewModelFactory(
                        collectionRepository = app.collectionRepository,
                        broadcastFetcher = app.broadcastFetcher,
                        seasonalFetcher = app.seasonalFetcher,
                        episodeRepository = app.episodeRepository,
                        refreshCoordinator = app.refreshCoordinator,
                    ),
                )
                StatsScreen(
                    modifier = Modifier.padding(top = contentTopInset),
                    viewModel = statsViewModel,
                    onSubjectClick = openSubject,
                    tabReselectEventId = tabReselectEventId,
                    tabReselect = tabReselect,
                    onNavigateToDiscover = {
                        scope.launch { pagerState.animateScrollToPage(TopLevelDestination.Discover.ordinal) }
                    },
                    // 共享元素隔离：仅当前可见页生效（与 Library/Discover 一致，防跨页同 key 闪烁）
                    sharedTransitionScope = pageSharedScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            TopLevelDestination.Settings -> {
                // 设置主页 = 分类导航；各分类页的 ViewModel 由 NirikoNavHost 二级路由自建
                SettingsScreen(
                    modifier = Modifier.padding(top = contentTopInset),
                    onNavigateToCategory = { route -> navController.navigate(route) },
                )
            }
        }
    }
    }
    // ===== B8：两页悬浮顶栏挂在 pager 外层（与底栏同级，不随页横向平移）=====
    // 玻璃源沿用底栏的 barBackdrop（壁纸 + 本页内容层），页面内部的 provider 保持不动。
    val topBarSlot = topBarSlotFor(pagerState.settledPage)
    if (topBarSlot != null) {
        CompositionLocalProvider(LocalSearchGlassBackdrop provides barBackdrop) {
            PagerTopBar(
                slot = topBarSlot,
                windowTopInset = contentTopInset,
                hideProgress = topBarHideProgress,
                modifier = Modifier.align(Alignment.TopStart),
                libraryContent = {
                    LibraryTopBar(
                        viewModel = libraryViewModel,
                        controls = libraryControls,
                        searchVm = librarySearchVm,
                    )
                },
                discoverContent = {
                    DiscoverTopBar(
                        viewModel = discoverViewModel,
                        searchVm = discoverSearchVm,
                    )
                },
            )
        }
    }
    // 手机 / 中等宽度：横向胶囊底栏挂在 MAIN 路由内容里 —— 预测性返回预览父页时，
    // 底栏会随父页一起被画出来（挂在窗口层则预览里没有它）。
    // 宽屏竖向 dock 仍在窗口层（见 MainActivity），二级路由也保留。
    if (windowLayout != NirikoWindowLayout.EXPANDED) {
        NirikoNavSuite(
            layout = windowLayout,
            pagerState = pagerState,
            backdrop = barBackdrop,
            onDestinationSelected = onDestinationSelected,
            onTabReselected = onTabReselected,
        )
    }
    }
}
