package com.otakup.niriko.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.screens.LibraryScreen
import com.otakup.niriko.ui.screens.StatsScreen
import com.otakup.niriko.ui.settings.SettingsScreen
import com.otakup.niriko.ui.subject.SubjectSearchScreen
import com.otakup.niriko.viewmodel.BackupViewModel
import com.otakup.niriko.viewmodel.BackupViewModelFactory
import com.otakup.niriko.viewmodel.CollectionViewModel
import com.otakup.niriko.viewmodel.CollectionViewModelFactory
import com.otakup.niriko.viewmodel.SettingsViewModel
import com.otakup.niriko.viewmodel.SettingsViewModelFactory
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
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Pager 手势锁：搜索交互按下/拖拽期间禁用翻页，根治左划冲突
    val searchGestureLock = LocalSearchGestureLock.current
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
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
                val libraryViewModel = viewModel<CollectionViewModel>(
                    factory = CollectionViewModelFactory(
                        collectionRepository = app.collectionRepository,
                        steamRepository = app.steamRepository,
                    ),
                )
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onNavigateToSearch = { navController.navigate("subject_search") },
                    onSubjectClick = { subjectId -> navController.navigate("subject_detail/$subjectId") },
                    onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                    sharedTransitionScope = pageSharedScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            TopLevelDestination.Discover -> {
                val searchViewModel = viewModel<SubjectSearchViewModel>(
                    factory = SubjectSearchViewModelFactory(
                        subjectRepository = app.subjectRepository,
                        collectionRepository = app.collectionRepository,
                        searchHistoryDao = app.searchHistoryDao,
                        steamRepository = app.steamRepository,
                    ),
                )
                SubjectSearchScreen(
                    viewModel = searchViewModel,
                    onSubjectClick = { subjectId -> navController.navigate("subject_detail/$subjectId") },
                    onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                    sharedTransitionScope = pageSharedScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            TopLevelDestination.Stats -> {
                val statsViewModel = viewModel<com.otakup.niriko.viewmodel.StatsViewModel>(
                    factory = com.otakup.niriko.viewmodel.StatsViewModelFactory(
                        collectionRepository = app.collectionRepository,
                        broadcastFetcher = app.broadcastFetcher,
                        seasonalFetcher = app.seasonalFetcher,
                    ),
                )
                StatsScreen(
                    viewModel = statsViewModel,
                    onSubjectClick = { subjectId -> navController.navigate("subject_detail/$subjectId") },
                    onNavigateToDiscover = {
                        scope.launch { pagerState.animateScrollToPage(TopLevelDestination.Discover.ordinal) }
                    },
                    // 共享元素隔离：仅当前可见页生效（与 Library/Discover 一致，防跨页同 key 闪烁）
                    sharedTransitionScope = pageSharedScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            TopLevelDestination.Settings -> {
                val settingsViewModel = viewModel<SettingsViewModel>(
                    factory = SettingsViewModelFactory(app.settingsDataStore, app.pluginManager, app.syncManager, app.bangumiSyncManager),
                )
                val backupViewModel = viewModel<BackupViewModel>(
                    factory = BackupViewModelFactory(app.backupManager, app.settingsDataStore),
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    backupViewModel = backupViewModel,
                    onNavigateToBilibiliSync = { navController.navigate("bilibili_sync") },
                )
            }
        }
    }
}
