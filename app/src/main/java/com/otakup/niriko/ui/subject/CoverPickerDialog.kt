package com.otakup.niriko.ui.subject

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.otakup.niriko.nirikoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 更换封面弹窗（阶段 E）。
 * 候选：当前源封面大图 + 「从相册选择」。选择后拷贝到 filesDir/covers/ 并写入覆盖存储，
 * 所有使用 CoverImage(subjectId) 的卡片/详情页封面自动失效原封面。
 * 提供「恢复默认封面」。
 */
@Composable
fun CoverPickerDialog(
    subjectId: Long,
    currentCoverUrl: String?,
    onDismiss: () -> Unit,
    /** TMDb 候选海报（由调用方按需拉取；空表示未配置 TMDb 或尚未加载）。 */
    remoteCandidates: List<CoverCandidate> = emptyList(),
    /** 正在拉取候选。 */
    loadingRemote: Boolean = false,
    /** 触发拉取 TMDb 候选。 */
    onRequestRemote: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.nirikoApp
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var previewUrl by remember { mutableStateOf<String?>(null) }

    // 打开即拉一次候选（对齐 AniShelf：进入选择器就有图可选，不需要额外点一下）
    LaunchedEffect(subjectId) { onRequestRemote() }

    // 清空覆盖（恢复默认）
    fun clearOverride() {
        scope.launch { app.coverOverrideStore.clearOverride(subjectId); onDismiss() }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            saving = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val mime = context.contentResolver.getType(uri)
                    val ext = when {
                        mime?.contains("png") == true -> "png"
                        mime?.contains("webp") == true -> "webp"
                        else -> "jpg"
                    }
                    val dir = File(context.filesDir, "covers").apply { mkdirs() }
                    val dest = File(dir, "$subjectId.$ext")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    } ?: error("无法读取所选图片")
                    dest.toURI().toString()
                }
            }.onSuccess { fileUri ->
                scope.launch { app.coverOverrideStore.setOverride(subjectId, fileUri); onDismiss() }
            }.onFailure { }
            saving = false
        }
    }

    // 全屏预览（对齐 AniShelf 的「先看大图再确认」）
    previewUrl?.let { url ->
        com.otakup.niriko.ui.common.ImageViewer(
            urls = listOf(url),
            initialIndex = 0,
            title = "封面预览",
            onDismiss = {
                scope.launch {
                    app.coverOverrideStore.setOverride(subjectId, url)
                    previewUrl = null
                    onDismiss()
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更换封面") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "选择新封面后，收藏卡与详情页将显示该封面；内容元数据不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 当前源封面（候选 1）
                if (currentCoverUrl != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoverThumbnailOrPlaceholder(currentCoverUrl)
                        Column {
                            Text("当前源封面", style = MaterialTheme.typography.labelMedium)
                            Text("默认使用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // TMDb 候选海报（候选 2..N）：多语言优先级排序已在仓库层完成
                if (loadingRemote) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("正在获取 TMDb 海报…", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (remoteCandidates.isNotEmpty()) {
                    Text(
                        "TMDb 海报（${remoteCandidates.size} 张，按语言与分辨率排序）",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(remoteCandidates) { candidate ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                coil.compose.AsyncImage(
                                    model = candidate.url,
                                    contentDescription = candidate.label,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .width(72.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { previewUrl = candidate.url },
                                )
                                Text(
                                    candidate.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // 上传（本地候选）
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable(enabled = !saving) { uploadLauncher.launch("image/*") }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (saving) "处理中…" else "从相册选择图片作为封面",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = { clearOverride() }) { Text("恢复默认封面") }
        },
    )
}

/** 封面候选（source 用于分组展示，label 展示语言/分辨率）。 */
data class CoverCandidate(
    val url: String,
    val label: String,
    val source: String = "tmdb",
)

@Composable
private fun CoverThumbnailOrPlaceholder(url: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.width(56.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        coil.compose.AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}
