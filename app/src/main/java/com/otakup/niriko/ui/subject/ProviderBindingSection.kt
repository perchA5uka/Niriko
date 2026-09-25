package com.otakup.niriko.ui.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.match.MatchCandidate
import com.otakup.niriko.ui.components.GlassSectionCard
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 统一的「外部数据源绑定」区块（第 4 轮 D）。
 *
 * ## 为什么要有它
 *
 * 改造前 TMDb / VNDB / AniList **各写一套绑定 UI**，而且彼此不一致：
 * VNDB/AniList 有「搜索更多」但只在未命中时出现，TMDb 连手动入口都没有
 * （候选为空就整块不渲染）。三处代码重复，三处都缺东西。
 *
 * 本组件把绑定交互收敛成一套，由参数决定差异：
 * - [title] / [subtitle]：区块标题与说明
 * - [bindingSummary]：已绑定时的一行摘要（**只放该源独有的信息**，不重复 Bangumi 字段）
 * - [detailContent]：已绑定时可展开的差异化内容（由各源自己提供）
 * - [candidates] + [matchReasons]：候选列表，带「为什么匹配它」的理由
 * - 手动兜底：关键词搜索 + 粘贴 ID/链接，**永远可用**
 *
 * ## 保守匹配的边界
 *
 * 本组件**不写库**——所有绑定都通过 [onBind] 交给调用方，由用户点击触发。
 */
@Composable
fun ProviderBindingSection(
    title: String,
    /** 未绑定时显示的说明（写清「绑定能带来什么」）。 */
    unboundHint: String,
    /** 已绑定时的摘要行（如「TMDb #42509 · 第 1 季 · 每集评分可用」）。 */
    bindingSummary: String?,
    candidates: List<MatchCandidate>,
    /** externalId → 匹配理由（可解释性）。 */
    matchReasons: Map<String, List<String>>,
    onBind: (MatchCandidate) -> Unit,
    onUnbind: () -> Unit,
    onSearch: (String) -> Unit,
    onPasteId: (String) -> Unit,
    /** 粘贴/搜索的一行反馈。 */
    manualMessage: String?,
    onClearMessage: () -> Unit,
    loading: Boolean,
    /** 粘贴输入框的示例提示（不同源的 id 形态不同）。 */
    pasteHint: String,
    modifier: Modifier = Modifier,
    /** 一键跳设置（配置 key / 镜像地址）。 */
    onOpenSettings: (() -> Unit)? = null,
    /** 已绑定时可展开的差异化内容。 */
    detailContent: (@Composable () -> Unit)? = null,
    /** 已绑定时的「待确认」候选（例如刚粘贴解析出来的那一条）。 */
    pastedCandidate: MatchCandidate? = null,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
) {
    val bound = bindingSummary != null

    GlassSectionCard(
        backdrop = glassBackdrop,
        isScrolling = isScrolling,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (bound) {
                    TextButton(onClick = onUnbind) { Text("解绑") }
                }
            }

            if (bound) {
                var expanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bindingSummary.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (detailContent != null) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "收起" else "详情")
                        }
                    }
                }
                if (detailContent != null) {
                    AnimatedVisibility(visible = expanded) { detailContent() }
                }
                return@Column
            }

            // —— 未绑定：说明 + 候选 + 手动入口（永远可用） ——
            Text(
                unboundHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            pastedCandidate?.let { candidate ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "已解析到以下条目，确认后绑定：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                CandidateRow(candidate = candidate, reasons = matchReasons[candidate.externalId], onBind = onBind, highlight = true)
            }

            if (candidates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "候选（按标题 + 年份 + 集数匹配度排序，需你确认后才写入）：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                candidates.take(6).forEach { candidate ->
                    if (candidate.externalId == pastedCandidate?.externalId) return@forEach
                    CandidateRow(
                        candidate = candidate,
                        reasons = matchReasons[candidate.externalId],
                        onBind = onBind,
                        highlight = false,
                    )
                }
            } else if (manualMessage == null && pastedCandidate == null && !loading) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "未自动匹配到相近条目——可在下面搜索关键词，或直接粘贴 id / 链接。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            manualMessage?.let { message ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearMessage) { Text("知道了") }
                }
            }

            if (loading) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("查询中…", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(6.dp))
            ManualEntry(
                onSearch = onSearch,
                onPasteId = onPasteId,
                loading = loading,
                pasteHint = pasteHint,
            )

            onOpenSettings?.let { open ->
                TextButton(onClick = open) { Text("打开数据源设置") }
            }
        }
    }
}

/** 关键词搜索 + 粘贴 ID 两条手动兜底路径。 */
@Composable
private fun ManualEntry(
    onSearch: (String) -> Unit,
    onPasteId: (String) -> Unit,
    loading: Boolean,
    pasteHint: String,
) {
    var showSearch by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var pasteInput by remember { mutableStateOf("") }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { showSearch = !showSearch; if (showSearch) showPaste = false }) {
            Text("搜索", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = { showPaste = !showPaste; if (showPaste) showSearch = false }) {
            Text("粘贴 ID / 链接", style = MaterialTheme.typography.labelMedium)
        }
    }

    AnimatedVisibility(visible = showSearch) {
        Column {
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                label = { Text("关键词（原文名 / 英文名 / 罗马音）", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(searchInput) }),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onSearch(searchInput) }, enabled = !loading) { Text("搜索") }
        }
    }

    AnimatedVisibility(visible = showPaste) {
        Column {
            OutlinedTextField(
                value = pasteInput,
                onValueChange = { pasteInput = it },
                label = { Text(pasteHint, style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onPasteId(pasteInput) }),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onPasteId(pasteInput) }, enabled = !loading) { Text("解析并预览") }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: MatchCandidate,
    reasons: List<String>?,
    onBind: (MatchCandidate) -> Unit,
    highlight: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onBind(candidate) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidate.imageUrl != null) {
            AsyncImage(
                model = candidate.imageUrl,
                contentDescription = candidate.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 40.dp, height = 56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                candidate.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            candidate.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (highlight) {
                    "已指定 #${candidate.externalId}"
                } else {
                    "匹配度 %.0f%%".format(candidate.confidence * 100f)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // 匹配理由：让用户能自己判断「为什么系统认为它是这条」
            reasons?.takeIf { it.isNotEmpty() }?.let { list ->
                Text(
                    list.take(2).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "绑定",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
