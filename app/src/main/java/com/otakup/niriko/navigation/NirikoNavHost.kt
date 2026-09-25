package com.otakup.niriko.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.animation.enterFromRight
import com.otakup.niriko.ui.animation.enterSharedAxisZ
import com.otakup.niriko.ui.animation.exitSharedAxisZ
import com.otakup.niriko.ui.animation.exitToRight
import com.otakup.niriko.ui.character.CharacterDetailScreen
import com.otakup.niriko.ui.episode.EpisodeDetailScreen
import com.otakup.niriko.ui.person.PersonDetailScreen
import com.otakup.niriko.ui.settings.pages.AboutSettingsScreen
import com.otakup.niriko.ui.settings.pages.AppearanceSettingsScreen
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

// ===== 设置二级页路由（分类导航，见 ui/settings/SettingsScreen.kt 主页） =====
const val SETTINGS_APPEARANCE_ROUTE = "settings_appearance"
const val SETTINGS_LIBRARY_ROUTE = "settings_library"
const val SETTINGS_SEARCH_ROUTE = "settings_search"
const val SETTINGS_DATASOURCE_ROUTE = "settings_datasource"
const val SETTINGS_SYNC_ROUTE = "settings_sync"
const val SETTINGS_ABOUT_ROUTE = "settings_about"
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
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = MAIN_ROUTE,
        modifier = modifier,
    ) {
        composable(MAIN_ROUTE) {
            MainPager(
                pagerState = pagerState,
                navController = navController,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
                modifier = Modifier,
            )
        }
        composable(
            "subject_search",
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
                onSubjectClick = { subjectId -> navController.navigate("subject_detail/$subjectId") },
                onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
            )
        }
        composable(
            "bilibili_sync",
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
            BilibiliSyncScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "steam_sync",
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
            SteamSyncScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "subject_detail/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.LongType }),
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
                ),
            )
            SubjectDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCharacterClick = { characterId -> navController.navigate("character_detail/$characterId") },
                onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                onRelationClick = { subjectId -> navController.navigate("subject_detail/$subjectId") },
                onViewAllStaffClick = { navController.navigate("staff_list/$subjectId") },
                onOpenEpisodeDetail = { epId ->
                    navController.navigate("episode_detail/$subjectId/$epId")
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
            )
        }
        composable(
            "staff_list/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.LongType }),
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
            StaffListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
            )
        }
        composable(
            "episode_detail/{subjectId}/{epId}",
            arguments = listOf(
                navArgument("subjectId") { type = NavType.LongType },
                navArgument("epId") { type = NavType.LongType },
            ),
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
            EpisodeDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            "character_detail/{characterId}",
            arguments = listOf(navArgument("characterId") { type = NavType.LongType }),
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
            CharacterDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSubjectClick = { subjectId -> navController.navigate("subject_detail/$subjectId") },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
            )
        }
        composable(
            "person_detail/{personId}",
            arguments = listOf(navArgument("personId") { type = NavType.LongType }),
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
            PersonDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSubjectClick = { subjectId -> navController.navigate("subject_detail/$subjectId") },
                onCharacterClick = { characterId -> navController.navigate("character_detail/$characterId") },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
            )
        }

        // ===== 设置二级页（分类导航，Kazumi 模式） =====
        composable(
            SETTINGS_APPEARANCE_ROUTE,
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            AppearanceSettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            SETTINGS_LIBRARY_ROUTE,
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            LibrarySettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            SETTINGS_SEARCH_ROUTE,
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            SearchSettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            SETTINGS_DATASOURCE_ROUTE,
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            DataSourceSettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onNavigateToBilibiliSync = { navController.navigate("bilibili_sync") },
                onNavigateToSteamSync = { navController.navigate("steam_sync") },
            )
        }
        composable(
            SETTINGS_SYNC_ROUTE,
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
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
            SyncBackupSettingsScreen(
                viewModel = vm,
                backupViewModel = backupVm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            SETTINGS_REFRESH_ROUTE,
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
            val vm = viewModel<SettingsViewModel>(
                factory = SettingsViewModelFactory(
                        app.settingsDataStore, app.pluginManager, app.syncManager,
                        app.bangumiSyncManager, app.refreshCoordinator,
                    ),
            )
            com.otakup.niriko.ui.settings.pages.RefreshDiagnosticsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            SETTINGS_ABOUT_ROUTE,
            enterTransition = { enterSharedAxisZ() },
            exitTransition = { exitSharedAxisZ() },
        ) {
            AboutSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
