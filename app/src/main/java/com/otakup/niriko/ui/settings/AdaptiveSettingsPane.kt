package com.otakup.niriko.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.animation.AnimDurationShort
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.animation.motionEnabled
import com.otakup.niriko.ui.common.reportBottomBarScroll
import com.otakup.niriko.ui.settings.pages.AboutSettingsContent
import com.otakup.niriko.ui.settings.pages.AppearanceSettingsContent
import com.otakup.niriko.ui.settings.pages.DataSourceSettingsContent
import com.otakup.niriko.ui.settings.pages.LibrarySettingsContent
import com.otakup.niriko.ui.settings.pages.RefreshDiagnosticsContent
import com.otakup.niriko.ui.settings.pages.SearchSettingsContent
import com.otakup.niriko.ui.settings.pages.SyncBackupSettingsContent
import com.otakup.niriko.viewmodel.BackupViewModel
import com.otakup.niriko.viewmodel.BackupViewModelFactory
import com.otakup.niriko.viewmodel.SettingsViewModel
import com.otakup.niriko.viewmodel.SettingsViewModelFactory

/**
 * B2c：宽屏（EXPANDED）设置页的两栏外壳。
 *
 * 左列 = 分类导航（标题「设置」+ 三个分组 + 七个分类，选中态高亮）；
 * 右列 = 真实二级页正文（复用 ui/settings/pages 里抽出的 XxxSettingsContent，
 * 不含二级页自己的脚手架／返回箭头，因为宽屏不需要「返回上一页」）。
 *
 * 窄屏（COMPACT / MEDIUM）完全不进这里，见 SettingsScreen 的分派。
 */
@Composable
fun SettingsExpandedScreen(
    modifier: Modifier = Modifier,
    onNavigateToCategory: (String) -> Unit = {},
) {
    // 选中项可跨配置变更存活；存 route 字符串而不是枚举，避免旧值无法解析时崩溃
    var selectedRoute by rememberSaveable { mutableStateOf(SettingsCategory.default.route) }
    val selected = SettingsCategory.fromRoute(selectedRoute)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        SettingsCategoryNavColumn(
            selected = selected,
            onSelect = { category -> selectedRoute = category.route },
            modifier = Modifier
                .width(SettingsNavWidth)
                .fillMaxHeight(),
        )
        Spacer(Modifier.width(16.dp))
        SettingsCategoryDetailColumn(
            category = selected,
            onNavigateToCategory = onNavigateToCategory,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

/** 左列宽度（B2c 要求 280~320dp，取中值）。 */
private val SettingsNavWidth = 300.dp

/**
 * 宽屏内嵌「数据源与账号」页里两个二级页跳转目标的路由字面量。
 * 与 NirikoNavHost 里 composable("bilibili_sync") / composable("steam_sync") 一致。
 * B2c 期间导航宿主由 B2b 并行编辑，本批次不动它，只在 UI 侧引用同样的字面量。
 */
private const val BILIBILI_SYNC_ROUTE = "bilibili_sync"
private const val STEAM_SYNC_ROUTE = "steam_sync"

/**
 * 左列：分类导航。结构刻意与窄屏 SettingsScreen 的单列列表同构
 * （标题 + 分组标题 + 分组卡），只是宽度固定且带上选中态。
 */
@Composable
private fun SettingsCategoryNavColumn(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
        )
        Spacer(Modifier.height(4.dp))

        SettingsCategory.groups().forEach { (group, categories) ->
            SettingsCategoryNavGroup(
                group = group,
                categories = categories,
                selected = selected,
                onSelect = onSelect,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsCategoryNavGroup(
    group: SettingsCategoryGroup,
    categories: List<SettingsCategory>,
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
) {
    SettingsGroupTitle(group.title, description = group.description)
    SettingsSplitGroup(
        content = categories.map { category ->
            {
                SettingsNavRow(
                    category = category,
                    selected = category == selected,
                    onClick = { onSelect(category) },
                )
            }
        },
    )
}

/**
 * 左列单个分类行：沿用窄屏 SettingsCategoryRow 的视觉（36dp 圆形图标底 + 标题 + 描述），
 * 只把尾部的 ChevronRight 换成选中态（背景色 + 标题加粗）。
 */
@Composable
private fun SettingsNavRow(
    category: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                else Color.Transparent,
            )
            .selectable(
                selected = selected,
                interactionSource = rememberRowInteractionSource(),
                indication = ripple(),
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 分类图标：与窄屏 SettingsScreen 七行使用的图标逐一对应。 */
private fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.APPEARANCE -> Icons.Outlined.Palette
    SettingsCategory.LIBRARY -> Icons.Outlined.CollectionsBookmark
    SettingsCategory.SEARCH -> Icons.Outlined.Search
    SettingsCategory.DATASOURCE -> Icons.Outlined.Storage
    SettingsCategory.SYNC -> Icons.Outlined.Sync
    SettingsCategory.REFRESH -> Icons.Outlined.Refresh
    SettingsCategory.ABOUT -> Icons.Outlined.Info
}

/**
 * 右列：二级页正文。切换分类用一个短 crossfade；reduceMotion 时直接换内容不播动画。
 */
@Composable
private fun SettingsCategoryDetailColumn(
    category: SettingsCategory,
    onNavigateToCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = motionEnabled()
    if (motion) {
        Crossfade(
            targetState = category,
            modifier = modifier,
            animationSpec = tween(durationMillis = AnimDurationShort, easing = AnimEasingDefault),
            label = "settingsCategoryPane",
        ) { target ->
            SettingsCategoryDetailContent(
                category = target,
                onNavigateToCategory = onNavigateToCategory,
            )
        }
    } else {
        Box(modifier = modifier) {
            SettingsCategoryDetailContent(
                category = category,
                onNavigateToCategory = onNavigateToCategory,
            )
        }
    }
}

/**
 * 右列正文：分类标题 + 二级页正文 + 该页自己的 Snackbar。
 * 滚动状态放在 crossfade 内部，所以切分类会各自回到顶部；
 * reportBottomBarScroll 让底部导航（或 B2b 的竖排 dock）跟着右列滚动收起。
 */
@Composable
private fun SettingsCategoryDetailContent(
    category: SettingsCategory,
    onNavigateToCategory: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .reportBottomBarScroll()
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            )
            SettingsCategoryDetailBody(
                category = category,
                snackbarHostState = snackbarHostState,
                onNavigateToCategory = onNavigateToCategory,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 分类 → 二级页正文（全部来自 ui/settings/pages 的 XxxSettingsContent）。 */
@Composable
private fun SettingsCategoryDetailBody(
    category: SettingsCategory,
    snackbarHostState: SnackbarHostState,
    onNavigateToCategory: (String) -> Unit,
) {
    when (category) {
        SettingsCategory.APPEARANCE -> AppearanceSettingsContent(
            viewModel = settingsViewModel(),
            snackbarHostState = snackbarHostState,
        )

        SettingsCategory.LIBRARY -> LibrarySettingsContent(
            viewModel = settingsViewModel(),
        )

        SettingsCategory.SEARCH -> SearchSettingsContent(
            viewModel = settingsViewModel(),
        )

        SettingsCategory.DATASOURCE -> DataSourceSettingsContent(
            viewModel = settingsViewModel(),
            snackbarHostState = snackbarHostState,
            onNavigateToBilibiliSync = { onNavigateToCategory(BILIBILI_SYNC_ROUTE) },
            onNavigateToSteamSync = { onNavigateToCategory(STEAM_SYNC_ROUTE) },
        )

        SettingsCategory.SYNC -> SyncBackupSettingsContent(
            viewModel = settingsViewModel(),
            backupViewModel = backupViewModel(),
            snackbarHostState = snackbarHostState,
        )

        SettingsCategory.REFRESH -> RefreshDiagnosticsContent(
            viewModel = settingsViewModel(),
        )

        SettingsCategory.ABOUT -> AboutSettingsContent()
    }
}

/**
 * 宽屏下二级页的 ViewModel 直接取当前 NavBackStackEntry 的 store，
 * 工厂参数与 NirikoNavHost 里七个二级页的写法完全一致（同一套单例依赖）。
 */
@Composable
private fun settingsViewModel(): SettingsViewModel {
    val app = LocalContext.current.nirikoApp
    return viewModel<SettingsViewModel>(
        factory = SettingsViewModelFactory(
            dataStore = app.settingsDataStore,
            pluginManager = app.pluginManager,
            syncManager = app.syncManager,
            bangumiSyncManager = app.bangumiSyncManager,
            refreshCoordinator = app.refreshCoordinator,
        ),
    )
}

@Composable
private fun backupViewModel(): BackupViewModel {
    val app = LocalContext.current.nirikoApp
    return viewModel<BackupViewModel>(
        factory = BackupViewModelFactory(app.backupManager, app.settingsDataStore),
    )
}
