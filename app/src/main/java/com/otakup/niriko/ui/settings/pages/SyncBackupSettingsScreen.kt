package com.otakup.niriko.ui.settings.pages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.refresh.RefreshKeys
import com.otakup.niriko.ui.settings.SettingsActionRow
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsPickerRow
import com.otakup.niriko.ui.settings.SettingsSplitGroup
import com.otakup.niriko.ui.settings.SettingsSwitchRow
import com.otakup.niriko.viewmodel.BackupViewModel
import com.otakup.niriko.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 「X 分钟前」样式的时间描述。 */
private fun formatAgo(at: Long, now: Long): String {
    val minutes = ((now - at).coerceAtLeast(0L)) / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 24 * 60 -> "${minutes / 60} 小时前"
        else -> "${minutes / (24 * 60)} 天前"
    }
}

/** 「X 分钟后」样式的时间描述。 */
private fun formatUntil(at: Long, now: Long): String {
    val minutes = ((at - now).coerceAtLeast(0L) + 59_999) / 60_000
    return when {
        minutes < 1 -> "马上"
        minutes < 60 -> "${minutes} 分钟后"
        else -> "${minutes / 60} 小时后"
    }
}

/** 同步与备份设置页：WebDAV 同步 + JSON 备份导出/导入。 */
@Composable
fun SyncBackupSettingsScreen(
    viewModel: SettingsViewModel,
    backupViewModel: BackupViewModel?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsState()

    // 自动同步状态：改造前这两个开关打开后完全没有任何反馈（也没有任何消费方），
    // 现在把「上次同步时间 / 失败后的下次可重试时间」显示出来。
    val refreshSnapshots by viewModel.refreshSnapshots.collectAsState()
    val webDavSnapshot = refreshSnapshots[RefreshKeys.AUTO_SYNC_WEBDAV]
    val nowMs = System.currentTimeMillis()
    val autoSyncSummary = buildString {
        append("回到前台时与远端合并同步")
        webDavSnapshot?.lastSuccessAt?.takeIf { it > 0L }?.let {
            append(" · 上次 ").append(formatAgo(it, nowMs))
        }
        webDavSnapshot?.backoffUntil?.takeIf { it > nowMs }?.let {
            append(" · 上次失败，").append(formatUntil(it, nowMs)).append("重试")
        }
    }
    val backupState by if (backupViewModel != null) {
        backupViewModel.uiState.collectAsState()
    } else {
        remember { mutableStateOf(com.otakup.niriko.viewmodel.BackupUiState()) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showWebDavConfig by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var webDavUrlInput by remember { mutableStateOf(settings.webDavUrl) }
    var webDavUsernameInput by remember { mutableStateOf(settings.webDavUsername) }
    var webDavPasswordInput by remember { mutableStateOf(settings.webDavPassword) }

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
                    snackbarHostState.showSnackbar("保存失败: " + (e.message ?: ""))
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
                snackbarHostState.showSnackbar("读取备份文件失败: " + (e.message ?: ""))
            }
        }
    }

    // Snackbar 消息
    androidx.compose.runtime.LaunchedEffect(backupState.exportResult, backupState.error) {
        backupState.exportResult?.let { snackbarHostState.showSnackbar(it) }
        backupState.error?.let { snackbarHostState.showSnackbar(it) }
        backupViewModel?.clearMessages()
    }

    Box(modifier = modifier.fillMaxSize()) {
        SettingsDetailScaffold(title = "同步与备份", onBack = onBack) {
            // ===== WebDAV =====
            SettingsGroupTitle("WebDAV 同步")
            SettingsSplitGroup(content = listOf(
                {
                    SettingsPickerRow(
                        icon = Icons.Outlined.Cloud,
                        title = "服务器地址",
                        description = "WebDAV 服务端 URL 与账号",
                        value = if (settings.webDavUrl.isEmpty()) "未配置" else settings.webDavUrl,
                        onClick = { showWebDavConfig = true },
                    )
                },
                {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Autorenew,
                        title = "自动同步",
                        description = autoSyncSummary,
                        checked = settings.webDavAutoSync,
                        onCheckedChange = viewModel::setWebDavAutoSync,
                    )
                },
            ))
            if (settings.webDavUrl.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
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

            // ===== 备份 =====
            if (backupViewModel != null) {
                SettingsGroupTitle("数据备份")
                SettingsSplitGroup(content = listOf(
                    {
                        SettingsActionRow(
                            icon = Icons.Outlined.FileUpload,
                            title = "导出备份",
                            description = "将所有数据导出为 JSON 文件",
                            isLoading = backupState.isExporting,
                            showChevron = false,
                            onClick = {
                                backupViewModel.getExportFileName().let { name ->
                                    exportLauncher.launch(name)
                                }
                            },
                        )
                    },
                    {
                        SettingsActionRow(
                            icon = Icons.Outlined.FileDownload,
                            title = "从备份恢复",
                            description = "从 JSON 文件恢复全部数据（将覆盖现有数据）",
                            isLoading = backupState.isImporting,
                            showChevron = false,
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        )
                    },
                ))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
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
                        visualTransformation = PasswordVisualTransformation(),
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
                TextButton(onClick = { showWebDavConfig = false }) { Text("取消") }
            },
        )
    }
}
