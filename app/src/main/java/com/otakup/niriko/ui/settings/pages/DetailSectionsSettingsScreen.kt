package com.otakup.niriko.ui.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.adaptive.NirikoDetailPane
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.ui.settings.SettingsSwitchRow
import com.otakup.niriko.ui.subject.DetailLayoutPolicy
import com.otakup.niriko.viewmodel.SettingsViewModel

/**
 * 「详情部件」设置页（F06）：**顺序 + 显示 / 隐藏**。
 *
 * - 顺序：列出解析后的完整顺序（未知 key 已被忽略、新部件已按默认顺序接到尾部），
 *   每行上移/下移一位；「恢复默认顺序」只在改过时出现。
 * - 显示 / 隐藏：只列**允许隐藏**的部件（§9.1：标题、收藏、返回与保存操作不可隐藏）——
 *   核心部件连开关都不出现，而不是给一个点了没反应的禁用开关（后者只会让人以为坏了）。
 */
@Composable
fun DetailSectionsSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDetailScaffold(title = "详情部件", onBack = onBack, modifier = modifier) {
        DetailSectionsSettingsContent(viewModel = viewModel)
    }
}

/** 「详情部件」正文（不含脚手架；宽屏两栏设置页的右列也直接用这一份）。 */
@Composable
fun DetailSectionsSettingsContent(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val hidden = remember(settings.detailHiddenSections) {
        DetailLayoutPolicy.hiddenFromEncoded(settings.detailHiddenSections)
    }
    val order = remember(settings.detailSectionOrder) {
        DetailLayoutPolicy.resolveOrder(settings.detailSectionOrder)
    }
    val hideable = remember { DetailLayoutPolicy.hideable }
    val isDefaultOrder = remember(settings.detailSectionOrder) {
        DetailLayoutPolicy.isDefaultOrder(settings.detailSectionOrder)
    }

    Column {
        // ===== 顺序 =====
        SettingsGroupTitle("顺序")
        Text(
            text = "上下调整详情页里各区块的先后。改动立刻生效，随时可以恢复默认。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        SettingsSplitGroup(
            content = order.mapIndexed { index, section ->
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}. ${section.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (section.pane == NirikoDetailPane.CONTENT) "内容列" else "概览列",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                viewModel.setDetailSectionOrder(
                                    DetailLayoutPolicy.moveSection(
                                        settings.detailSectionOrder,
                                        section,
                                        offset = -1,
                                    ),
                                )
                            },
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移 ${section.label}")
                        }
                        IconButton(
                            enabled = index < order.lastIndex,
                            onClick = {
                                viewModel.setDetailSectionOrder(
                                    DetailLayoutPolicy.moveSection(
                                        settings.detailSectionOrder,
                                        section,
                                        offset = 1,
                                    ),
                                )
                            },
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移 ${section.label}")
                        }
                    }
                }
            },
        )
        if (!isDefaultOrder) {
            TextButton(
                onClick = { viewModel.setDetailSectionOrder("") },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { Text("恢复默认顺序") }
        }
        Spacer(Modifier.height(8.dp))

        // ===== 显示 / 隐藏 =====
        SettingsGroupTitle("显示的部件")
        Text(
            text = "关掉不需要的区块，详情页会立刻少一块（随时可以再打开）。" +
                "标题、封面与收藏操作属于核心部件，不提供隐藏。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        SettingsSplitGroup(
            content = hideable.map { id ->
                {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Visibility,
                        title = id.label,
                        description = if (id.pane == NirikoDetailPane.CONTENT) "宽屏时归入内容列" else null,
                        checked = id !in hidden,
                        onCheckedChange = { visible ->
                            // 规则在 ui 层的 DetailLayoutPolicy 里（纯函数、可测）；
                            // ViewModel 只负责把结果串写进 DataStore。
                            val next = if (visible) hidden - id else hidden + id
                            viewModel.setDetailHiddenSections(DetailLayoutPolicy.encodeHidden(next))
                        },
                    )
                }
            },
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** 分类页入口的副标题：可见部件数与顺序是否改过。 */
fun detailVisibleSectionSummary(encodedHidden: String?, encodedOrder: String? = null): String {
    val hidden = DetailLayoutPolicy.hiddenFromEncoded(encodedHidden)
    val total = DetailLayoutPolicy.hideable.size
    val visible = DetailLayoutPolicy.hideable.count { it !in hidden }
    val reordered = !DetailLayoutPolicy.isDefaultOrder(encodedOrder)
    return "$visible / $total 个部件显示中" + if (reordered) " · 已自定义顺序" else ""
}
