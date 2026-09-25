package com.otakup.niriko.ui.settings.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.otakup.niriko.data.themepack.ThemePackImportRequest
import com.otakup.niriko.data.themepack.ThemePackImportResult
import com.otakup.niriko.data.themepack.ThemePackMeta
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.settings.SettingsActionRow
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsPickerRow
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.util.WallpaperPage
import com.otakup.niriko.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * 主题包管理区块：导入（含外部"打开方式"唤起）/ 应用 / 重新导出 / 删除。
 * 导出 = 把当前配色 + 壁纸打包为 .nirikotheme 分享。
 */
@Composable
fun ThemePackSection(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = remember { context.nirikoApp.themePackManager }
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()

    var packs by remember { mutableStateOf(manager.listInstalled()) }
    var importing by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<String?>(null) }
    var isExternalOpen by remember { mutableStateOf(false) }

    // 外部 .nirikotheme 打开：暂存 URI → 弹确认
    LaunchedEffect(Unit) {
        ThemePackImportRequest.uri?.let {
            pendingImportUri = it
            isExternalOpen = true
            ThemePackImportRequest.uri = null
        }
    }

    fun applyPack(meta: ThemePackMeta) {
        val payload = manager.buildApply(meta)
        viewModel.setCustomSeedColor(payload.seedColorArgb)
        if (payload.wallpaperUri != null) {
            viewModel.setWallpaperUri(payload.wallpaperUri)
            viewModel.setWallpaperEnabled(true)
        }
        viewModel.setWallpaperBlurDp(payload.wallpaperBlurDp)
        viewModel.setSplashEnabled(payload.splashEnabled)
        viewModel.setActiveThemePackId(meta.id)
    }

    fun doImport(uriString: String) {
        scope.launch {
            importing = true
            val result = manager.import(Uri.parse(uriString))
            importing = false
            when (result) {
                is ThemePackImportResult.Success -> {
                    packs = manager.listInstalled()
                    applyPack(result.meta)
                    snackbarHostState.showSnackbar("已导入并应用主题：「" + result.meta.name + "」")
                }
                is ThemePackImportResult.Failure -> {
                    snackbarHostState.showSnackbar(result.reason)
                }
            }
        }
    }

    fun exportAndShare() {
        scope.launch {
            val file = manager.export(
                seedColorArgb = settings.customSeedColor,
                globalWallpaperUri = settings.wallpaperUri,
                perPageUris = mapOf(
                    WallpaperPage.LIBRARY.key to settings.wallpaperLibraryUri,
                    WallpaperPage.DISCOVER.key to settings.wallpaperDiscoverUri,
                    WallpaperPage.STATS.key to settings.wallpaperStatsUri,
                    WallpaperPage.SETTINGS.key to settings.wallpaperSettingsUri,
                ),
                blurDp = settings.wallpaperBlurDp,
                splashEnabled = settings.splashEnabled,
            )
            if (file != null) {
                val providerUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, providerUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "分享主题包"))
            } else {
                scope.launch { snackbarHostState.showSnackbar("导出主题包失败") }
            }
        }
    }

    fun shareInstalled(meta: ThemePackMeta) {
        val file = manager.exportInstalledPack(meta.id)
        if (file != null) {
            val providerUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, providerUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "分享主题包"))
        } else {
            scope.launch { snackbarHostState.showSnackbar("导出失败") }
        }
    }

    fun deletePack(meta: ThemePackMeta) {
        manager.delete(meta.id)
        packs = manager.listInstalled()
        if (settings.activeThemePackId == meta.id) {
            // 删除正在应用的主题包：配色回默认，其余（壁纸等）保留用户选择
            viewModel.setActiveThemePackId("")
            viewModel.setCustomSeedColor(null)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImportUri = uri.toString()
            isExternalOpen = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsGroupTitle("主题包")
        SettingsSplitGroup(content = listOf(
            {
                SettingsActionRow(
                    icon = Icons.Outlined.FileOpen,
                    title = "导入主题包",
                    description = ".nirikotheme 文件（≤50MB，支持图片/视频壁纸）",
                    isLoading = importing,
                    showChevron = false,
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                )
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.IosShare,
                    title = "导出当前为主题包",
                    description = "打包当前配色与壁纸，分享给他人",
                    showChevron = false,
                    onClick = { exportAndShare() },
                )
            },
        ))

        if (packs.isNotEmpty()) {
            SettingsGroupTitle("已安装")
            SettingsSplitGroup(content = packs.map { pack ->
                {
                    InstalledPackRow(
                        pack = pack,
                        active = pack.id == settings.activeThemePackId,
                        onApply = { applyPack(pack) },
                        onShare = { shareInstalled(pack) },
                        onDelete = { deletePack(pack) },
                    )
                }
            })
        }
    }

    // 导入确认弹窗（文件选择器结果 或 外部打开）
    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(if (isExternalOpen) "打开主题包" else "导入主题包") },
            text = { Text("导入后将立即应用其中的配色与壁纸，并加入已安装列表。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val uriStr = pendingImportUri ?: ""
                    pendingImportUri = null
                    doImport(uriStr)
                }) { Text("导入并应用") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("取消") }
            },
        )
    }
}

/** 已安装主题包行：统一行解剖 + 分享/删除尾部控件（点击整行即应用）。 */
@Composable
private fun InstalledPackRow(
    pack: ThemePackMeta,
    active: Boolean,
    onApply: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val metaText = buildString {
        if (pack.author.isNotBlank()) append(pack.author).append(" · ")
        append("v").append(pack.version)
        if (pack.wallpaperType != "none") {
            append(" · ").append(if (pack.wallpaperType == "video") "视频壁纸" else "图片壁纸")
        }
    }
    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = "分享",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    SettingsPickerRow(
        icon = Icons.Outlined.Palette,
        title = pack.name,
        description = metaText,
        value = if (active) "应用中" else null,
        onClick = onApply,
        trailing = actions,
    )
}
