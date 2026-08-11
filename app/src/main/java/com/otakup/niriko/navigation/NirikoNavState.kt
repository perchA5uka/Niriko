package com.otakup.niriko.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** 封装 NavController，供底部栏与 NavHost 共用。 */
@Stable
class NirikoNavState(
    val navController: NavHostController,
) {
    val currentDestination: NavDestination?
        @Composable
        get() {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            return navBackStackEntry?.destination
        }

    /** 切换顶级 Tab：单例栈顶，避免重复压栈。 */
    fun navigateToTopLevel(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@Composable
fun rememberNirikoNavState(
    navController: NavHostController = rememberNavController(),
): NirikoNavState = remember(navController) {
    NirikoNavState(navController)
}
