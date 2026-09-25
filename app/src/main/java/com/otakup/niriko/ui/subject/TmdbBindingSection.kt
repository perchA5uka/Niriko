package com.otakup.niriko.ui.subject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.remote.rating.RatingCandidate
import com.otakup.niriko.data.remote.tmdb.dto.TmdbTvDetailDto
import com.otakup.niriko.ui.components.GlassSectionCard
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * TMDb 绑定区块（第 4 轮 C 重做）。
 *
 * ## 两个根本性改动
 *
 * 1. **永远有手动入口**：改造前 `if (binding == null && candidates.isEmpty() && detail == null) return`
 *    ——候选搜不到就整块消失，用户无从下手。现在只要 TMDb 覆盖此作品类型就渲染，
 *    并提供「按关键词搜索」与「粘贴 ID / 链接」两条手动路径。
 * 2. **差异化展示**：绑定后**不再重复 Bangumi 已有的字段**（名称 / 首播 / 集数 / 整剧评分
 *    Bangumi 全都有）。只保留 TMDb 独有且对本项目有用的信息，且**默认折叠**。
 *
 * | 保留（TMDb 独有） | 移除（Bangumi 已有） |
 * |---|---|
 * | networks / production_companies | 名称 / 原名 |
 * | status（是否还在播） | 首播日 |
 * | episode_run_time（单集时长） | 总集数 |
 * | external_ids（IMDb/TVDB/Wikidata/社交） | 整剧评分（已有权威评分卡） |
 * | 多语言海报入口 / 每集评分入口 | — |
 */
@Composable
fun TmdbBindingSection(
    binding: SubjectExternalIdEntity?,
    movieBinding: SubjectExternalIdEntity?,
    candidates: List<RatingCandidate>,
    pastedCandidate: RatingCandidate?,
    detail: TmdbTvDetailDto?,
    manualQuery: String,
    manualLoading: Boolean,
    manualMessage: String?,
    onBind: (RatingCandidate) -> Unit,
    onUnbind: () -> Unit,
    onSearchMore: (String) -> Unit,
    onPasteId: (String) -> Unit,
    onClearMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val bound = binding ?: movieBinding
    // 只要 TMDb 覆盖此类型就渲染（不再因「没有候选」而整块消失）
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "TMDb",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        GlassSectionCard(
            backdrop = glassBackdrop,
            isScrolling = isScrolling,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (bound != null) {
                    BoundHeader(
                        binding = bound,
                        detail = detail,
                        onUnbind = onUnbind,
                    )
                } else {
                    UnboundEntry(
                        candidates = candidates,
                        pastedCandidate = pastedCandidate,
                        manualQuery = manualQuery,
                        manualLoading = manualLoading,
                        manualMessage = manualMessage,
                        onBind = onBind,
                        onSearchMore = onSearchMore,
                        onPasteId = onPasteId,
                        onClearMessage = onClearMessage,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }
        }
    }
}

/** 已绑定：一行摘要 + 可展开的「TMDb 独有信息」。 */
@Composable
private fun BoundHeader(
    binding: SubjectExternalIdEntity,
    detail: TmdbTvDetailDto?,
    onUnbind: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isMovie = binding.provider == SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "已绑定 TMDb #${binding.externalId}" + if (isMovie) "（电影）" else "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // 摘要行只放「绑定的价值」，不放 Bangumi 已有的名称/首播/集数
            val summary = buildList {
                binding.subKey?.let { add("第 $it 季") }
                detail?.let { d ->
                    if (!isMovie) {
                        add("${d.seasons.count { it.seasonNumber > 0 }} 季可切换")
                        add("每集评分可用")
                    }
                    d.status?.let { add(statusLabel(it)) }
                }
                if (detail?.backdropPath != null) add("有剧照")
            }
            if (summary.isNotEmpty()) {
                Text(
                    text = summary.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "详情") }
        TextButton(onClick = onUnbind) { Text("解绑") }
    }

    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(top = 6.dp)) {
            if (isMovie) {
                Text(
                    "电影条目无季/集结构，因此不提供每集评分与剧照回填。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val d = detail ?: run {
                Text(
                    "详情暂未取到（检查网络或 TMDb 镜像地址）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            // —— 以下全部是 Bangumi 通常**没有**的字段 ——
            d.networks.mapNotNull { it.name }.takeIf { it.isNotEmpty() }?.let {
                TmdbInfoLine("播出电视台", it.joinToString("、"))
            }
            d.productionCompanies.mapNotNull { it.name }.takeIf { it.isNotEmpty() }?.let {
                TmdbInfoLine("制作公司", it.take(4).joinToString("、"))
            }
            d.status?.let { TmdbInfoLine("播出状态", statusLabel(it)) }
            d.episodeRunTime.firstOrNull()?.let { TmdbInfoLine("单集时长", "${it} 分钟") }
            d.originalLanguage?.let { TmdbInfoLine("原始语言", it) }

            val ext = d.externalIds
            if (ext != null) {
                TmdbInfoLine(
                    "外部 ID",
                    buildList {
                        ext.imdbId?.let { add("IMDb $it") }
                        ext.tvdbId?.let { add("TVDB $it") }
                        ext.wikidataId?.let { add("Wikidata $it") }
                    }.joinToString(" · ").ifBlank { "无" },
                )
            }
            TmdbInfoLine("每集评分/剧照", "已同步到上方「剧集」与「剧照」区块")
            TextButton(onClick = { }) {
                Text(
                    "提示：换封面时可用 TMDb 多语言海报候选",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun TmdbInfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 未绑定：候选 + 手动搜索 + 粘贴 ID，永远可操作。 */
@Composable
private fun UnboundEntry(
    candidates: List<RatingCandidate>,
    pastedCandidate: RatingCandidate?,
    manualQuery: String,
    manualLoading: Boolean,
    manualMessage: String?,
    onBind: (RatingCandidate) -> Unit,
    onSearchMore: (String) -> Unit,
    onPasteId: (String) -> Unit,
    onClearMessage: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searchInput by remember { mutableStateOf("") }
    var pasteInput by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }

    Text(
        text = "绑定 TMDb 后可获得**每集评分走势**、剧照与多语言海报候选。" +
            "只需在下面的候选里点一下，或手动搜索 / 粘贴 ID。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // —— 粘贴后待确认的候选（最高优先级展示） ——
    pastedCandidate?.let { candidate ->
        Spacer(Modifier.height(8.dp))
        Text(
            "已解析到以下条目，确认后绑定：",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        CandidateRow(candidate = candidate, onBind = onBind, highlight = true)
    }

    // —— 自动匹配候选 ——
    if (candidates.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "候选（按标题 + 年份匹配度排序，需你确认后才写入）：",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        candidates.take(6).forEach { candidate ->
            // 已展示为「待确认」的那个不重复列出
            if (candidate.externalId == pastedCandidate?.externalId &&
                candidate.provider == pastedCandidate?.provider
            ) {
                return@forEach
            }
            CandidateRow(candidate = candidate, onBind = onBind, highlight = false)
        }
    } else if (manualMessage == null && pastedCandidate == null) {
        Spacer(Modifier.height(6.dp))
        Text(
            "未自动匹配到标题相近的候选——可手动搜索，或直接粘贴 TMDb ID / 链接。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // —— 状态反馈（改造前是完全静默的） ——
    manualMessage?.let { message ->
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearMessage) { Text("知道了") }
        }
    }

    if (manualLoading) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("查询中…", style = MaterialTheme.typography.labelSmall)
        }
    }

    Spacer(Modifier.height(6.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { showSearch = !showSearch; if (showSearch) showPaste = false }) {
            Text("搜索 TMDb")
        }
        TextButton(onClick = { showPaste = !showPaste; if (showPaste) showSearch = false }) {
            Text("粘贴 ID / 链接")
        }
    }

    AnimatedVisibility(visible = showSearch) {
        Column {
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                label = { Text("关键词（原文名 / 英文名 / 罗马音）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchMore(searchInput) }),
            )
            TextButton(
                onClick = { onSearchMore(searchInput) },
                enabled = !manualLoading,
            ) { Text("搜索") }
        }
    }

    AnimatedVisibility(visible = showPaste) {
        Column {
            OutlinedTextField(
                value = pasteInput,
                onValueChange = { pasteInput = it },
                label = { Text("如 42509 或 themoviedb.org/tv/42509-...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onPasteId(pasteInput) }),
            )
            TextButton(
                onClick = { onPasteId(pasteInput) },
                enabled = !manualLoading,
            ) { Text("解析并预览") }
        }
    }

    Spacer(Modifier.height(2.dp))
    TextButton(onClick = onOpenSettings) { Text("TMDb 设置（API Key / 镜像地址）") }
    if (manualQuery.isNotBlank()) {
        Text(
            "上次搜索关键词：$manualQuery",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: RatingCandidate,
    onBind: (RatingCandidate) -> Unit,
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
                text = candidate.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            candidate.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (highlight) {
                    "TMDb ${providerLabel(candidate.provider)} #${candidate.externalId}"
                } else {
                    "匹配度 %.0f%%".format(candidate.confidence * 100f)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "绑定",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun providerLabel(provider: String): String =
    if (provider == SubjectExternalIdEntity.PROVIDER_TMDB_MOVIE) "电影" else "剧集"

/** TMDb 的 status 直译对中文用户不友好，映射成「是否还在播」。 */
private fun statusLabel(raw: String): String = when (raw.lowercase()) {
    "returning series" -> "连载中"
    "ended" -> "已完结"
    "canceled", "cancelled" -> "已砍"
    "in production" -> "制作中"
    "planned" -> "已计划"
    "pilot" -> "试播"
    "released" -> "已上映"
    "post production" -> "后期制作"
    "rumored" -> "传闻"
    else -> raw
}
