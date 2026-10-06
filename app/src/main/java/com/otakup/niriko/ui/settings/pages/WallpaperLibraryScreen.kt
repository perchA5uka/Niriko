package com.otakup.niriko.ui.settings.pages

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.otakup.niriko.data.wallpaper.WallpaperLibraryEntry
import com.otakup.niriko.data.wallpaper.WallpaperLibraryFiles
import com.otakup.niriko.data.wallpaper.WallpaperRotation
import com.otakup.niriko.ui.settings.SettingsActionRow
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.ui.settings.SettingsSwitchRow
import com.otakup.niriko.ui.settings.SingleChoiceDialog
import com.otakup.niriko.util.WallpaperPage
import com.otakup.niriko.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * 壁纸库（R3，参考 Peristyle 的壁纸库形态）：网格选图 + 收藏/标签 + 每日轮换 + 立即换一张。
 *
 * 库里的图在导入时就复制进 filesDir/wallpapers（见 WallpaperLibraryFiles），
 * 所以这里的 uri 都是本地 file URI，不依赖 SAF 长期读权限。
 */
@Composable
fun WallpaperLibraryScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    Box(modifier = modifier.fillMaxWidth()) {
        SettingsDetailScaffold(title = "壁纸库", onBack = onBack) {
            WallpaperLibraryContent(
                viewModel = viewModel,
                snackbarHostState = snackbarHostState,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 壁纸库正文（宽屏两栏可直接复用，见 AdaptiveSettingsPane）。 */
@Composable
fun WallpaperLibraryContent(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val entries = settings.wallpaperLibraryEntries

    // 点按某张 → 选应用目标（全局 / 四个顶层页）
    var applying by remember { mutableStateOf<WallpaperLibraryEntry?>(null) }
    // 长按某张 → 收藏 / 标签 / 删除
    var acting by remember { mutableStateOf<WallpaperLibraryEntry?>(null) }
    var tagging by remember { mutableStateOf<WallpaperLibraryEntry?>(null) }
    var tagDraft by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = uris.mapNotNull { uri ->
                WallpaperLibraryFiles.import(
                    context = context,
                    source = uri,
                    name = uri.lastPathSegment?.substringAfterLast('/').orEmpty(),
                    addedAt = System.currentTimeMillis(),
                )
            }
            if (imported.isEmpty()) {
                snackbarHostState.showSnackbar("导入失败，请换一张图片试试")
            } else {
                viewModel.setWallpaperLibraryEntries(entries + imported)
                snackbarHostState.showSnackbar("已导入 " + imported.size + " 张壁纸")
            }
        }
    }

    val candidates = WallpaperRotation.candidates(entries, settings.wallpaperRotationFavoritesOnly)

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsGroupTitle(
            text = "壁纸库",
            description = "库中 " + entries.size + " 张 · 收藏 " + entries.count { it.favorite } + " 张",
        )
        SettingsSplitGroup(content = listOf(
            {
                SettingsActionRow(
                    icon = Icons.Outlined.AddPhotoAlternate,
                    title = "导入壁纸",
                    description = "可多选，导入后复制进应用私有目录",
                    value = null,
                    showChevron = false,
                    onClick = { importLauncher.launch(arrayOf("image/*")) },
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Collections,
                    title = "每日轮换",
                    description = "每天自动从壁纸库换一张（只改全局壁纸槽位）",
                    checked = settings.wallpaperRotationEnabled,
                    onCheckedChange = viewModel::setWallpaperRotationEnabled,
                )
            },
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "只轮换收藏",
                    description = "打开后只从收藏的壁纸里轮换，没有收藏时保持现状",
                    checked = settings.wallpaperRotationFavoritesOnly,
                    enabled = settings.wallpaperRotationEnabled,
                    onCheckedChange = viewModel::setWallpaperRotationFavoritesOnly,
                )
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Shuffle,
                    title = "立即换一张",
                    description = if (candidates.isEmpty()) "壁纸库为空或没有收藏，先导入壁纸" else "候选 " + candidates.size + " 张",
                    value = null,
                    showChevron = false,
                    isLoading = false,
                    enabled = candidates.isNotEmpty(),
                    onClick = {
                        val next = WallpaperRotation.next(
                            entries = entries,
                            currentUri = settings.wallpaperUri,
                            favoritesOnly = settings.wallpaperRotationFavoritesOnly,
                        ) ?: return@SettingsActionRow
                        viewModel.setWallpaperUri(next.uri)
                        viewModel.setWallpaperEnabled(true)
                        scope.launch { snackbarHostState.showSnackbar("已换一张壁纸") }
                    },
                )
            },
        ))

        SettingsGroupTitle(
            text = "库中壁纸",
            description = "点按应用（可选全局或单页）· 长按收藏 / 标签 / 删除",
        )
        if (entries.isEmpty()) {
            Text(
                text = "还没有壁纸。点上面的「导入壁纸」把喜欢的图加进来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { entry ->
                            WallpaperThumbnail(
                                context = context,
                                entry = entry,
                                inUse = entry.uri == settings.wallpaperUri,
                                modifier = Modifier.weight(1f),
                                onClick = { applying = entry },
                                onLongClick = { acting = entry },
                            )
                        }
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    // ===== 应用目标选择 =====
    applying?.let { entry ->
        SingleChoiceDialog(
            title = "把这张设为壁纸",
            options = listOf("" to "全局（未单独设置的页面都用它）") +
                WallpaperPage.entries.map { it.key to it.label + " 页" },
            selected = "",
            onSelect = { target ->
                if (target.isEmpty()) {
                    viewModel.setWallpaperUri(entry.uri)
                } else {
                    viewModel.setWallpaperPageUri(target, entry.uri)
                }
                viewModel.setWallpaperEnabled(true)
                applying = null
                scope.launch {
                    val where = WallpaperPage.entries.firstOrNull { it.key == target }?.label ?: "全局"
                    snackbarHostState.showSnackbar("已设为" + where + "壁纸")
                }
            },
            onDismiss = { applying = null },
        )
    }

    // ===== 长按操作 =====
    acting?.let { entry ->
        AlertDialog(
            onDismissRequest = { acting = null },
            title = { Text(entry.name.ifBlank { "壁纸" }) },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.setWallpaperLibraryEntries(
                            entries.map { if (it.id == entry.id) it.copy(favorite = !it.favorite) else it },
                        )
                        acting = null
                    }) { Text(if (entry.favorite) "取消收藏" else "收藏") }
                    TextButton(onClick = {
                        tagDraft = entry.tags.joinToString(", ")
                        tagging = entry
                        acting = null
                    }) { Text("编辑标签") }
                    TextButton(onClick = {
                        WallpaperLibraryFiles.delete(context, entry)
                        viewModel.setWallpaperLibraryEntries(entries.filterNot { it.id == entry.id })
                        acting = null
                    }) { Text("删除") }
                }
            },
            confirmButton = {
                TextButton(onClick = { acting = null }) { Text("关闭") }
            },
        )
    }

    // ===== 标签编辑 =====
    tagging?.let { entry ->
        AlertDialog(
            onDismissRequest = { tagging = null },
            title = { Text("编辑标签") },
            text = {
                OutlinedTextField(
                    value = tagDraft,
                    onValueChange = { tagDraft = it },
                    label = { Text("用逗号分隔，例如 风景, 夜景") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val tags = tagDraft.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }
                    viewModel.setWallpaperLibraryEntries(
                        entries.map { if (it.id == entry.id) it.copy(tags = tags) else it },
                    )
                    tagging = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { tagging = null }) { Text("取消") }
            },
        )
    }
}

/** 库中一张壁纸的缩略图（2:3 竖版，与作品库海报网格同比例）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallpaperThumbnail(
    context: Context,
    entry: WallpaperLibraryEntry,
    inUse: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(entry.uri).crossfade(true).build(),
            contentDescription = entry.name.ifBlank { "壁纸" },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        if (entry.favorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "已收藏",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp),
            )
        }
        if (inUse) {
            Text(
                text = "使用中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 6.dp),
            )
        }
    }
}

