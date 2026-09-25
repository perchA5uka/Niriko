package com.otakup.niriko.ui.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.ui.settings.SettingsSwitchRow
import com.otakup.niriko.viewmodel.SettingsViewModel

/** 搜索设置页。 */
@Composable
fun SearchSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsState()
    SettingsDetailScaffold(title = "搜索", onBack = onBack, modifier = modifier) {
        SettingsGroupTitle("搜索行为")
        SettingsSplitGroup(content = listOf(
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Visibility,
                    title = "允许显示 R18 内容",
                    description = "搜索与发现中展示成人向条目",
                    checked = settings.nsfwEnabled,
                    onCheckedChange = viewModel::setNsfwEnabled,
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Lightbulb,
                    title = "显示搜索建议",
                    description = "输入时显示历史记录与聚合建议",
                    checked = settings.showSearchSuggestions,
                    onCheckedChange = viewModel::setShowSearchSuggestions,
                )
            },
        ))
    }
}
