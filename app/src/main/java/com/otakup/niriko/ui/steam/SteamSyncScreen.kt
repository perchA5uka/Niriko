package com.otakup.niriko.ui.steam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.remote.steam.SteamLibraryPreview

/**
 * Steam 游戏库导入工作流页。
 *
 * 状态机：NEED_LOGIN（嵌入登录页）→ IDLE（已登录）→ PULLING → MATCHING →
 * READY（预览勾选）→ IMPORTING；ERROR 可重试。
 * 占位条目（Bangumi 无词条）单独分组显示「Steam 独占」标记，可重新匹配升级。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamSyncScreen(
    viewModel: SteamSyncViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Steam 游戏库导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (state.stage) {
                SteamSyncStatus.NEED_LOGIN -> {
                    // 未登录：内嵌登录页（登录成功回调切换阶段）
                    val loginViewModel = androidx.lifecycle.viewmodel.compose.viewModel<SteamLoginViewModel>(
                        factory = SteamLoginViewModelFactory(
                            settingsDataStore = com.otakup.niriko.data.settings.SettingsDataStore(
                                androidx.compose.ui.platform.LocalContext.current.applicationContext,
                            ),
                        ),
                    )
                    SteamLoginScreen(
                        viewModel = loginViewModel,
                        onLoginSuccess = { steamId64 ->
                            viewModel.onLoginSuccess(steamId64)
                        },
                        onBack = onBack,
                    )
                }

                SteamSyncStatus.ERROR -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("拉取失败", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.error ?: "未知错误",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::pullLibrary) {
                            Text("重试")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onBack) {
                            Text("返回")
                        }
                    }
                }

                SteamSyncStatus.PULLING, SteamSyncStatus.MATCHING -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                else -> {
                    // IDLE / READY / IMPORTING
                    Column(Modifier.fillMaxSize()) {
                        // 顶部状态条
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )

                        if (state.stage == SteamSyncStatus.IDLE) {
                            // 已登录：拉库按钮
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "已登录 SteamID64：${state.steamId64}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = viewModel::pullLibrary, modifier = Modifier.fillMaxWidth()) {
                                    Text("拉取我的游戏库")
                                }
                            }
                        }

                        if (state.isReady) {
                            SteamPreviewList(
                                previews = state.previews,
                                onToggle = viewModel::toggleSelection,
                                onRematch = viewModel::rematchPlaceholder,
                                onSelectAll = viewModel::selectAll,
                                onImport = viewModel::importSelected,
                                isImporting = state.isImporting,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        state.importResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "导入完成：新增 ${result.imported}，占位 ${result.placeholderCreated}，跳过已存在 ${result.skippedExisting}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 预览勾选列表。 */
@Composable
private fun SteamPreviewList(
    previews: List<SteamLibraryPreview>,
    onToggle: (Int, Boolean) -> Unit,
    onRematch: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onImport: () -> Unit,
    isImporting: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // 操作条
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "已选 ${previews.count { it.selected }} / ${previews.size}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onSelectAll) {
                Text("全选已匹配")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onImport, enabled = previews.any { it.selected } && !isImporting) {
                if (isImporting) CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                else Text("导入")
            }
        }
        HorizontalDivider()

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(previews, key = { it.appId }) { preview ->
                SteamPreviewRow(
                    preview = preview,
                    onToggle = { checked -> onToggle(preview.appId, checked) },
                    onRematch = { onRematch(preview.appId) },
                )
            }
        }
    }
}

/** 预览行：封面 + 名称 + 时长 + 匹配状态 + 勾选。 */
@Composable
private fun SteamPreviewRow(
    preview: SteamLibraryPreview,
    onToggle: (Boolean) -> Unit,
    onRematch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面
        AsyncImage(
            model = preview.coverUrl,
            contentDescription = preview.name,
            modifier = Modifier.width(48.dp).height(48.dp),
        )
        Spacer(Modifier.width(12.dp))

        // 信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                preview.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = buildString {
                if (preview.playtimeForeverMinutes > 0) {
                    append("游玩 ").append(formatPlaytime(preview.playtimeForeverMinutes))
                } else {
                    append("未游玩")
                }
                if (preview.isPlaceholder) {
                    append(" · ").append("Steam 独占")
                } else if (preview.isMatched) {
                    append(" · ").append("已匹配")
                } else {
                    append(" · ").append("未匹配")
                }
                if (preview.shared) {
                    append(" · ").append("家庭库")
                }
            }
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = if (preview.isPlaceholder) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.alreadyInCollection) {
                Text(
                    "已收藏",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        // 占位条目：重新匹配按钮
        if (preview.isPlaceholder) {
            OutlinedButton(onClick = onRematch) {
                Text("重匹配")
            }
            Spacer(Modifier.width(8.dp))
        }

        // 勾选
        Checkbox(
            checked = preview.selected,
            onCheckedChange = onToggle,
            enabled = !preview.isPlaceholder || preview.selected,
        )
    }
}

/** 分钟 → 可读时长。 */
private fun formatPlaytime(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    else -> "${minutes}m"
}
