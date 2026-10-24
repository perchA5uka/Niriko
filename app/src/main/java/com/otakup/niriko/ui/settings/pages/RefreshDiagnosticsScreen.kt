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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.otakup.niriko.data.diagnose.fetchLatestRelease
import com.otakup.niriko.data.diagnose.isNewerVersion
import com.otakup.niriko.data.diagnose.ContrastCheck
import com.otakup.niriko.data.diagnose.DEFAULT_CACHE_LIMIT_BYTES
import com.otakup.niriko.data.diagnose.Finding
import com.otakup.niriko.data.diagnose.HealthChecks
import com.otakup.niriko.data.diagnose.HealthInput
import com.otakup.niriko.data.diagnose.HealthSummary
import com.otakup.niriko.data.diagnose.ProbeCell
import com.otakup.niriko.data.diagnose.ProbeSummary
import com.otakup.niriko.data.diagnose.StorageBucket
import com.otakup.niriko.data.diagnose.StorageItem
import com.otakup.niriko.data.diagnose.StorageScan
import com.otakup.niriko.data.diagnose.auditContrast
import com.otakup.niriko.data.diagnose.planProbes
import com.otakup.niriko.data.diagnose.releasesUrl
import com.otakup.niriko.data.probe.SourceProbeRunner
import com.otakup.niriko.data.probe.UrlProbe
import com.otakup.niriko.data.snapshot.SnapshotFile
import com.otakup.niriko.data.snapshot.SnapshotRuntime
import com.otakup.niriko.data.snapshot.SnapshotStore
import com.otakup.niriko.nirikoApp
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import com.otakup.niriko.data.refresh.RefreshStatusLabels
import com.otakup.niriko.ui.settings.SettingsActionRow
import com.otakup.niriko.ui.settings.SettingsDetailScaffold
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsInfoRow
import com.otakup.niriko.ui.settings.SettingsPageSplitGroup
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
    SettingsDetailScaffold(title = "刷新诊断", onBack = onBack, modifier = modifier) {
        RefreshDiagnosticsContent(viewModel = viewModel)
    }
}

/**
 * 「刷新诊断」二级页正文（不含脚手架与返回箭头）：
 * 窄屏由 RefreshDiagnosticsScreen 套进 SettingsDetailScaffold，宽屏由设置两栏的右列直接调用。
 */
@Composable
fun RefreshDiagnosticsContent(
    viewModel: SettingsViewModel,
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

    // 13.4 检查更新：真查一次 releases/latest（3 秒超时）——但**只查版本，不下载、不安装**，
    // 也不申请 REQUEST_INSTALL_PACKAGES。版本比较与 JSON 解析都在 data/diagnose/UpdateCheck.kt（纯函数，有单测）。
    val context = LocalContext.current
    val appVersion = remember {
        runCatching { com.otakup.niriko.BuildConfig.VERSION_NAME }.getOrNull().orEmpty()
    }
    var updateState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Checking) }
    var checkAttempt by remember { mutableStateOf(0) }
    var lastCheckAt by remember { mutableStateOf(0L) }
    LaunchedEffect(checkAttempt) {
        updateState = UpdateCheckState.Checking
        updateState = fetchLatestRelease().fold(
            onSuccess = { release ->
                when {
                    appVersion.isBlank() -> UpdateCheckState.Failed("无法读取当前版本")
                    isNewerVersion(appVersion, release.tag) ->
                        UpdateCheckState.Available(release.tag, release.htmlUrl)
                    else -> UpdateCheckState.Latest
                }
            },
            onFailure = { UpdateCheckState.Failed(it.message ?: "网络不可用") },
        )
        lastCheckAt = System.currentTimeMillis()
    }

    // ===== C-4：五个可折叠 section 的只读数据（只采集一次，供体检/占用/快照复用）=====
    val settings by viewModel.settings.collectAsState()
    val app = context.nirikoApp
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    var expandedMask by remember { mutableIntStateOf(DiagnosticsFormat.defaultExpandedMask()) }

    var storageItems by remember { mutableStateOf<List<StorageItem>>(emptyList()) }
    var snapshotFiles by remember { mutableStateOf<List<SnapshotFile>>(emptyList()) }
    var healthFindings by remember { mutableStateOf<List<Finding>>(emptyList()) }
    var healthSummary by remember { mutableStateOf<HealthSummary?>(null) }
    var countsUnavailable by remember { mutableStateOf(false) }
    var healthLoading by remember { mutableStateOf(true) }
    var dataTick by remember { mutableIntStateOf(0) }

    var storageMessage by remember { mutableStateOf<String?>(null) }
    var storageBusy by remember { mutableStateOf(false) }
    var snapshotMessage by remember { mutableStateOf<String?>(null) }
    var snapshotBusy by remember { mutableStateOf(false) }

    var urlProbes by remember { mutableStateOf<List<UrlProbe>>(emptyList()) }
    var probeSummary by remember { mutableStateOf<ProbeSummary?>(null) }
    var probeBusy by remember { mutableStateOf(false) }

    val probeCapabilities = remember { SourceProbeRunner.capabilitiesOf(app.pluginManager.plugins) }
    val probeCells = remember(probeCapabilities) { planProbes(probeCapabilities) }

    LaunchedEffect(dataTick, settings.webDavUrl) {
        healthLoading = true
        val loadedCounts = viewModel.loadCollectionCounts()
        countsUnavailable = loadedCounts == null
        val loadedStorage = withContext(Dispatchers.IO) { StorageScan.scan(context) }
        storageItems = loadedStorage
        val loadedSnapshots = withContext(Dispatchers.IO) { SnapshotRuntime.list() }
        snapshotFiles = loadedSnapshots
        val contrastFailures = auditContrast(listOf(
            ContrastCheck("onSurface/surface", colorScheme.onSurface.toArgb(), colorScheme.surface.toArgb(), 4.5),
            ContrastCheck("onSurfaceVariant/surface", colorScheme.onSurfaceVariant.toArgb(), colorScheme.surface.toArgb(), 4.5),
            ContrastCheck("onPrimary/primary", colorScheme.onPrimary.toArgb(), colorScheme.primary.toArgb(), 3.0),
        )).size
        val input = HealthInput(
            collectionCount = loadedCounts?.total ?: 0,
            collectionWithoutProgress = loadedCounts?.withoutProgress ?: 0,
            collectionWithoutRating = loadedCounts?.withoutRating ?: 0,
            registeredSourceCount = loadedCounts?.registeredSources ?: app.pluginManager.plugins.size,
            snapshotCount = loadedSnapshots.size,
            lastSnapshotAgeHours = loadedSnapshots.maxOfOrNull { it.createdAt }
                ?.let { (System.currentTimeMillis() - it) / 3_600_000L },
            cacheBytes = loadedStorage.filter { it.bucket == StorageBucket.COVER }.sumOf { it.bytes },
            cacheLimitBytes = DEFAULT_CACHE_LIMIT_BYTES,
            webDavConfigured = settings.webDavUrl.isNotBlank(),
            contrastFailures = contrastFailures,
        )
        healthFindings = HealthChecks.run(input)
        healthSummary = HealthChecks.summarize(healthFindings)
        healthLoading = false
    }

    val onCleanupStale: () -> Unit = {
        scope.launch {
            storageBusy = true
            val removed = withContext(Dispatchers.IO) { StorageScan.cleanupStaleImages(context) }
            storageMessage = "已清理 " + removed + " 个文件"
            storageBusy = false
            dataTick++
        }
    }
    val onCreateSnapshot: () -> Unit = {
        scope.launch {
            snapshotBusy = true
            val json = runCatching { withContext(Dispatchers.IO) { app.backupManager.exportToJson(settings) } }.getOrNull()
            val written = withContext(Dispatchers.IO) {
                when {
                    json.isNullOrBlank() -> null
                    else -> SnapshotStore(context).write(json, "manual")?.let { "已创建 " + it.name }
                }
            }
            snapshotMessage = written ?: "创建失败（导出为空或写盘失败）"
            snapshotBusy = false
            dataTick++
        }
    }
    val onRunProbe: () -> Unit = {
        scope.launch {
            probeBusy = true
            val urls = probeUrlsFor(app.pluginManager.plugins.map { it.id })
            val result = withContext(Dispatchers.IO) { SourceProbeRunner.probeUrls(urls) }
            urlProbes = result
            probeSummary = SourceProbeRunner.summarize(result)
            probeBusy = false
        }
    }
    val onOpenReleases: () -> Unit = { openInBrowser(context, releasesUrl()) }

    Column {
        SettingsGroupTitle("操作")
        SettingsPageSplitGroup(content = listOf(
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

        // ===== C-4：五个可折叠 section（默认只展开体检）=====
        val healthLine = DiagnosticsFormat.healthSummaryLine(healthSummary, healthLoading, countsUnavailable)
        val storageLine = DiagnosticsFormat.storageSummaryLine(storageItems)
        val snapshotLine = DiagnosticsFormat.snapshotSummaryLine(snapshotFiles)
        val probeLine = DiagnosticsFormat.probeSummaryLine(probeCells, probeSummary)

        DiagnosticsHealthSection(
            findings = healthFindings,
            summary = healthSummary,
            summaryLine = healthLine,
            expanded = DiagnosticsFormat.isExpanded(expandedMask, DiagnosticsFormat.SECTION_HEALTH),
            onToggle = { expandedMask = DiagnosticsFormat.toggle(expandedMask, DiagnosticsFormat.SECTION_HEALTH) },
        )
        DiagnosticsStorageSection(
            items = storageItems,
            summaryLine = storageLine,
            message = storageMessage,
            busy = storageBusy,
            expanded = DiagnosticsFormat.isExpanded(expandedMask, DiagnosticsFormat.SECTION_STORAGE),
            onToggle = { expandedMask = DiagnosticsFormat.toggle(expandedMask, DiagnosticsFormat.SECTION_STORAGE) },
            onCleanup = onCleanupStale,
        )
        DiagnosticsSnapshotSection(
            files = snapshotFiles,
            summaryLine = snapshotLine,
            message = snapshotMessage,
            busy = snapshotBusy,
            expanded = DiagnosticsFormat.isExpanded(expandedMask, DiagnosticsFormat.SECTION_SNAPSHOT),
            onToggle = { expandedMask = DiagnosticsFormat.toggle(expandedMask, DiagnosticsFormat.SECTION_SNAPSHOT) },
            onSnapshot = onCreateSnapshot,
        )
        DiagnosticsProbeSection(
            capabilities = probeCapabilities,
            cells = probeCells,
            probes = urlProbes,
            summary = probeSummary,
            summaryLine = probeLine,
            busy = probeBusy,
            expanded = DiagnosticsFormat.isExpanded(expandedMask, DiagnosticsFormat.SECTION_PROBE),
            onToggle = { expandedMask = DiagnosticsFormat.toggle(expandedMask, DiagnosticsFormat.SECTION_PROBE) },
            onRun = onRunProbe,
        )

        if (runningKeys.isNotEmpty()) {
            SettingsGroupTitle("进行中")
            SettingsPageSplitGroup(content = runningKeys.sorted().map { key ->
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
            SettingsPageSplitGroup(content = listOf({
                SettingsInfoRow(
                    icon = Icons.Outlined.Info,
                    title = "暂无记录",
                    value = "尚未发生受编排的刷新",
                )
            }))
        } else {
            SettingsPageSplitGroup(content = rows.map { (key, snapshot) ->
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

        // C-4 第 5 个 section：折叠版「更新状态」（沿用上面的真实检查结果，不重复请求）
        val updateLine = when (val state = updateState) {
            is UpdateCheckState.Checking -> "正在检查更新…"
            is UpdateCheckState.Available ->
                DiagnosticsFormat.updateLine(appVersion, lastCheckAt, state.tag, true, nowMs)
            is UpdateCheckState.Latest ->
                DiagnosticsFormat.updateLine(appVersion, lastCheckAt, appVersion, false, nowMs)
            is UpdateCheckState.Failed ->
                DiagnosticsFormat.updateLine(appVersion, lastCheckAt, null, false, nowMs)
        }
        DiagnosticsUpdateSection(
            currentVersion = appVersion,
            statusLine = updateLine,
            busy = updateState is UpdateCheckState.Checking,
            expanded = DiagnosticsFormat.isExpanded(expandedMask, DiagnosticsFormat.SECTION_UPDATE),
            onToggle = { expandedMask = DiagnosticsFormat.toggle(expandedMask, DiagnosticsFormat.SECTION_UPDATE) },
            onCheck = { checkAttempt += 1 },
            onOpenReleases = onOpenReleases,
        )

        SettingsGroupTitle("判定规则")
        SettingsPageSplitGroup(content = listOf({
            SettingsInfoRow(
                icon = Icons.Outlined.Info,
                title = "跳过与退避",
                description = "命中软 TTL 的自动刷新会被跳过；失败后按 30s/60s/120s/300s 退避",
            )
        }))
    }
}

/**
 * 13.4 一次检查更新的 UI 状态（只在页面内，不落 DataStore：它是瞬时结果，不需要跨进程）。
 *
 * [Available] 带 release 的 html_url，点击直接打开**该 tag** 的发布页；[Failed] 只带原因，
 * UI 退化成「打开发布页」——失败也要有手动出口。
 */
private sealed interface UpdateCheckState {
    object Checking : UpdateCheckState
    data class Available(val tag: String, val url: String) : UpdateCheckState
    object Latest : UpdateCheckState
    data class Failed(val reason: String) : UpdateCheckState
}

/** 只跳浏览器；不申请安装权限、不下载 APK（13.4 的硬边界）。 */
private fun openInBrowser(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
