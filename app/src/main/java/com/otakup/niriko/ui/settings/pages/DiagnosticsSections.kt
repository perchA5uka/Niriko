package com.otakup.niriko.ui.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.diagnose.Finding
import com.otakup.niriko.data.diagnose.HealthSummary
import com.otakup.niriko.data.diagnose.ProbeCell
import com.otakup.niriko.data.diagnose.ProbeStatus
import com.otakup.niriko.data.diagnose.ProbeSummary
import com.otakup.niriko.data.diagnose.Severity
import com.otakup.niriko.data.diagnose.SourceCapability
import com.otakup.niriko.data.diagnose.StorageBucket
import com.otakup.niriko.data.diagnose.StorageItem
import com.otakup.niriko.data.diagnose.formatBytes
import com.otakup.niriko.data.diagnose.summarize
import com.otakup.niriko.data.diagnose.totalBytes
import com.otakup.niriko.data.probe.UrlProbe
import com.otakup.niriko.data.snapshot.SnapshotFile
import com.otakup.niriko.ui.settings.SettingsActionRow
import com.otakup.niriko.ui.settings.SettingsGroupTitle
import com.otakup.niriko.ui.settings.SettingsInfoRow
import com.otakup.niriko.ui.settings.SettingsPageSplitGroup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * C-4：刷新诊断页的 5 个 section（体检 / 图片占用 / 本地快照 / 数据源自检 / 更新状态）。
 *
 * 只做**接线与渲染**：所有判定都在 data/diagnose、data/snapshot、data/probe 的纯函数里
 * （各自已有单测）。本文件里的 [DiagnosticsFormat] 只负责「人话文案与默认展开态」，
 * 因此也可以在 JVM 单测里断言（[DiagnosticsSectionsTest]）。
 */
internal object DiagnosticsFormat {

    /** 5 个 section 的位掩码下标（顺序 = 页面顺序）。 */
    const val SECTION_HEALTH = 0
    const val SECTION_STORAGE = 1
    const val SECTION_SNAPSHOT = 2
    const val SECTION_PROBE = 3
    const val SECTION_UPDATE = 4

    /** 默认只展开体检（位 0）。 */
    fun defaultExpandedMask(): Int = 1 shl SECTION_HEALTH

    fun isExpanded(mask: Int, section: Int): Boolean = (mask and (1 shl section)) != 0

    fun toggle(mask: Int, section: Int): Int = mask xor (1 shl section)

    /** 体检摘要：「8 项检查 · 2 项发现」。读取失败时明确说读不到，不假装干净。 */
    fun healthSummaryLine(summary: HealthSummary?, loading: Boolean, countsUnavailable: Boolean): String = when {
        loading -> "正在采集只读数据…"
        summary == null -> "尚未运行体检"
        else -> buildString {
            append(summary.checked).append(" 项检查")
            val findings = summary.warnings + summary.errors
            append(" · ").append(if (findings > 0) "${findings} 项发现" else "全部正常")
            if (summary.errors > 0) append(" · ").append(summary.errors).append(" 项错误")
            if (countsUnavailable) append(" · 计数读取失败（收藏类检查已跳过）")
        }
    }

    /** 单条检查的图标语义。 */
    fun severityLabel(severity: Severity): String = when (severity) {
        Severity.OK -> "正常"
        Severity.INFO -> "提示"
        Severity.WARN -> "注意"
        Severity.ERROR -> "错误"
    }

    /** 四类占用条的占比（总量为 0 时全 0，不产出 NaN）。 */
    fun bucketFraction(bytes: Long, total: Long): Float =
        if (total <= 0L) 0f else (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f)

    /** 占用摘要：总计 + 最大的一类。 */
    fun storageSummaryLine(items: List<StorageItem>): String {
        val total = totalBytes(items)
        if (items.isEmpty()) return "没有可统计的缓存目录"
        val biggest = summarize(items).maxByOrNull { it.bytes }
        return buildString {
            append("共 ").append(formatBytes(total))
            if (biggest != null && biggest.bytes > 0) {
                append(" · 最多：").append(biggest.bucket.label).append(" ").append(formatBytes(biggest.bytes))
            }
        }
    }

    /** 快照时间：yyyy-MM-dd HH:mm（本地时区；测试用 UTC 固定）。 */
    fun snapshotTime(createdAt: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)
            .withZone(zone)
            .format(Instant.ofEpochMilli(createdAt))

    /** 快照一行：时间 · 大小。 */
    fun snapshotLine(file: SnapshotFile, zone: ZoneId = ZoneId.systemDefault()): String =
        snapshotTime(file.createdAt, zone) + " · " + formatBytes(file.bytes)

    /** 快照摘要：最近 5 份 show 的时间范围。 */
    fun snapshotSummaryLine(files: List<SnapshotFile>): String =
        if (files.isEmpty()) "还没有本地快照" else "共 ${files.size} 份 · 最近 ${snapshotLine(files.last())}"

    /** 自检摘要：能力矩阵一行。 */
    fun probeSummaryLine(cells: List<ProbeCell>, summary: ProbeSummary?): String = buildString {
        val supported = cells.count { it.status != ProbeStatus.UNSUPPORTED }
        if (cells.isEmpty()) {
            append("没有已注册的数据源")
        } else {
            append(cells.size).append(" 项能力 · 声明支持 ").append(supported)
        }
        if (summary != null) {
            append(" · 探测 ").append(summary.probed).append(" 源，失败 ").append(summary.failed)
            if (summary.slowestMs > 0) append("，最慢 ").append(summary.slowestMs).append("ms")
        }
    }

    /** 失败摘要：每个源不支持的能力（最多列 4 个源）。 */
    fun unsupportedLine(bySource: Map<String, List<String>>): String {
        if (bySource.isEmpty()) return "所有声明支持的能力都可自检"
        return bySource.entries.take(4).joinToString("；") { (source, caps) ->
            source + " 缺 " + caps.take(3).joinToString("/") + if (caps.size > 3) "…" else ""
        }
    }

    /** 更新状态一行。lastCheckAt = 0 表示本次会话还没检查过。 */
    fun updateLine(
        current: String,
        lastCheckAt: Long,
        latestTag: String?,
        hasUpdate: Boolean,
        now: Long,
    ): String = when {
        lastCheckAt <= 0L -> "当前 v$current · 本次会话尚未检查"
        latestTag == null -> "当前 v$current · 检查失败（可直接打开发布页）"
        hasUpdate -> "当前 v$current · 有新版本 $latestTag"
        else -> "当前 v$current · 已是最新（$latestTag）"
    }

    /** 从 GitHub releases/latest 的 JSON 里取 tag_name（无网络时可单测）。 */
    fun extractTagName(json: String): String? =
        Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
}

/** 一个可折叠 section 的外壳：标题行（点击切换）+ 展开内容。 */
@Composable
internal fun DiagnosticsSection(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsPageSplitGroup(content = listOf({
        SettingsActionRow(
            icon = icon,
            title = title,
            description = summary,
            showChevron = true,
            onClick = onToggle,
        )
    }))
    AnimatedVisibility(visible = expanded) {
        Column { content() }
    }
}

/** ① 体检：8 项结果 + 摘要；修复按钮无执行器 → 置灰并说明。 */
@Composable
internal fun DiagnosticsHealthSection(
    findings: List<Finding>,
    summary: HealthSummary?,
    summaryLine: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    DiagnosticsSection(
        icon = Icons.Outlined.CheckCircle,
        title = "数据体检",
        summary = summaryLine,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        if (findings.isEmpty()) {
            SettingsPageSplitGroup(content = listOf({
                SettingsInfoRow(icon = Icons.Outlined.Info, title = "尚未运行", value = "切回本页或点上方标题刷新")
            }))
        } else {
            SettingsPageSplitGroup(content = findings.map { finding ->
                {
                    HealthRow(finding)
                }
            })
        }
        SettingsPageSplitGroup(content = listOf({
            SettingsInfoRow(
                icon = Icons.Outlined.Info,
                title = "一键修复",
                value = "本轮未接修复执行器：按每条建议手动处理",
            )
        }))
    }
}

@Composable
private fun HealthRow(finding: Finding) {
    val icon = when (finding.severity) {
        Severity.OK -> Icons.Outlined.CheckCircle
        Severity.INFO -> Icons.Outlined.Info
        Severity.WARN -> Icons.Outlined.HourglassEmpty
        Severity.ERROR -> Icons.Outlined.ErrorOutline
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = DiagnosticsFormat.severityLabel(finding.severity),
            tint = when (finding.severity) {
                Severity.ERROR -> MaterialTheme.colorScheme.error
                Severity.WARN -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(finding.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                finding.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            finding.fix?.let {
                Text(
                    "建议：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** ② 图片占用：四类体积条 + 总计 + 清理 30 天旧图。 */
@Composable
internal fun DiagnosticsStorageSection(
    items: List<StorageItem>,
    summaryLine: String,
    message: String?,
    busy: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCleanup: () -> Unit,
) {
    DiagnosticsSection(
        icon = Icons.Outlined.RestartAlt,
        title = "图片与缓存占用",
        summary = summaryLine,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        val usages = summarize(items)
        SettingsPageSplitGroup(content = usages.map { usage ->
            {
                UsageRow(usage.bucket.label, usage.bytes, usage.items, DiagnosticsFormat.bucketFraction(usage.bytes, totalBytes(items)))
            }
        })
        message?.let {
            SettingsPageSplitGroup(content = listOf({
                SettingsInfoRow(icon = Icons.Outlined.Info, title = "上次清理", value = it)
            }))
        }
        SettingsPageSplitGroup(content = listOf({
            SettingsActionRow(
                icon = Icons.Outlined.RestartAlt,
                title = "清理 30 天未访问的图片缓存",
                description = "只清 image_cache，不碰快照/壁纸/备份",
                isLoading = busy,
                showChevron = false,
                onClick = onCleanup,
            )
        }))
    }
}

@Composable
private fun UsageRow(label: String, bytes: Long, count: Int, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                formatBytes(bytes) + " · " + count + " 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** ③ 本地快照：最近 5 份 + 立即快照。 */
@Composable
internal fun DiagnosticsSnapshotSection(
    files: List<SnapshotFile>,
    summaryLine: String,
    message: String?,
    busy: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSnapshot: () -> Unit,
) {
    DiagnosticsSection(
        icon = Icons.Outlined.Sync,
        title = "本地快照",
        summary = summaryLine,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        val recent = files.sortedByDescending { it.createdAt }.take(5)
        if (recent.isEmpty()) {
            SettingsPageSplitGroup(content = listOf({
                SettingsInfoRow(icon = Icons.Outlined.Info, title = "还没有快照", value = "点下方立即创建；策略保留最近 5 份 + 最近 7 天每天 1 份")
            }))
        } else {
            SettingsPageSplitGroup(content = recent.map { file ->
                {
                    SettingsInfoRow(
                        icon = Icons.Outlined.Sync,
                        title = DiagnosticsFormat.snapshotTime(file.createdAt),
                        value = formatBytes(file.bytes),
                    )
                }
            })
        }
        message?.let {
            SettingsPageSplitGroup(content = listOf({
                SettingsInfoRow(icon = Icons.Outlined.Info, title = "上次操作", value = it)
            }))
        }
        SettingsPageSplitGroup(content = listOf({
            SettingsActionRow(
                icon = Icons.Outlined.Sync,
                title = "立即创建快照",
                description = "导出当前数据并按策略清理旧快照",
                isLoading = busy,
                showChevron = false,
                onClick = onSnapshot,
            )
        }))
    }
}

/** ④ 数据源自检：能力矩阵 + 2s 串行探测 + 失败摘要。 */
@Composable
internal fun DiagnosticsProbeSection(
    capabilities: List<SourceCapability>,
    cells: List<ProbeCell>,
    probes: List<UrlProbe>,
    summary: ProbeSummary?,
    summaryLine: String,
    busy: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRun: () -> Unit,
) {
    DiagnosticsSection(
        icon = Icons.Outlined.Refresh,
        title = "数据源自检",
        summary = summaryLine,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        if (capabilities.isEmpty()) {
            SettingsPageSplitGroup(content = listOf({
                SettingsInfoRow(icon = Icons.Outlined.Info, title = "没有已注册的数据源", value = "重启应用后重试")
            }))
        } else {
            SettingsPageSplitGroup(content = capabilities.map { source ->
                {
                    ProbeSourceRow(source, cells.filter { it.sourceId == source.sourceId })
                }
            })
        }
        if (probes.isNotEmpty()) {
            SettingsPageSplitGroup(content = probes.map { probe ->
                {
                    SettingsInfoRow(
                        icon = if (probe.error == null && (probe.statusCode ?: 500) < 400) Icons.Outlined.CheckCircle
                        else Icons.Outlined.ErrorOutline,
                        title = probe.statusCode?.toString() ?: "失败",
                        value = probe.url + " · " + probe.elapsedMs + "ms" +
                            (probe.error?.let { " · $it" } ?: ""),
                    )
                }
            })
        }
        SettingsPageSplitGroup(content = listOf({
            SettingsActionRow(
                icon = Icons.Outlined.Refresh,
                title = "运行探测",
                description = "逐源 HEAD，2s 超时、串行，不解析正文",
                isLoading = busy,
                showChevron = false,
                onClick = onRun,
            )
        }))
    }
}

@Composable
private fun ProbeSourceRow(source: SourceCapability, cells: List<ProbeCell>) {
    val supported = cells.count { it.status != ProbeStatus.UNSUPPORTED }
    val unsupported = cells.filter { it.status == ProbeStatus.UNSUPPORTED }.map { it.capability }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(source.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                "$supported/${cells.size} 项可用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (unsupported.isEmpty()) "全部能力已声明"
            else "不支持：" + unsupported.joinToString("、"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** ⑤ 更新状态：当前版本 + 上次检查 + 检查/打开发布页。 */
@Composable
internal fun DiagnosticsUpdateSection(
    currentVersion: String,
    statusLine: String,
    busy: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCheck: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    DiagnosticsSection(
        icon = Icons.Outlined.Info,
        title = "更新状态",
        summary = statusLine,
        expanded = expanded,
        onToggle = onToggle,
    ) {
        SettingsPageSplitGroup(content = listOf(
            {
                SettingsInfoRow(icon = Icons.Outlined.Info, title = "当前版本", value = "v$currentVersion")
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Refresh,
                    title = "检查更新",
                    description = "只查最新 tag 并比较版本，不下载、不安装",
                    isLoading = busy,
                    showChevron = false,
                    onClick = onCheck,
                )
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Info,
                    title = "打开发布页",
                    description = "浏览器查看完整 Release Notes",
                    showChevron = false,
                    onClick = onOpenReleases,
                )
            },
        ))
    }
}

/** 供探针选择 URL：只在 UI 侧做 id→公开常量映射（内核没有「按插件给 URL」的 API）。 */
internal fun probeUrlsFor(pluginIds: List<String>): List<String> = pluginIds.mapNotNull { id ->
    when (id) {
        "bangumi" -> com.otakup.niriko.data.remote.BangumiClient.baseUrl
        "anilist" -> "https://graphql.anilist.co"
        else -> null
    }
}
