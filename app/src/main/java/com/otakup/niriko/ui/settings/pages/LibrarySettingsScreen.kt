package com.otakup.niriko.ui.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsPickerRow
import com.otakup.niriko.ui.settings.SettingsRadioRow
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.ui.settings.SettingsSwitchRow
import com.otakup.niriko.ui.settings.SingleChoiceDialog
import com.otakup.niriko.viewmodel.SettingsViewModel

/** 收藏与展示设置页（二级页外壳：脚手架 + 返回箭头）。 */
@Composable
fun LibrarySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDetailScaffold(title = "收藏与展示", onBack = onBack, modifier = modifier) {
        LibrarySettingsContent(viewModel = viewModel)
    }
}

/**
 * 「收藏与展示」二级页正文（不含脚手架与返回箭头）：
 * 窄屏由 LibrarySettingsScreen 套进 SettingsDetailScaffold，宽屏由设置两栏的右列直接调用。
 */
@Composable
fun LibrarySettingsContent(
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsState()
    var showSortPicker by remember { mutableStateOf(false) }

    Column {
        SettingsGroupTitle("收藏卡片")
        SettingsSplitGroup(content = listOf(
            {
                SettingsPickerRow(
                    icon = Icons.Outlined.Sort,
                    title = "默认排序",
                    description = "作品库卡片的默认排序方式",
                    value = settings.defaultSortOrder.label,
                    onClick = { showSortPicker = true },
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Label,
                    title = "显示收藏状态标签",
                    description = "在卡片上显示想看/在看/看过/抛弃等标签",
                    checked = settings.showStatusTags,
                    onCheckedChange = viewModel::setShowStatusTags,
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Timeline,
                    title = "显示观看进度条",
                    description = "在卡片上显示集数进度条",
                    checked = settings.showProgressBar,
                    onCheckedChange = viewModel::setShowProgressBar,
                )
            },
        ))
        SettingsGroupTitle("统计与启动")
        SettingsSplitGroup(content = listOf(
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Insights,
                    title = "显示年度总结",
                    description = "在统计页顶部展示年度数据卡片",
                    checked = settings.showAnnuallySummary,
                    onCheckedChange = viewModel::setShowAnnuallySummary,
                )
            },
        ))

        // 三选项：就地单选（Kazumi radio 行）
        SettingsGroupTitle("启动时默认 Tab")
        SettingsSplitGroup(content = listOf(
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = "作品库",
                    selected = settings.startPage == "library",
                    onSelect = { viewModel.setStartPage("library") },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.Explore,
                    title = "发现",
                    selected = settings.startPage == "discover",
                    onSelect = { viewModel.setStartPage("discover") },
                )
            },
            {
                SettingsRadioRow(
                    icon = Icons.Outlined.Insights,
                    title = "统计",
                    selected = settings.startPage == "stats",
                    onSelect = { viewModel.setStartPage("stats") },
                )
            },
        ))
    }

    if (showSortPicker) {
        SingleChoiceDialog(
            title = "默认排序方式",
            options = SortOrder.entries.map { it to it.label },
            selected = settings.defaultSortOrder,
            onSelect = { viewModel.setDefaultSortOrder(it); showSortPicker = false },
            onDismiss = { showSortPicker = false },
        )
    }
}
