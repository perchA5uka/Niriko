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
import com.otakup.niriko.ui.animation.exitToRight
import com.otakup.niriko.ui.character.CharacterDetailScreen
import com.otakup.niriko.ui.person.PersonDetailScreen
import com.otakup.niriko.viewmodel.CharacterDetailViewModel
import com.otakup.niriko.viewmodel.CharacterDetailViewModelFactory
import com.otakup.niriko.viewmodel.PersonDetailViewModel
import com.otakup.niriko.viewmodel.PersonDetailViewModelFactory
import com.otakup.niriko.ui.subject.SubjectDetailScreen
import com.otakup.niriko.ui.subject.StaffListScreen
import com.otakup.niriko.ui.subject.SubjectSearchScreen
import com.otakup.niriko.ui.bilibili.BilibiliSyncScreen
import com.otakup.niriko.ui.bilibili.BilibiliSyncViewModelFactory
import com.otakup.niriko.viewmodel.StaffListViewModel
import com.otakup.niriko.viewmodel.StaffListViewModelFactory
import com.otakup.niriko.viewmodel.SubjectDetailViewModel
import com.otakup.niriko.viewmodel.SubjectDetailViewModelFactory
import com.otakup.niriko.viewmodel.SubjectSearchViewModel
import com.otakup.niriko.viewmodel.SubjectSearchViewModelFactory

/** 顶层页面路由：HorizontalPager 承载（见 [MainPager]）。 */
const val MAIN_ROUTE = "main"

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
            enterTransition = { enterFromRight() },
            exitTransition = { exitToRight() },
        ) {
            val context = LocalContext.current
            val app = context.nirikoApp
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
            )
        }
        composable(
            "bilibili_sync",
            enterTransition = { enterFromRight() },
            exitTransition = { exitToRight() },
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
            "subject_detail/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.LongType }),
            enterTransition = { enterFromRight() },
            exitTransition = { exitToRight() },
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
                ),
            )
            SubjectDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCharacterClick = { characterId -> navController.navigate("character_detail/$characterId") },
                onPersonClick = { personId -> navController.navigate("person_detail/$personId") },
                onRelationClick = { subjectId -> navController.navigate("subject_detail/$subjectId") },
                onViewAllStaffClick = { navController.navigate("staff_list/$subjectId") },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this@composable,
            )
        }
        composable(
            "staff_list/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.LongType }),
            enterTransition = { enterFromRight() },
            exitTransition = { exitToRight() },
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
            "character_detail/{characterId}",
            arguments = listOf(navArgument("characterId") { type = NavType.LongType }),
            enterTransition = { enterFromRight() },
            exitTransition = { exitToRight() },
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
            )
        }
        composable(
            "person_detail/{personId}",
            arguments = listOf(navArgument("personId") { type = NavType.LongType }),
            enterTransition = { enterFromRight() },
            exitTransition = { exitToRight() },
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
            )
        }
    }
}
