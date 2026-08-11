package com.otakup.niriko.ui.bilibili

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.otakup.niriko.plugin.bilibili.BilibiliSyncPreview
import com.otakup.niriko.plugin.bilibili.BilibiliWebBridge
import com.otakup.niriko.ui.bilibili.BilibiliJsBridge

/**
 * 哔哩哔哩导入工作流页。
 *
 * 布局分层（顶部不再侵占网页区域）：
 * - 单行状态条：图标 + 单行文案 + 「展开网页/收起网页」开关 + 评分拉取进度条；
 * - WebView 可折叠：默认展开（登录用），收到登录成功/列表消息后自动收起，
 *   需要时点「展开网页」恢复（重新获取时强制展开并重载）；
 * - 中部：追番预览列表（封面/title/状态/评分/短评 + 匹配结果 + 勾选）；
 * - 底部：覆盖开关 +「一键导入」，完成后 Snackbar 汇总。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliSyncScreen(
    viewModel: BilibiliSyncViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var reloadKey by remember { mutableIntStateOf(0) }
    var webViewExpanded by remember { mutableStateOf(true) }

    // 登录成功/列表返回后自动收起网页，把空间让给进度与预览
    LaunchedEffect(uiState.stage) {
        if (uiState.stage == BilibiliSyncStatus.LOGGED_IN ||
            uiState.stage == BilibiliSyncStatus.FETCHING_REVIEWS ||
            uiState.stage == BilibiliSyncStatus.READY
        ) {
            webViewExpanded = false
        }
    }

    // 导入完成提示（result 变化时弹出一次）
    LaunchedEffect(uiState.importResult) {
        val result = uiState.importResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            "导入完成：新增 ${result.imported}，跳过未匹配 ${result.skippedNotMatched}，" +
                "跳过已存在 ${result.skippedExisting}，失败 ${result.failed}",
        )
    }

    // 网页脚本异常：Snackbar 全文展示，避免被单行状态栏截断
    LaunchedEffect(uiState.debugError) {
        val error = uiState.debugError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar("网页脚本异常：$error")
        viewModel.consumeDebugError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("哔哩哔哩导入") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ===== 单行状态条 =====
            BilibiliSyncStatusBar(
                message = uiState.message,
                userName = uiState.userName,
                webViewExpanded = webViewExpanded,
                reviewDone = uiState.reviewDone,
                reviewTotal = uiState.reviewTotal,
                isFetchingReviews = uiState.isFetchingReviews,
                onToggleWebView = { webViewExpanded = !webViewExpanded },
                onRefresh = {
                    webViewExpanded = true
                    reloadKey += 1
                },
            )

            // ===== 可折叠登录 WebView =====
            AnimatedVisibility(visible = webViewExpanded) {
                LoginWebView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    reloadKey = reloadKey,
                    onMessage = viewModel::onBridgeMessage,
                )
            }

            // ===== 预览列表 / 空态 =====
            if (uiState.previews.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "预览（选中 ${uiState.selectedCount}/${uiState.previews.count { it.isMatched }} 项可导入）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.setAllSelected(true) }) { Text("全选") }
                    TextButton(onClick = { viewModel.setAllSelected(false) }) { Text("全不选") }
                }
                BilibiliSyncPreviewList(
                    previews = uiState.previews,
                    onToggle = viewModel::setSelected,
                    modifier = Modifier.weight(1f),
                )
                BilibiliSyncBottomBar(
                    selectedCount = uiState.selectedCount,
                    overwrite = uiState.overwrite,
                    isImporting = uiState.isImporting,
                    onOverwriteChange = viewModel::setOverwrite,
                    onImport = viewModel::import,
                )
            } else {
                // 拉取进行中：空态显示进度
                if (uiState.isFetchingReviews) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "正在拉取 ${uiState.reviewDone}/${uiState.reviewTotal} 条评分与短评…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "追番较多时约需半分钟，期间无需操作网页",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (uiState.stage) {
                                BilibiliSyncStatus.IDLE ->
                                    "在下方网页中登录哔哩哔哩后自动获取追番与评分"
                                else ->
                                    "正在获取数据…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ==================== 单行状态条 ====================

/**
 * 单行状态条（固定高度，不随文案换行撑高）：
 * 状态点 + 文案（单行省略）+ 网页展开/收起 + 重新获取 +（拉取时）细进度条。
 */
@Composable
private fun BilibiliSyncStatusBar(
    message: String,
    userName: String?,
    webViewExpanded: Boolean,
    onToggleWebView: () -> Unit,
    onRefresh: () -> Unit,
    reviewDone: Int = 0,
    reviewTotal: Int = 0,
    isFetchingReviews: Boolean = false,
) {
    Card {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态点
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (userName != null) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = onToggleWebView) {
                    Icon(
                        imageVector = if (webViewExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("网页")
                }
                TextButton(onClick = onRefresh) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("刷新")
                }
            }
            // 拉取进度条：细条置于状态条底边，避免额外占高
            if (isFetchingReviews && reviewTotal > 0) {
                LinearProgressIndicator(
                    progress = { (reviewDone.toFloat() / reviewTotal).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }
    }
}

// ==================== WebView ====================

/**
 * 承载 m.bilibili.com 登录态的 WebView。
 *
 * 页面加载完成后注入 [BilibiliWebBridge.injectedJavaScript]；消息经 [BilibiliJsBridge] 回传。
 * [reloadKey] 变化时重载页面（重跑注入脚本），Cookie 由 WebView 会话保持。
 * 视口设置：useWideViewPort + loadWithOverviewMode，避免移动页在窄 WebView 中比例错乱。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    modifier: Modifier = Modifier,
    reloadKey: Int,
    onMessage: (String) -> Unit,
) {
    val bridge = remember { BilibiliJsBridge(onMessage) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // 保留移动端标识，避免 B 站识别为桌面而跳转桌面版
                /* 不再替换 userAgent */
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // 仅注入白名单域：防止第三方页面（外链跳转）被注入脚本/bridge 投毒
                        if (isBilibiliDomain(url)) {
                            view?.evaluateJavascript(BilibiliWebBridge.injectedJavaScript(), null)
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString()
                        return if (isBilibiliDomain(url)) {
                            false // 留在 WebView 内
                        } else {
                            // 外链交给系统浏览器打开，避免 bridge 暴露给第三方域
                            runCatching {
                                view?.context?.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(url),
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                            true
                        }
                    }
                }
                addJavascriptInterface(bridge, "NirikoBridge")
                loadUrl(BilibiliWebBridge.URL_ZONE)
            }.also { webViewRef = it }
        },
        update = { view ->
            if (view.tag != reloadKey) {
                view.tag = reloadKey
                // 保持会话：仅重载页面，不清 Cookie；先重置幂等 guard 让脚本可重新 bootstrap
                view.evaluateJavascript(BilibiliWebBridge.resetInjected(), null)
                view.loadUrl(BilibiliWebBridge.URL_ZONE)
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.removeJavascriptInterface("NirikoBridge")
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

/**
 * 判断 URL 是否属于 Bilibili 白名单域（www/m/api/passport 等子域）。
 * 用于 WebView 注入与导航拦截：仅放行 B 站相关域名，避免 bridge 暴露给第三方。
 */
private fun isBilibiliDomain(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = runCatching {
        android.net.Uri.parse(url).host?.lowercase()
    }.getOrNull() ?: return false
    return host == "bilibili.com" || host.endsWith(".bilibili.com")
}

// ==================== 预览列表 ====================

@Composable
private fun BilibiliSyncPreviewList(
    previews: List<BilibiliSyncPreview>,
    onToggle: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(previews, key = { it.mediaId }) { preview ->
            BilibiliSyncPreviewRow(
                preview = preview,
                onToggle = { onToggle(preview.mediaId, !preview.selected) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun BilibiliSyncPreviewRow(
    preview: BilibiliSyncPreview,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = preview.cover,
            contentDescription = preview.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 56.dp, height = 80.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preview.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(if (preview.totalEpisodes != null) "共${preview.totalEpisodes}话 " else "")
                        if (preview.progress != null) append("看到${preview.progress}话 ")
                        if (preview.biliScore != null) append("评分${preview.biliScore}")
                    }.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!preview.biliComment.isNullOrBlank()) {
                Text(
                    text = preview.biliComment,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                preview.bgmSubjectId != null -> {
                    val local = preview.localCollection
                    Text(
                        text = buildString {
                            append("已匹配 · 本地")
                            append(
                                when {
                                    local == null -> "未收藏"
                                    local.rating != null -> "评分${local.rating}"
                                    else -> "已收藏"
                                },
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                else -> {
                    Text(
                        text = "未匹配 Bangumi 条目（导入时跳过）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(
            checked = preview.selected,
            onCheckedChange = { onToggle() },
        )
    }
}

// ==================== 底部操作 ====================

@Composable
private fun BilibiliSyncBottomBar(
    selectedCount: Int,
    overwrite: Boolean,
    isImporting: Boolean,
    onOverwriteChange: (Boolean) -> Unit,
    onImport: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "覆盖已有评分/短评",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = overwrite,
                    onCheckedChange = onOverwriteChange,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onImport,
                enabled = selectedCount > 0 && !isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("导入中…")
                } else {
                    Text("一键导入（$selectedCount）")
                }
            }
        }
    }
}