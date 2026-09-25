package com.otakup.niriko.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.navigation.SETTINGS_APPEARANCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_LIBRARY_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SEARCH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_DATASOURCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SYNC_ROUTE
import com.otakup.niriko.navigation.SETTINGS_ABOUT_ROUTE
import com.otakup.niriko.navigation.SETTINGS_REFRESH_ROUTE
import com.otakup.niriko.ui.common.reportBottomBarScroll

/**
 * 设置分类主页（对齐 Kazumi _SettingsGroup 模式）：
 * 分组 + 类别卡片（图标 + 名称 + 一句话描述），点类别进独立二级页。
 * 二级页见 ui/settings/pages/，路由注册见 NirikoNavHost。
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToCategory: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .reportBottomBarScroll(),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
        )
        Spacer(Modifier.height(4.dp))

        SettingsGroupTitle("偏好", description = "外观、收藏展示与搜索")
        SettingsSplitGroup(content = listOf(
            {
                SettingsCategoryRow(
                    title = "外观",
                    subtitle = "主题配色、玻璃、壁纸与图标",
                    icon = Icons.Outlined.Palette,
                    onClick = { onNavigateToCategory(SETTINGS_APPEARANCE_ROUTE) },
                )
            },
            {
                SettingsCategoryRow(
                    title = "收藏与展示",
                    subtitle = "卡片展示、统计与启动页",
                    icon = Icons.Outlined.CollectionsBookmark,
                    onClick = { onNavigateToCategory(SETTINGS_LIBRARY_ROUTE) },
                )
            },
            {
                SettingsCategoryRow(
                    title = "搜索",
                    subtitle = "内容过滤与搜索建议",
                    icon = Icons.Outlined.Search,
                    onClick = { onNavigateToCategory(SETTINGS_SEARCH_ROUTE) },
                )
            },
        ))

        SettingsGroupTitle("数据", description = "数据源、账号、同步与备份")
        SettingsSplitGroup(content = listOf(
            {
                SettingsCategoryRow(
                    title = "数据源与账号",
                    subtitle = "Bangumi / Steam 与第三方导入",
                    icon = Icons.Outlined.Storage,
                    onClick = { onNavigateToCategory(SETTINGS_DATASOURCE_ROUTE) },
                )
            },
            {
                SettingsCategoryRow(
                    title = "同步与备份",
                    subtitle = "WebDAV 同步与 JSON 备份",
                    icon = Icons.Outlined.Sync,
                    onClick = { onNavigateToCategory(SETTINGS_SYNC_ROUTE) },
                )
            },
        ))

        SettingsGroupTitle("其他", description = "诊断与版本信息")
        SettingsSplitGroup(content = listOf(
            {
                SettingsCategoryRow(
                    title = "刷新诊断",
                    subtitle = "各数据源的新鲜度、退避与强制刷新",
                    icon = Icons.Outlined.Refresh,
                    onClick = { onNavigateToCategory(SETTINGS_REFRESH_ROUTE) },
                )
            },
            {
                SettingsCategoryRow(
                    title = "关于",
                    subtitle = "版本与开源许可",
                    icon = Icons.Outlined.Info,
                    onClick = { onNavigateToCategory(SETTINGS_ABOUT_ROUTE) },
                )
            },
        ))

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * 分类行（Kazumi SettingsCategoryTile 等价物）：
 * 圆形 secondaryContainer 图标底 36dp + 标题 bodyLarge + 描述 bodySmall + ChevronRight 图标。
 */
@Composable
private fun SettingsCategoryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = rememberRowInteractionSource(),
                indication = androidx.compose.material3.ripple(),
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
