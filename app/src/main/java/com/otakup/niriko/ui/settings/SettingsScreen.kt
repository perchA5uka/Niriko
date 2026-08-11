package com.otakup.niriko.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
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
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.remote.BangumiClient.BangumiEndpoint
import com.otakup.niriko.data.settings.ThemeMode
import com.otakup.niriko.viewmodel.BackupViewModel
import com.otakup.niriko.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    backupViewModel: BackupViewModel? = null,
    modifier: Modifier = Modifier,
    onNavigateToBilibiliSync: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val backupState by if (backupViewModel != null) {
        backupViewModel.uiState.collectAsState()
    } else {
        remember { mutableStateOf(com.otakup.niriko.viewmodel.BackupUiState()) }
    }

    var showThemePicker by remember { mutableStateOf(false) }
    var showSortPicker by remember { mutableStateOf(false) }
    var showStartPagePicker by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var showWebDavConfig by remember { mutableStateOf(false) }
    var showBangumiConfig by remember { mutableStateOf(false) }
    var showSteamConfig by remember { mutableStateOf(false) }

    // WebDAV 编辑临时状态
    var webDavUrlInput by remember { mutableStateOf(settings.webDavUrl) }
    var webDavUsernameInput by remember { mutableStateOf(settings.webDavUsername) }
    var webDavPasswordInput by remember { mutableStateOf(settings.webDavPassword) }

    // Bangumi Token 编辑临时状态（不预填已存 Token，避免误显秘密）
    var bangumiTokenInput by remember { mutableStateOf("") }

    // Steam API Key 编辑临时状态（弹窗打开时同步已存值，避免误清）
    var steamApiKeyInput by remember { mutableStateOf(settings.steamApiKey) }
    LaunchedEffect(showSteamConfig) {
        if (showSteamConfig) steamApiKeyInput = settings.steamApiKey
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 导出文件选择器
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null || backupViewModel == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val json = backupViewModel.export()
            if (json != null) {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    snackbarHostState.showSnackbar("备份已保存")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("保存失败: ${e.message}")
                }
            }
        }
    }

    // 导入文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null || backupViewModel == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw Exception("无法读取文件")
                }
                pendingImportJson = json
                showImportConfirm = true
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("读取备份文件失败: ${e.message}")
            }
        }
    }

    // Snackbar 消息
    LaunchedEffect(backupState.exportResult, backupState.error) {
        backupState.exportResult?.let { snackbarHostState.showSnackbar(it) }
        backupState.error?.let { snackbarHostState.showSnackbar(it) }
        backupViewModel?.clearMessages()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // 标题
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )

            // ===== 外观 =====
            SettingsSection(title = "外观") {
                SettingsPickerRow(
                    title = "主题模式",
                    value = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    onClick = { showThemePicker = true },
                )
                SettingsSwitchRow(
                    title = "Material You 动态取色",
                    summary = "使用 Android 12+ 动态配色覆盖品牌色",
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                SettingsSwitchRow(
                    title = "OLED 纯黑优化",
                    summary = "深色模式下使用纯黑背景",
                    checked = settings.oledDark,
                    onCheckedChange = viewModel::setOledDark,
                )
            }

            // ===== 收藏 =====
            SettingsSection(title = "收藏") {
                SettingsPickerRow(
                    title = "默认排序",
                    value = settings.defaultSortOrder.label,
                    onClick = { showSortPicker = true },
                )
                SettingsSwitchRow(
                    title = "显示收藏状态标签",
                    summary = "在卡片上显示想看/在看/看过/抛弃等标签",
                    checked = settings.showStatusTags,
                    onCheckedChange = viewModel::setShowStatusTags,
                )
                SettingsSwitchRow(
                    title = "显示观看进度条",
                    summary = "在卡片上显示集数进度条",
                    checked = settings.showProgressBar,
                    onCheckedChange = viewModel::setShowProgressBar,
                )
            }

            // ===== 搜索 =====
            SettingsSection(title = "搜索") {
                SettingsSwitchRow(
                    title = "允许显示 R18 内容",
                    checked = settings.nsfwEnabled,
                    onCheckedChange = viewModel::setNsfwEnabled,
                )
                SettingsSwitchRow(
                    title = "显示搜索建议",
                    summary = "输入时显示历史搜索和建议",
                    checked = settings.showSearchSuggestions,
                    onCheckedChange = viewModel::setShowSearchSuggestions,
                )
            }

            // ===== 统计与启动 =====
            SettingsSection(title = "统计与启动") {
                SettingsSwitchRow(
                    title = "显示年度总结",
                    summary = "在统计页顶部展示年度数据卡片",
                    checked = settings.showAnnuallySummary,
                    onCheckedChange = viewModel::setShowAnnuallySummary,
                )
                SettingsPickerRow(
                    title = "启动时默认 Tab",
                    value = when (settings.startPage) {
                        "library" -> "作品库"
                        "discover" -> "发现"
                        "stats" -> "统计"
                        else -> "作品库"
                    },
                    onClick = { showStartPagePicker = true },
                )
            }

            // ===== 数据源 =====
            if (viewModel.plugins.isNotEmpty()) {
                SettingsSection(title = "数据源") {
                    // Bangumi 接口端点：官方站 / 国内反代
                    val endpoint = viewModel.settings.value.bangumiEndpoint
                    SettingsPickerRow(
                        title = "Bangumi 接口",
                        value = if (endpoint == BangumiEndpoint.PROXY) "国内反代" else "官方站",
                        onClick = { viewModel.setBangumiEndpoint(if (endpoint == BangumiEndpoint.PROXY) BangumiEndpoint.OFFICIAL else BangumiEndpoint.PROXY) },
                    )
                    viewModel.plugins.forEach { plugin ->
                        val isActive = plugin.id == viewModel.primaryPluginId
                        SettingsPickerRow(
                            title = plugin.name,
                            value = if (isActive) "✓ 当前" else "点击切换",
                            onClick = {
                                if (!isActive) viewModel.setActiveDataSourceId(plugin.id)
                            },
                        )
                    }
                    // Steam Web API Key（可选，游戏数据补充用）
                    SettingsPickerRow(
                        title = "Steam API Key",
                        value = if (viewModel.settings.value.steamApiKey.isEmpty()) "未配置(可选)" else "已配置(点击修改)",
                        onClick = { showSteamConfig = true },
                    )
                }
            }

            // ===== WebDAV 同步 =====
            SettingsSection(title = "WebDAV 同步") {
                val s = viewModel.settings.value
                SettingsPickerRow(
                    title = "服务器地址",
                    value = if (s.webDavUrl.isEmpty()) "未配置" else s.webDavUrl,
                    onClick = { showWebDavConfig = true },
                )
                SettingsSwitchRow(
                    title = "自动同步",
                    summary = "收藏变更时自动上传",
                    checked = s.webDavAutoSync,
                    onCheckedChange = viewModel::setWebDavAutoSync,
                )
                if (s.webDavUrl.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.uploadToWebDav() },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isSyncing.value,
                        ) {
                            if (viewModel.isSyncing.value) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("上传")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.downloadFromWebDav() },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isSyncing.value,
                        ) {
                            if (viewModel.isSyncing.value) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("下载")
                        }
                    }
                }
            }

            // ===== Bangumi 账号同步 =====
            SettingsSection(title = "Bangumi 账号") {
                val s = viewModel.settings.value
                val loggedIn = s.bangumiUsername.isNotEmpty()
                SettingsPickerRow(
                    title = "Access Token",
                    value = if (s.bangumiAccessToken.isEmpty()) "未配置" else "已配置(点击修改)",
                    onClick = { showBangumiConfig = true },
                )
                SettingsInfoRow(
                    title = "登录状态",
                    value = if (loggedIn) "已登录:${s.bangumiUsername}" else "未登录",
                )
                SettingsSwitchRow(
                    title = "启用同步",
                    summary = "双向同步收藏状态与评分",
                    checked = s.bangumiSyncEnabled,
                    onCheckedChange = viewModel::setBangumiSyncEnabled,
                )
                SettingsPickerRow(
                    title = "冲突优先级",
                    value = if (s.bangumiSyncPriority == com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority.LOCAL_FIRST) "本地优先" else "Bangumi 优先",
                    onClick = {
                        viewModel.setBangumiSyncPriority(
                            if (s.bangumiSyncPriority == com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority.LOCAL_FIRST)
                                com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority.BANGUMI_FIRST
                            else
                                com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority.LOCAL_FIRST
                        )
                    },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.loginBangumi() },
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.isBangumiSyncing.value && s.bangumiAccessToken.isNotEmpty(),
                    ) {
                        Text("登录 Bangumi")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.syncBangumi() },
                        modifier = Modifier.weight(1f),
                        enabled = !viewModel.isBangumiSyncing.value && s.bangumiSyncEnabled,
                    ) {
                        if (viewModel.isBangumiSyncing.value) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("立即同步")
                        }
                    }
                }
                val loginState = viewModel.bangumiLoginState.value
                if (loginState != null) {
                    SettingsInfoRow(title = "登录结果", value = loginState)
                }
                val progress = viewModel.bangumiProgress.value
                if (progress.isNotEmpty()) {
                    SettingsInfoRow(title = "进度", value = progress)
                }
                val result = viewModel.bangumiSyncResult.value
                if (result != null) {
                    SettingsInfoRow(title = "同步结果", value = result.message)
                }
            }

            // ===== 从 Kazumi 导入 =====
            SettingsSection(title = "Kazumi 导入") {
                val app = context.nirikoApp
                KazumiImportSection(
                    subjectDao = app.database.subjectDao(),
                    collectionDao = app.database.collectionDao(),
                    snackbarHost = snackbarHostState,
                )
            }

            // ===== 哔哩哔哩导入 =====
            SettingsSection(title = "哔哩哔哩导入") {
                BilibiliImportSection(
                    onNavigateToBilibiliSync = onNavigateToBilibiliSync,
                )
            }

            // ===== 数据备份 =====
            if (backupViewModel != null) {
                SettingsSection(title = "数据备份") {
                    SettingsIconActionRow(
                        icon = Icons.Outlined.FileUpload,
                        title = "导出备份",
                        subtitle = "将所有数据导出为 JSON 文件",
                        isLoading = backupState.isExporting,
                        onClick = {
                            backupViewModel.getExportFileName().let { name ->
                                exportLauncher.launch(name)
                            }
                        },
                    )
                    SettingsIconActionRow(
                        icon = Icons.Outlined.FileDownload,
                        title = "从备份恢复",
                        subtitle = "从 JSON 文件恢复全部数据（将覆盖现有数据）",
                        isLoading = backupState.isImporting,
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    )
                }
            }

            // ===== 关于 =====
            SettingsSection(title = "关于") {
                // 运行时读取版本号，避免硬编码与实际版本漂移
                // LocalContext 是 @Composable，须在 runCatching 外取（Kotlin 2.3 新检查）
                val context = LocalContext.current
                val versionName = runCatching {
                    val pm = context.packageManager
                    pm.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "1.0.0"
                SettingsInfoRow(title = "版本号", value = "v$versionName")
                SettingsActionRow(
                    title = "开源许可",
                    subtitle = "MIT License",
                    onClick = { showLicenseDialog = true },
                )
                SettingsActionRow(
                    title = "反馈与建议",
                    subtitle = "GitHub Issues",
                    onClick = { /* 预留：打开 GitHub 链接 */ },
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ===== 弹窗 =====

    if (showThemePicker) {
        SingleChoiceDialog(
            title = "主题模式",
            options = listOf(
                ThemeMode.SYSTEM to "跟随系统",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.DARK to "深色",
            ),
            selected = settings.themeMode,
            onSelect = { viewModel.setThemeMode(it); showThemePicker = false },
            onDismiss = { showThemePicker = false },
        )
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

    if (showStartPagePicker) {
        SingleChoiceDialog(
            title = "启动时默认 Tab",
            options = listOf(
                "library" to "作品库",
                "discover" to "发现",
                "stats" to "统计",
            ),
            selected = settings.startPage,
            onSelect = { viewModel.setStartPage(it); showStartPagePicker = false },
            onDismiss = { showStartPagePicker = false },
        )
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("开源许可") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text("本应用基于 MIT License 开源。")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "依赖库：\n" +
                            "• Jetpack Compose (Apache 2.0)\n" +
                            "• Material 3 (Apache 2.0)\n" +
                            "• Navigation Compose (Apache 2.0)\n" +
                            "• Room (Apache 2.0)\n" +
                            "• Coil (Apache 2.0)\n" +
                            "• Retrofit (Apache 2.0)\n" +
                            "• OkHttp (Apache 2.0)\n" +
                            "• Kotlinx Serialization (Apache 2.0)\n" +
                            "• DataStore (Apache 2.0)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    // 导入确认弹窗
    if (showImportConfirm && pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportJson = null
            },
            title = { Text("恢复备份") },
            text = { Text("此操作将覆盖当前所有数据（收藏、作品记录、搜索历史）。确定要继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirm = false
                        backupViewModel?.import(pendingImportJson ?: "")
                        pendingImportJson = null
                    },
                ) { Text("确定恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportJson = null
                }) { Text("取消") }
            },
        )
    }

    // WebDAV 配置弹窗
    if (showWebDavConfig) {
        AlertDialog(
            onDismissRequest = { showWebDavConfig = false },
            title = { Text("WebDAV 配置") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = webDavUrlInput,
                        onValueChange = { webDavUrlInput = it },
                        label = { Text("服务器 URL") },
                        placeholder = { Text("https://example.com/dav") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = webDavUsernameInput,
                        onValueChange = { webDavUsernameInput = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = webDavPasswordInput,
                        onValueChange = { webDavPasswordInput = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setWebDavUrl(webDavUrlInput)
                    viewModel.setWebDavUsername(webDavUsernameInput)
                    viewModel.setWebDavPassword(webDavPasswordInput)
                    showWebDavConfig = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // 重置为原始值
                    showWebDavConfig = false
                }) { Text("取消") }
            },
        )
    }

    // Bangumi Token 配置弹窗
    if (showBangumiConfig) {
        AlertDialog(
            onDismissRequest = { showBangumiConfig = false },
            title = { Text("Bangumi Access Token") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "在 bangumi.tv 设置 → 同步管理中创建一个 Access Token 后粘贴到下方。Token 仅保存在本机,请勿泄露。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = bangumiTokenInput,
                        onValueChange = { bangumiTokenInput = it },
                        label = { Text("Access Token") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBangumiAccessToken(bangumiTokenInput.trim())
                    bangumiTokenInput = ""
                    showBangumiConfig = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBangumiConfig = false }) { Text("取消") }
            },
        )
    }

    // Steam API Key 配置弹窗（可选：公开接口无需 key，仅未来扩展用）
    if (showSteamConfig) {
        AlertDialog(
            onDismissRequest = { showSteamConfig = false },
            title = { Text("Steam Web API Key") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "可选配置。当前游戏信息（价格/在线人数/开发商等）使用公开接口,无需 Key。\n如需使用更多 Steam Web API,可在 steamcommunity.com/dev/apikey 免费申请后填入。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = steamApiKeyInput,
                        onValueChange = { steamApiKeyInput = it },
                        label = { Text("API Key（留空清除）") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSteamApiKey(steamApiKeyInput)
                    showSteamConfig = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSteamConfig = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 带图标的设置操作行（用于备份/恢复按钮）。
 */
@Composable
private fun SettingsIconActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLoading) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/**
 * 通用单选弹窗。
 */
@Composable
private fun SettingsInfoLine(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}