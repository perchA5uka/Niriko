package com.otakup.niriko.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.animation.NavEntryReturnedEffect
import com.otakup.niriko.ui.animation.SubjectReturnGlow
import com.kyant.backdrop.Backdrop
import com.otakup.niriko.ui.adaptive.NirikoWindowLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.animation.enterSharedAxisZ
import com.otakup.niriko.ui.animation.exitSharedAxisZ
import com.otakup.niriko.ui.animation.popEnterSharedAxisZ
import com.otakup.niriko.ui.animation.popExitSharedAxisZ
import com.otakup.niriko.ui.animation.LocalReduceMotion
import com.otakup.niriko.ui.animation.NirikoMotionSpecs
import com.otakup.niriko.ui.character.CharacterDetailScreen
import com.otakup.niriko.ui.episode.EpisodeDetailScreen
import com.otakup.niriko.ui.person.PersonDetailScreen
import com.otakup.niriko.ui.settings.pages.AboutSettingsScreen
import com.otakup.niriko.ui.settings.pages.DonateSettingsScreen
import com.otakup.niriko.ui.settings.pages.AppearanceSettingsScreen
import com.otakup.niriko.ui.settings.pages.DetailSectionsSettingsScreen
import com.otakup.niriko.ui.settings.pages.WallpaperLibraryScreen
import com.otakup.niriko.ui.settings.pages.DataSourceSettingsScreen
import com.otakup.niriko.ui.settings.pages.LibrarySettingsScreen
import com.otakup.niriko.ui.settings.pages.SearchSettingsScreen
import com.otakup.niriko.ui.settings.pages.SyncBackupSettingsScreen
import com.otakup.niriko.viewmodel.BackupViewModel
import com.otakup.niriko.viewmodel.BackupViewModelFactory
import com.otakup.niriko.viewmodel.SettingsViewModel
import com.otakup.niriko.viewmodel.SettingsViewModelFactory
import com.otakup.niriko.viewmodel.CharacterDetailViewModel
import com.otakup.niriko.viewmodel.CharacterDetailViewModelFactory
import com.otakup.niriko.viewmodel.PersonDetailViewModel
import com.otakup.niriko.viewmodel.EpisodeDetailViewModel
import com.otakup.niriko.viewmodel.EpisodeDetailViewModelFactory
import com.otakup.niriko.viewmodel.PersonDetailViewModelFactory
import com.otakup.niriko.ui.subject.SubjectDetailScreen
import com.otakup.niriko.ui.subject.StaffListScreen
import com.otakup.niriko.ui.subject.SubjectSearchScreen
import com.otakup.niriko.ui.bilibili.BilibiliSyncScreen
import com.otakup.niriko.ui.bilibili.BilibiliSyncViewModelFactory
import com.otakup.niriko.ui.steam.SteamSyncScreen
import com.otakup.niriko.ui.steam.SteamSyncViewModelFactory
import com.otakup.niriko.viewmodel.StaffListViewModel
import com.otakup.niriko.viewmodel.StaffListViewModelFactory
import com.otakup.niriko.viewmodel.SubjectDetailViewModel
import com.otakup.niriko.viewmodel.SubjectDetailViewModelFactory
import com.otakup.niriko.viewmodel.SubjectSearchViewModel
import com.otakup.niriko.viewmodel.SubjectSearchViewModelFactory

/** 顶层页面路由：HorizontalPager 承载（见 [MainPager]）。 */
const val MAIN_ROUTE = "main"

private val subjectCoverSourceRoutes = setOf(
    MAIN_ROUTE, "subject_search", "subject_detail/{subjectId}",
    "character_detail/{characterId}", "person_detail/{personId}",
)

internal fun usesSubjectCoverTransition(initialRoute: String?, targetRoute: String?): Boolean {
    fun normalize(route: String?): String? = when {
        route?.startsWith("subject_detail/") == true -> "subject_detail/{subjectId}"
        route?.startsWith("character_detail/") == true -> "character_detail/{characterId}"
        route?.startsWith("person_detail/") == true -> "person_detail/{personId}"
        else -> route
    }
    val source = normalize(initialRoute)
    val target = normalize(targetRoute)
    return (source == "subject_detail/{subjectId}" && target in subjectCoverSourceRoutes) ||
        (target == "subject_detail/{subjectId}" && source in subjectCoverSourceRoutes) ||
        (source in subjectCoverSourceRoutes && target in subjectCoverSourceRoutes &&
            (source?.startsWith("character_detail/") == true || source?.startsWith("person_detail/") == true ||
                target?.startsWith("character_detail/") == true || target?.startsWith("person_detail/") == true))
}

// ===== 设置二级页路由（分类导航，见 ui/settings/SettingsScreen.kt 主页） =====
const val SETTINGS_APPEARANCE_ROUTE = "settings_appearance"
/** 外观 → 壁纸库（R3）。 */
const val SETTINGS_WALLPAPER_LIBRARY_ROUTE = "settings_wallpaper_library"
/** 外观 → 详情部件（F06）。 */
const val SETTINGS_DETAIL_SECTIONS_ROUTE = "settings_detail_sections"
const val SETTINGS_LIBRARY_ROUTE = "settings_library"
const val SETTINGS_SEARCH_ROUTE = "settings_search"
const val SETTINGS_DATASOURCE_ROUTE = "settings_datasource"
const val SETTINGS_SYNC_ROUTE = "settings_sync"
const val SETTINGS_ABOUT_ROUTE = "settings_about"
/** 设置 → 其他 → 向开发者捐赠（F19）。 */
const val SETTINGS_DONATE_ROUTE = "settings_donate"
const val SETTINGS_REFRESH_ROUTE = "settings_refresh"

/**
 * 导航宿主。顶层四页由 [MainPager]（HorizontalPager）承载，二级页面（详情/搜索等）走 NavHost 路由覆盖其上。
 * [pagerState] 由 MainActivity 创建并共享给 MainPager 与 NirikoBottomBar（指示器跟手联动）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NirikoNavHost(
    navController: NavHostController,
    pagerState: PagerState,
    sharedTransitionScope: SharedTransitionScope,
    backdrop: Backdrop,
    windowLayout: NirikoWindowLayout,
    onDestinationSelected: (Int) -> Unit = {},
    onTabReselected: (Int) -> Unit = {},
    /**
     * Window safe-top inset. NavHost remains full-window; each route owns its content inset.
     * Discover/detail scroll from window y=0 and inset only their floating controls or initial items.
     * Other routes retain their previous safe-top viewport without route-dependent parent shifts.
     */
    contentTopInset: Dp = 0.dp,
    /** 底栏「重选当前 tab」事件号与处置（F11），透传给顶层各页。 */
    tabReselectEventId: Int = 0,
    tabReselect: TabReselectSignal = TabReselectSignal.None,
    modifier: Modifier = Modifier,
) {
    val navigationContext = LocalContext.current
    val posterNavigationScope = rememberCoroutineScope()
    val openSubject: (Long) -> Unit = { id ->
        posterNavigationScope.launch {
            val subject = withContext(Dispatchers.IO) {
                navigationContext.nirikoApp.subjectRepository.getById(id)
                    ?: com.otakup.niriko.util.SubjectNavigationSeed.peekSubject(id)
            }
            com.otakup.niriko.util.SubjectNavigationSeed.prepare(subject)
            val heroKey = SubjectCoverHandoff.consume(id) ?: "cover_$id"
            // Hero identity must exist before destination first composition.
            navController.navigate("subject_detail/$id?heroKey=" + android.net.Uri.encode(heroKey))
        }
    }
    // P0-B：页面过渡 lambda 非 @Composable，specs 在此取出后用闭包捕获。
    val specs = NirikoMotionSpecs
    val reduceMotion = LocalReduceMotion.current
    NavHost(
        navController = navController,
        startDestination = MAIN_ROUTE,
        modifier = modifier,
        enterTransition = {
            if (reduceMotion) androidx.compose.animation.EnterTransition.None
            else if (usesSubjectCoverTransition(initialState.destination.route, targetState.destination.route))
                androidx.compose.animation.fadeIn(specs.subjectCoverSeek(contracting = targetState.destination.route?.startsWith("subject_detail/") != true))
            else enterSharedAxisZ(specs)
        },
        exitTransition = {
            if (reduceMotion) androidx.compose.animation.ExitTransition.None
            else if (usesSubjectCoverTransition(initialState.destination.route, targetState.destination.route))
                androidx.compose.animation.fadeOut(specs.subjectCoverSeek(contracting = targetState.destination.route?.startsWith("subject_detail/") != true))
            else exitSharedAxisZ(specs)
        },
        popEnterTransition = {
            if (reduceMotion) androidx.compose.animation.EnterTransition.None
            else if (usesSubjectCoverTransition(initialState.destination.route, targetState.destination.route))
                androidx.compose.animation.fadeIn(specs.subjectCoverSeek(contracting = targetState.destination.route?.startsWith("subject_detail/") != true))
            else popEnterSharedAxisZ(specs)
        },
        popExitTransition = {
            if (reduceMotion) androidx.compose.animation.ExitTransition.None
            else if (usesSubjectCoverTransition(initialState.destination.route, targetState.destination.route))
                androidx.compose.animation.fadeOut(specs.subjectCoverSeek(contracting = targetState.destination.route?.startsWith("subject_detail/") != true))
            else popExitSharedAxisZ(specs)
        },
    ) {
        composable(MAIN_ROUTE) {
            MainPager(
                pagerState = pagerState,
                navController = navController,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
                backdrop = backdrop,
                windowLayout = windowLayout,
                onDestinationSelected = onDestinationSelected,
                onTabReselected = onTabReselected,
                tabReselectEventId = tabReselectEventId,
                tabReselect = tabReselect,
                contentTopInset = contentTopInset,
                modifier = Modifier,
            )
        }
        composable(
            "subject_search",
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val searchViewModel = viewModel<SubjectSearchViewModel>(
                factory = SubjectSearchViewModelFactory(
                    subjectRepository = app.subjectRepository,
                    collectionRepository = app.collectionRepository,
                    searchHistoryDao = app.searchHistoryDao,
                    steamRepository = app.steamRepository,
                    // 第 6 轮 F8：subject_search 路由此前漏了 subjectDao ——
                    // 于是这条入口的本地建议/标签能力比发现页弱（同一功能两套能力）。
                    subjectDao = app.database.subjectDao(),
                    settingsDataStore = app.settingsDataStore,
                    refreshCoordinator = app.refreshCoordinator,
                    seasonalTrendingRepository = app.seasonalTrendingRepository,
                ),
            )
            SubjectSearchScreen(
                viewModel = searchViewModel,
                onSubjectClick = openSubject,
                onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                windowTopInset = contentTopInset,
            )
        }
        composable(
            "bilibili_sync",
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val viewModel = viewModel<com.otakup.niriko.ui.bilibili.BilibiliSyncViewModel>(
                factory = BilibiliSyncViewModelFactory(
                    context = context,
                    bilibiliSyncItemDao = app.bilibiliSyncItemDao,
                    subjectDao = app.database.subjectDao(),
                    collectionDao = app.database.collectionDao(),
                ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                BilibiliSyncScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            "steam_sync",
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val viewModel = viewModel<com.otakup.niriko.ui.steam.SteamSyncViewModel>(
                factory = SteamSyncViewModelFactory(
                    settingsDataStore = app.settingsDataStore,
                    steamRepository = app.steamRepository,
                    subjectRepository = app.subjectRepository,
                    database = app.database,
                ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                SteamSyncScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            "subject_detail/{subjectId}?heroKey={heroKey}",
            arguments = listOf(
                navArgument("subjectId") { type = NavType.LongType },
                navArgument("heroKey") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getLong("subjectId") ?: return@composable
            val context = LocalContext.current
            val app = context.nirikoApp
            val viewModel = viewModel<SubjectDetailViewModel>(
                factory = SubjectDetailViewModelFactory(
                    subjectRepository = app.subjectRepository,
                    collectionRepository = app.collectionRepository,
                    remoteDataSource = app.dataSourceChain,
                    subjectId = subjectId,
                    context = context,
                    steamRepository = app.steamRepository,
                    vndbRepository = app.vndbRepository,
                    anilistRepository = app.anilistRepository,
                    anitabiRepository = app.anitabiRepository,
                    externalRatingRepository = app.externalRatingRepository,
                    episodeRatingRepository = app.episodeRatingRepository,
                    tmdbRepository = app.tmdbRepository,
                    externalIdRepository = app.externalIdRepository,
                    manualAwardRepository = app.manualAwardRepository,
                    // B15：详情页集合结果落 Room —— 重启后再打开不必重拉角色/Staff/关联作品
                    detailCacheStore = app.detailCacheStore,
                ),
            )
            // F17：**完成返回**（工具栏返回 / 手势返回 / 返回键）时留一个一次性口令给列表卡片。
            // 判据是「这条导航记录真的被销毁」——向前导航不会销毁它，取消的预测性返回也不会。
            NavEntryReturnedEffect(backStackEntry) { SubjectReturnGlow.request(subjectId) }
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDetailNavigationState provides backStackEntry.savedStateHandle,
                LocalDetailHeroKey provides (backStackEntry.arguments?.getString("heroKey")
                    ?: backStackEntry.savedStateHandle.get<String>("detail_hero_key")),
                LocalDetailSubjectId provides subjectId,
            ) {
            SubjectDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCharacterClick = { characterId -> navController.navigate("character_detail/$characterId") },
                onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                onRelationClick = openSubject,
                onViewAllStaffClick = { navController.navigate("staff_list/$subjectId") },
                onOpenEpisodeDetail = { epId ->
                    navController.navigate("episode_detail/$subjectId/$epId")
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
                // Full-window viewport; floating controls alone avoid the status bar.
                modifier = Modifier,
            )
            }
        }
        composable(
            "staff_list/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getLong("subjectId") ?: return@composable
            val context = LocalContext.current
            val app = context.nirikoApp
            val viewModel = viewModel<StaffListViewModel>(
                factory = StaffListViewModelFactory(
                    remoteDataSource = app.dataSourceChain,
                    subjectId = subjectId,
                ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                StaffListScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                )
            }
        }
        composable(
            "episode_detail/{subjectId}/{epId}",
            arguments = listOf(
                navArgument("subjectId") { type = NavType.LongType },
                navArgument("epId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getLong("subjectId") ?: return@composable
            val epId = backStackEntry.arguments?.getLong("epId") ?: return@composable
            val context = LocalContext.current
            val app = context.nirikoApp
            val viewModel = viewModel<EpisodeDetailViewModel>(
                factory = EpisodeDetailViewModelFactory(
                    episodeDao = app.database.episodeDao(),
                    externalRatingDao = app.database.externalRatingDao(),
                    subjectDao = app.database.subjectDao(),
                    collectionDao = app.database.collectionDao(),
                    episodeRepository = app.episodeRepository,
                    subjectId = subjectId,
                    epId = epId,
                ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                EpisodeDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            "character_detail/{characterId}",
            arguments = listOf(navArgument("characterId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: return@composable
            val context = LocalContext.current
            val app = context.nirikoApp
            val viewModel = viewModel<CharacterDetailViewModel>(
                factory = CharacterDetailViewModelFactory(
                    remoteDataSource = app.dataSourceChain,
                    characterId = characterId,
                ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                CharacterDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSubjectClick = openSubject,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@composable,
                )
            }
        }
        composable(
            "person_detail/{personId}",
            arguments = listOf(navArgument("personId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val personId = backStackEntry.arguments?.getLong("personId") ?: return@composable
            val context = LocalContext.current
            val app = context.nirikoApp
            val viewModel = viewModel<PersonDetailViewModel>(
                factory = PersonDetailViewModelFactory(
                    remoteDataSource = app.dataSourceChain,
                    personCollectionDao = app.personCollectionDao,
                    personId = personId,
                ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                PersonDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onSubjectClick = openSubject,
                    onCharacterClick = { characterId -> navController.navigate("character_detail/$characterId") },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this@composable,
                )
            }
        }

        // ===== 设置二级页（分类导航，Kazumi 模式） =====
        composable(
            SETTINGS_APPEARANCE_ROUTE,
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                AppearanceSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpenWallpaperLibrary = { navController.navigate(SETTINGS_WALLPAPER_LIBRARY_ROUTE) },
                    onOpenDetailSections = { navController.navigate(SETTINGS_DETAIL_SECTIONS_ROUTE) },
                )
            }
        }
        composable(
            SETTINGS_WALLPAPER_LIBRARY_ROUTE,
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                WallpaperLibraryScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        // F06：详情部件（显示 / 隐藏）
        composable(
            SETTINGS_DETAIL_SECTIONS_ROUTE,
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                DetailSectionsSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            SETTINGS_LIBRARY_ROUTE,
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                LibrarySettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            SETTINGS_SEARCH_ROUTE,
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                SearchSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            SETTINGS_DATASOURCE_ROUTE,
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                DataSourceSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onNavigateToBilibiliSync = { navController.navigate("bilibili_sync") },
                    onNavigateToSteamSync = { navController.navigate("steam_sync") },
                )
            }
        }
        composable(
            SETTINGS_SYNC_ROUTE,
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            val backupVm = viewModel<BackupViewModel>(
                factory = BackupViewModelFactory(app.backupManager, app.settingsDataStore),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                SyncBackupSettingsScreen(
                    viewModel = vm,
                    backupViewModel = backupVm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            SETTINGS_REFRESH_ROUTE,
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                com.otakup.niriko.ui.settings.pages.RefreshDiagnosticsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            SETTINGS_ABOUT_ROUTE,
        ) {
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                AboutSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            SETTINGS_DONATE_ROUTE,
        ) {
            Box(Modifier.fillMaxSize().padding(top = contentTopInset)) {
                DonateSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
