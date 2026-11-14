package com.otakup.niriko.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.navigation.SETTINGS_ABOUT_ROUTE
import com.otakup.niriko.navigation.SETTINGS_APPEARANCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_DATASOURCE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_DONATE_ROUTE
import com.otakup.niriko.navigation.SETTINGS_LIBRARY_ROUTE
import com.otakup.niriko.navigation.SETTINGS_REFRESH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SEARCH_ROUTE
import com.otakup.niriko.navigation.SETTINGS_SYNC_ROUTE
import com.otakup.niriko.ui.adaptive.NirikoWindowLayout
import com.otakup.niriko.ui.adaptive.currentNirikoWindowLayout
import com.otakup.niriko.ui.common.reportBottomBarScroll
import com.otakup.niriko.ui.components.NirikoSearchField
import com.otakup.niriko.ui.components.nirikoRowIndication
import com.otakup.niriko.ui.theme.NirikoShapes

/**
 * 设置主页：
 * - 窄屏（COMPACT / MEDIUM）：分组 + 类别卡片（图标 + 名称 + 一句话描述），点类别进独立二级页；
 * - 宽屏（EXPANDED）：左列分类导航 + 右列二级页正文并排，见 SettingsExpandedScreen。
 * 二级页见 ui/settings/pages/，路由注册见 NirikoNavHost。
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToCategory: (String) -> Unit = {},
) {
    when (currentNirikoWindowLayout()) {
        // 宽屏：两栏并排（B2c），右列直接嵌真实二级页正文
        NirikoWindowLayout.EXPANDED -> SettingsExpandedScreen(
            modifier = modifier,
            onNavigateToCategory = onNavigateToCategory,
        )

        // 窄屏：B2c 之前的单列列表，代码路径与视觉零变化
        NirikoWindowLayout.COMPACT,
        NirikoWindowLayout.MEDIUM -> SettingsCategoryListScreen(
            modifier = modifier,
            onNavigateToCategory = onNavigateToCategory,
        )
    }
}

/**
 * 窄屏（COMPACT / MEDIUM）设置主页：B2c 之前的原实现，逐字保留。
 */
@Composable
private fun SettingsCategoryListScreen(
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
            // V-05：与组标题、组卡同一条 16dp 水平基线（此前这里是 20dp）
            modifier = Modifier.padding(
                start = SettingsPageHorizontalInset,
                end = SettingsPageHorizontalInset,
                top = 20.dp,
                bottom = 4.dp,
            ),
        )
        Spacer(Modifier.height(4.dp))

        // 10.17 设置搜索：命中标题 / 说明 / 分组，结果置顶，点击直达分类页。
        // 索引是显式表（见 SettingsSearchIndex.kt）——手写 Compose 设置页没有自描述能力，
        // 但显式表可以被单测钉住「新分类忘了进索引」。
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val searchIndex = remember { defaultSettingsIndex() }
        val searchHits = remember(searchQuery) { searchSettings(searchIndex, searchQuery) }
        // P2-1：整块换成共享组件 NirikoSearchField（玻璃胶囊 + 放大镜 + placeholder + 输入 + 清除）。
        // 改造前这里是本文件自绘的第 6 副「输入框面孔」：没有 focus 态（点进去只有光标在闪）、
        // 没有 imeAction、清除钮自己拼 IconButton —— 与作品库/搜索页的搜索框三套写法。
        // 组件自带 focus 描边（effectsFast + reduceMotion 降级）与 48dp 清除钮，本页不再重复实现。
        NirikoSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "搜索设置（标题 / 说明 / 分组）",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsPageHorizontalInset, vertical = 4.dp),
            minHeight = SettingsRowMinHeight,
            leadingIcon = Icons.Outlined.Search,
            onClear = { searchQuery = "" },
        )
        if (searchQuery.isNotBlank()) {
            SettingsGroupTitle(
                "搜索结果",
                description = if (searchHits.isEmpty()) "没有找到相关设置" else "共 ${searchHits.size} 项",
            )
            if (searchHits.isEmpty()) {
                SettingsSplitGroup(
                    content = listOf({
                        SettingsInfoRow(
                            icon = Icons.Outlined.Search,
                            title = "没有找到这个设置",
                            description = "试试「壁纸」「同步」「更新」「备份」等关键词",
                        )
                    })
                )
            } else {
                SettingsSplitGroup(
                    content = searchHits.map { hit ->
                        { SettingsSearchResultRow(hit) { onNavigateToCategory(hit.entry.route) } }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        SettingsGroupTitle("偏好", description = "外观、收藏展示与搜索")
        SettingsSplitGroup(
            content = listOf(
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
            )
        )

        SettingsGroupTitle("数据", description = "数据源、账号、同步与备份")
        SettingsSplitGroup(
            content = listOf(
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
            )
        )

        SettingsGroupTitle("其他", description = "诊断与版本信息")
        SettingsSplitGroup(
            content = listOf(
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
                {
                    SettingsCategoryRow(
                        title = "向开发者捐赠",
                        subtitle = "帮助我继续更新",
                        icon = Icons.Outlined.VolunteerActivism,
                        onClick = { onNavigateToCategory(SETTINGS_DONATE_ROUTE) },
                    )
                },
            )
        )

        // 底部留白必须盖住悬浮 dock，否则最后一行会被压在胶囊底下。
        // dock 的实际占位 = 64dp 胶囊 + 14dp 距底偏移(+ 系统导航栏 inset)，
        // 这里取与发现/统计信息流同款的 120dp，并同样叠加导航栏 inset —— 两种 inset
        // 归属模型（外层是否已消化底部 inset）下，最后一行到 dock 顶边的净空都是 ~42dp。
        Spacer(
            Modifier
                .navigationBarsPadding()
                .height(DockBottomReserve),
        )
    }
}

/**
 * 顶层页底部为悬浮 dock 预留的空白高度。
 *
 * dock 胶囊本身 64dp（见 [com.otakup.niriko.ui.bottombar.LiquidBottomTabs]），
 * 再加距底 14dp；留白必须大于一个 dock 高度，否则分组最后一行会被挡住。
 */
private val DockBottomReserve = 120.dp

/**
 * 分类行（Kazumi SettingsCategoryTile 等价物）：
 * 36dp 圆角方片图标底（[SettingsRowIconChip]）+ 标题 bodyLarge + 描述 bodySmall + [SettingsChevron]。
 */
@Composable
private fun SettingsCategoryRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val interactionSource = rememberRowInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // V-07：分类行与组内行共用同一套行高基准（标题 + 说明 = 72dp）
            .heightIn(min = SettingsRowTwoLineMinHeight)
            .clickable(
                interactionSource = interactionSource,
                // M1：分类行的圆角 morph 动画保留（正向样本），只把 M3 矩形水波换成整行覆盖
                indication = null,
                onClick = onClick,
            )
            .nirikoRowIndication(
                interactionSource = interactionSource,
                cornerRadius = NirikoShapes.RowInner,
            )
            .padding(horizontal = SettingsPageHorizontalInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // V-08/V-05：36dp 圆形实底改成 ControlShape 圆角方片 + 玻璃标签底，与组卡"瓷"材质同族
        SettingsRowIconChip(icon)
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
        SettingsChevron()
    }
}

/**
 * 搜索结果行：命中标题时把匹配片段标成主题色 + 加粗。
 *
 * 不复用 [SettingsCategoryRow]：后者只接受 String，而高亮需要 AnnotatedString；
 * 这里只画「图标 + 高亮标题 + 分组面包屑 + chevron」，与分类行视觉一致。
 */
@Composable
private fun SettingsSearchResultRow(
    hit: SettingsSearchHit,
    onClick: () -> Unit,
) {
    val interactionSource = rememberRowInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // V-07 / M1：行高与按压反馈都和分类行、组内行保持一致
            .heightIn(min = SettingsRowTwoLineMinHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .nirikoRowIndication(
                interactionSource = interactionSource,
                cornerRadius = NirikoShapes.RowInner,
            )
            .padding(horizontal = SettingsPageHorizontalInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedTitle(hit),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${hit.entry.group} · ${hit.entry.pageTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SettingsChevron()
    }
}

/** 把命中区间染成主题色（越界区间会被裁掉，防脏数据把行渲染坏）。 */
@Composable
private fun highlightedTitle(hit: SettingsSearchHit): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    val title = hit.entry.title
    return buildAnnotatedString {
        append(title)
        hit.ranges.forEach { range ->
            val start = range.first.coerceIn(0, title.length)
            val end = (range.last + 1).coerceIn(start, title.length)
            if (end > start) {
                addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), start, end)
            }
        }
    }
}
