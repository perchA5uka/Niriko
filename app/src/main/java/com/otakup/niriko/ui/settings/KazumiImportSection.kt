package com.otakup.niriko.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.plugin.kazumi.KazumiHiveReader
import com.otakup.niriko.plugin.kazumi.KazumiImporter
import com.otakup.niriko.plugin.kazumi.KazumiImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「从 Kazumi 导入收藏」设置入口。
 *
 * 用户先在 Kazumi「设置→数据备份→导出 collectibles.hive」，再在本页选择该文件：
 * 读取 → 解析（KazumiHiveReader）→ 预览（N 条/新增/跳过非动画/失败）→
 * 确认导入（KazumiImporter，默认跳过已存在，可勾选覆盖）→ Snackbar 结果。
 *
 * @param subjectDao   导入目标 subjects DAO
 * @param collectionDao 导入目标 collections DAO
 * @param snackbarHost  结果提示
 */
@Composable
fun KazumiImportSection(
    subjectDao: SubjectDao,
    collectionDao: CollectionDao,
    snackbarHost: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewEntries by remember { mutableStateOf<List<com.otakup.niriko.plugin.kazumi.KazumiCollectEntry>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var overwrite by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<KazumiImportResult?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw Exception("无法读取文件")
                }
                val entries = withContext(Dispatchers.IO) {
                    KazumiHiveReader.readCollectibles(bytes)
                }
                if (entries.isEmpty()) {
                    importResult = null
                    snackbarHost.showSnackbar("未在文件中发现 Kazumi 收藏（请确认导出的是 collectibles.hive）")
                } else {
                    previewEntries = entries
                }
            } catch (e: Exception) {
                snackbarHost.showSnackbar("解析失败：${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    SettingsActionRow(
        icon = Icons.Outlined.Download,
        title = "从 Kazumi 导入收藏",
        description = "选择 Kazumi 备份文件 collectibles.hive（仅导入动画）",
        isLoading = isLoading,
        enabled = !isLoading,
        showChevron = false,
        onClick = { filePicker.launch(arrayOf("*/*")) },
        modifier = modifier,
    )

    // ==== 预览确认对话框 ====
    previewEntries?.let { entries ->
        AlertDialog(
            onDismissRequest = { previewEntries = null },
            title = { Text("导入 Kazumi 收藏") },
            text = {
                Column {
                    Text(
                        text = "解析到 ${entries.size} 部动画收藏，将按状态导入 Niriko。",
                    )
                    Spacer(Modifier.width(0.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = overwrite,
                            onCheckedChange = { overwrite = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("覆盖 Niriko 中已存在的收藏状态", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val snapshot = entries
                        val doOverwrite = overwrite
                        previewEntries = null
                        scope.launch {
                            isLoading = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    KazumiImporter(subjectDao, collectionDao).import(snapshot, overwrite = doOverwrite)
                                }
                                importResult = result
                                snackbarHost.showSnackbar(
                                    "导入完成：新增 ${result.imported}，跳过非动画 ${result.skippedNonAnime}，" +
                                        "跳过已存在 ${result.skippedExisting}，失败 ${result.failed}",
                                )
                            } catch (e: Exception) {
                                snackbarHost.showSnackbar("导入失败：${e.message}")
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { previewEntries = null }) { Text("取消") }
            },
        )
    }
}