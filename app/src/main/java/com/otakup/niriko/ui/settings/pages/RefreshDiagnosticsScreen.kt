package com.otakup.niriko.ui.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import com.otakup.niriko.data.refresh.RefreshStatusLabels
import com.otakup.niriko.ui.settings.SettingsActionRow
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsInfoRow
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

/**
 * 刷新诊断页（模块 8）。
 *
 * 改造前每个仓储各写各的 TTL、失败一律 runCatching 静默，用户和开发者都无从判断
 * 「这块数据是刚拉的还是三天前的缓存」「为什么它一直不更新」。
 * 这里把 RefreshCoordinator 的全部状态摊开，并提供两个操作：
 *
 * - 立即强制刷新应用级数据：Steam 排行榜 / Steam 自动匹配，绕过新鲜度与退避；
 * - 清除全部刷新记录：不立即请求，只是让下一次刷新重新拉取所有资源。
 *
 * 这同时也是「手动强制刷新」的可发现入口 —— 下拉手势是没有提示的隐藏操作。
 */
@Composable
fun RefreshDiagnosticsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshots by viewModel.refreshSnapshots.collectAsState()
    val runningKeys by viewModel.refreshRunningKeys.collectAsState()

    // 相对时间自己走字，否则「3 分钟前」会一直停在打开页面那一刻
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }

    val rows = remember(snapshots, nowMs) {
        snapshots.entries
            .map { it.key to it.value }
            .sortedWith { a, b -> RefreshStatusLabels.diagnoseOrder(a, b, nowMs) }
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxWidth()) {
        SettingsDetailScaffold(title = "刷新诊断", onBack = onBack) {
            SettingsGroupTitle("操作")
            SettingsSplitGroup(content = listOf(
                {
                    SettingsActionRow(
                        icon = Icons.Outlined.Refresh,
                        title = "立即强制刷新应用级数据",
                        description = "Steam 排行榜与自动匹配，绕过新鲜度与退避",
                        showChevron = false,
                        onClick = viewModel::forceRefreshAppResources,
                    )
                },
                {
                    SettingsActionRow(
                        icon = Icons.Outlined.RestartAlt,
                        title = "清除全部刷新记录",
                        description = "不立即请求；下次进入各页面时全部重新拉取",
                        showChevron = false,
                        onClick = viewModel::resetAllRefreshRecords,
                    )
                },
            ))

            if (runningKeys.isNotEmpty()) {
                SettingsGroupTitle("进行中")
                SettingsSplitGroup(content = runningKeys.sorted().map { key ->
                    {
                        SettingsInfoRow(
                            icon = Icons.Outlined.Sync,
                            title = RefreshStatusLabels.label(key),
                            value = "刷新中…",
                        )
                    }
                })
            }

            SettingsGroupTitle("资源状态（${rows.size}）")
            if (rows.isEmpty()) {
                SettingsSplitGroup(content = listOf({
                    SettingsInfoRow(
                        icon = Icons.Outlined.Info,
                        title = "暂无记录",
                        value = "尚未发生受编排的刷新",
                    )
                }))
            } else {
                SettingsSplitGroup(content = rows.map { (key, snapshot) ->
                    {
                        DiagnosticRow(
                            icon = diagnosticIcon(snapshot.lastError, snapshot.backoffUntil > nowMs),
                            title = RefreshStatusLabels.label(key),
                            summary = RefreshStatusLabels.describe(snapshot, nowMs),
                            error = snapshot.lastError,
                            onReset = { viewModel.resetRefreshKey(key) },
                        )
                    }
                })
            }

            SettingsGroupTitle("判定规则")
            SettingsSplitGroup(content = listOf({
                SettingsInfoRow(
                    icon = Icons.Outlined.Info,
                    title = "跳过与退避",
                    description = "命中软 TTL 的自动刷新会被跳过；失败后按 30s/60s/120s/300s 退避",
                )
            }))
        }
    }
}

/** 资源状态的图标：有错误 / 退避中 / 正常。 */
private fun diagnosticIcon(lastError: String?, backingOff: Boolean): ImageVector = when {
    !lastError.isNullOrBlank() -> Icons.Outlined.ErrorOutline
    backingOff -> Icons.Outlined.HourglassEmpty
    else -> Icons.Outlined.CheckCircle
}

/** 单条资源状态：图标 + 名称 + 摘要（+ 最近错误）+ 「清除」动作。 */
@Composable
private fun DiagnosticRow(
    icon: ImageVector,
    title: String,
    summary: String,
    error: String?,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onReset) { Text("清除") }
    }
}
