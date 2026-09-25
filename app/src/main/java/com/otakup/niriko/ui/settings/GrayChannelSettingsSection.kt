package com.otakup.niriko.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PowerOff
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.probe.GrayChannelRunner
import com.otakup.niriko.data.probe.ProbeConfig
import com.otakup.niriko.data.probe.ProbeResult
import com.otakup.niriko.data.probe.ProbeState
import com.otakup.niriko.data.settings.AppSettings
import kotlinx.coroutines.launch

/**
 * 「灰色通道与连通性自检」设置分区（第 4 轮 F）。
 *
 * ## 这个分区为什么存在
 *
 * 第 3 轮把豆瓣剧照做成「客户端实时搜索」并失败，根本原因是**没有任何测量手段**：
 * 既不知道 rexxar / frodo / HTML 三条链路各自返回什么状态码，也无法在本机复现
 * （开发机不能直连外网、「web_fetch」不能带自定义头）。于是只能盲猜路径与请求头。
 *
 * 这个分区把「盲猜」换成可观测的工程动作：点一下自检，**原样**显示每个端点的
 * HTTP 状态码、耗时、以及响应前 400 字节。看到的是「sec.douban.com」还是
 * 「invalid_request」，直接决定下一步改哪里。
 *
 * ## 合规
 *
 * 灰色通道（豆瓣 / 非官方接口）**默认关闭**；自检本身不受开关限制
 * （用户必须先能自检，才知道要不要开启）。豆瓣没有授权第三方抓取，
 * 这里明确提示「仅供个人查看，若将来分发建议移除」。
 */
@Composable
fun GrayChannelSettingsGroup(
    settings: AppSettings,
    onGrayChannelEnabled: (Boolean) -> Unit,
    onProbeHeaders: (String, String) -> Unit,
    onDoubanReferers: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<ProbeResult>?>(null) }
    var running by remember { mutableStateOf(false) }
    var expandedResult by remember { mutableStateOf<Int?>(null) }
    var showHeaderDialog by remember { mutableStateOf(false) }
    var showRefererDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsGroupTitle("灰色通道")

        SettingsSplitGroup(content = listOf(
            {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Security,
                    title = "启用灰色通道",
                    description = "非官方 / 未文档化接口（豆瓣剧照等）。默认关闭。",
                    checked = settings.grayChannelEnabled,
                    onCheckedChange = onGrayChannelEnabled,
                )
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Http,
                    title = "请求头（User-Agent / Cookie）",
                    description = if (settings.probeUserAgent.isBlank() && settings.probeCookie.isBlank()) {
                        "使用默认值 · 失效时可在此修改，无需等发版"
                    } else {
                        "已自定义 UA" + if (settings.probeCookie.isNotBlank()) " + Cookie" else ""
                    },
                    onClick = { showHeaderDialog = true },
                )
            },
            {
                SettingsActionRow(
                    icon = Icons.Outlined.Link,
                    title = "豆瓣 Referer",
                    description = "图片 ${settings.doubanImageReferer} · API ${settings.doubanApiReferer}",
                    onClick = { showRefererDialog = true },
                )
            },
            {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                running = true
                                results = null
                                scope.launch {
                                    val config = ProbeConfig(
                                        userAgent = settings.probeUserAgent,
                                        cookie = settings.probeCookie,
                                        doubanImageReferer = settings.doubanImageReferer,
                                        doubanApiReferer = settings.doubanApiReferer,
                                        grayChannelEnabled = settings.grayChannelEnabled,
                                    )
                                    results = runCatching { GrayChannelRunner.runAll(config) }
                                        .getOrDefault(emptyList())
                                    running = false
                                }
                            },
                            enabled = !running,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (running) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("自检中…")
                            } else {
                                Text("运行连通性自检")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "依次请求每个端点，原样显示 HTTP 状态码与响应前 400 字节。" +
                            "看不懂没关系——把结果发给我即可定位问题。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "⚠ 豆瓣没有授权第三方抓取。该通道仅供个人查看；若将来分发建议移除。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
        ))

        results?.let { list ->
            Spacer(Modifier.height(8.dp))
            SettingsGroupTitle("自检结果（${list.count { it.state == ProbeState.OK }}/${list.size} 可用）")
            SettingsSplitGroup(content = listOf {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    list.forEachIndexed { index, result ->
                        ProbeResultRow(
                            result = result,
                            expanded = expandedResult == index,
                            onToggle = { expandedResult = if (expandedResult == index) null else index },
                        )
                    }
                }
            })
        }
    }

    if (showHeaderDialog) {
        var ua by remember { mutableStateOf(settings.probeUserAgent) }
        var cookie by remember { mutableStateOf(settings.probeCookie) }
        AlertDialog(
            onDismissRequest = { showHeaderDialog = false },
            title = { Text("灰色通道请求头") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "留空则使用探针内置的默认 UA。若某些站点开始拒绝请求，" +
                            "在这里改成它当前接受的 UA / Cookie 即可，不必等发版。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = ua,
                        onValueChange = { ua = it },
                        label = { Text("User-Agent（留空 = 默认）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cookie,
                        onValueChange = { cookie = it },
                        label = { Text("Cookie（留空 = 不带）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onProbeHeaders(ua, cookie)
                    showHeaderDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showHeaderDialog = false }) { Text("取消") } },
        )
    }

    if (showRefererDialog) {
        var imageReferer by remember { mutableStateOf(settings.doubanImageReferer) }
        var apiReferer by remember { mutableStateOf(settings.doubanApiReferer) }
        AlertDialog(
            onDismissRequest = { showRefererDialog = false },
            title = { Text("豆瓣 Referer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "图片 Referer 默认是 https://douban.com —— 这是 Bangumi-master 的实测值：" +
                            "带 path 的 movie.douban.com/ 反而会被图床拒。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = imageReferer,
                        onValueChange = { imageReferer = it },
                        label = { Text("图片 Referer") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = apiReferer,
                        onValueChange = { apiReferer = it },
                        label = { Text("API Referer 前缀") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDoubanReferers(imageReferer, apiReferer)
                    showRefererDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRefererDialog = false }) { Text("取消") } },
        )
    }
}

/** 自检结果图标：可用 / 被拦 / 不可达 / 跳过。 */
private fun probeResultIcon(state: ProbeState): ImageVector = when (state) {
    ProbeState.OK -> Icons.Outlined.CheckCircle
    ProbeState.BLOCKED -> Icons.Outlined.ErrorOutline
    ProbeState.UNREACHABLE -> Icons.Outlined.HourglassEmpty
    ProbeState.SKIPPED -> Icons.Outlined.PowerOff
}

/** 单条自检结果：一行摘要，点开看响应原文。 */
@Composable
private fun ProbeResultRow(
    result: ProbeResult,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val color = when (result.state) {
        ProbeState.OK -> MaterialTheme.colorScheme.primary
        ProbeState.BLOCKED -> MaterialTheme.colorScheme.error
        ProbeState.UNREACHABLE -> MaterialTheme.colorScheme.error
        ProbeState.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = probeResultIcon(result.state),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    result.endpointName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    result.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
                if (result.finalUrl != null) {
                    Text(
                        "→ ${result.finalUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(if (expanded) "收起" else "响应", style = MaterialTheme.typography.labelSmall, color = color)
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Text(
                    result.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp)
                        .height(180.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(
                            result.bodyPreview.ifBlank { "(空响应)" },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                result.contentType?.let {
                    Text(
                        "Content-Type: $it · 共 ${result.bodyLength} 字节",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
