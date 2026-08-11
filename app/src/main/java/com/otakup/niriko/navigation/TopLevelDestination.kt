package com.otakup.niriko.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.otakup.niriko.R

/**
 * 底部导航四个顶级目的地。
 * route 与 NavHost 中的 composable 路由保持一致。
 *
 * 用 enum class（而非 sealed class + data object）：enum 的 entries 由编译器
 * 生成稳定数组，不会出现 data object 单例初始化未就绪导致的 null。
 */
enum class TopLevelDestination(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Library(
        route = "library",
        titleRes = R.string.tab_library,
        selectedIcon = Icons.AutoMirrored.Filled.LibraryBooks,
        unselectedIcon = Icons.AutoMirrored.Outlined.LibraryBooks,
    ),
    Discover(
        route = "discover",
        titleRes = R.string.tab_discover,
        selectedIcon = Icons.Filled.Explore,
        unselectedIcon = Icons.Outlined.Explore,
    ),
    Stats(
        route = "stats",
        titleRes = R.string.tab_stats,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart,
    ),
    Settings(
        route = "settings",
        titleRes = R.string.tab_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    );
}
