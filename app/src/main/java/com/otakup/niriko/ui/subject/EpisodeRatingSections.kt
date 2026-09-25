package com.otakup.niriko.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.calculator.EpisodeRatingAnalyzer
import com.otakup.niriko.data.local.entity.EpisodeRatingEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.data.repository.EpisodeRatingRepository
import com.otakup.niriko.ui.components.GlassSectionCard
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 剧集 / 章节区块（对齐 Bangumi-master 的 `Ep` 区块）。
 *
 * 这是项目此前完全缺失的一块：`episodes` 数据一直只喂给统计页与进度选择器，
 * 动画详情页根本没有剧集列表。本区块同时承担三件事：
 * 1. 列出每集（集号 / 标题 / 播出日 / 时长 / 讨论数）；
 * 2. 承载**每集评分**角标 + 剧照缩略图；
 * 3. 提供「看到这里」——一键把观看进度写到收藏（复用既有 collection 字段）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeRatingSection(
    episodes: List<EpisodeInfo>,
    ratings: Map<Long, Map<String, EpisodeRatingEntity>>,
    myRatings: Map<Long, Float>,
    stills: Map<Long, String>,
    loadState: EpisodeRatingRepository.TmdbEpisodeLoad?,
    imdbLoading: Boolean,
    hasTmdbBinding: Boolean,
    imdbAvailable: Boolean,
    /**
     * IMDb 逐集入口的逐项前置条件（第 4 轮 H）。
     *
     * 改造前只有 [imdbAvailable] 一个布尔：任一条件不满足就整块隐藏且不说明原因。
     * 现在无论是否可用都渲染状态行，不可用时逐条列出「缺什么」并给一键跳设置。
     */
    imdbEntry: EpisodeRatingRepository.ImdbEntryStatus? = null,
    onLoadImdb: () -> Unit,
    onOpenSettings: () -> Unit = {},
    /** 每次通过 OMDb 兜底加载后带回来的统计（S/E 兜底集数）。 */
    imdbLoadResult: EpisodeRatingRepository.ImdbEpisodeLoad? = null,
    onMarkWatched: (Double) -> Unit,
    onOpenEpisodeDetail: (Long) -> Unit = {},
    onOpenImage: (List<String>, Int, String) -> Unit,
    watchedEpisodes: Int?,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mainEpisodes = remember(episodes) { episodes.filter { it.type == 0 }.sortedBy { it.sort } }
    if (mainEpisodes.isEmpty()) return

    var source by remember { mutableStateOf(RatingSourceChoice.TMDB) }
    var showMovingAverage by remember { mutableStateOf(true) }
    var minVotesFilter by remember { mutableStateOf(0) }
    var expandedEpId by remember { mutableStateOf<Long?>(null) }

    val sourceId = when (source) {
        RatingSourceChoice.TMDB -> ExternalRating.SOURCE_TMDB
        RatingSourceChoice.IMDB -> ExternalRating.SOURCE_IMDB
        RatingSourceChoice.MINE -> MINE_SOURCE
    }
    val rawPoints = remember(mainEpisodes, ratings, myRatings, source) {
        when (source) {
            RatingSourceChoice.MINE -> mainEpisodes.mapNotNull { ep ->
                myRatings[ep.id]?.let { score ->
                    EpisodeRatingAnalyzer.RatingPoint(
                        epId = ep.id, ep = ep.ep, label = ep.nameCn ?: ep.name,
                        score = score, votes = null, sourceId = MINE_SOURCE,
                    )
                }
            }
            else -> mainEpisodes.mapNotNull { ep ->
                val r = ratings[ep.id]?.get(sourceId) ?: return@mapNotNull null
                val score = r.score ?: return@mapNotNull null
                EpisodeRatingAnalyzer.RatingPoint(
                    epId = ep.id, ep = ep.ep, label = ep.nameCn ?: ep.name,
                    score = score, votes = r.voteCount, sourceId = sourceId,
                )
            }
        }
    }
    val points = remember(rawPoints, minVotesFilter) {
        if (minVotesFilter <= 0) rawPoints
        else EpisodeRatingAnalyzer.filterLowVotes(rawPoints, minVotesFilter).first
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "剧集",
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
                // —— 评分走势 ——
                if (points.size >= 2) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RatingSourceChoice.entries.forEach { choice ->
                            FilterChip(
                                selected = source == choice,
                                onClick = { source = choice },
                                label = { Text(choice.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    EpisodeRatingChart(
                        points = points,
                        showMovingAverage = showMovingAverage,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilterChip(
                            selected = showMovingAverage,
                            onClick = { showMovingAverage = !showMovingAverage },
                            label = { Text("移动平均", style = MaterialTheme.typography.labelSmall) },
                        )
                        listOf(0, 10, 50).forEach { threshold ->
                            FilterChip(
                                selected = minVotesFilter == threshold,
                                onClick = { minVotesFilter = threshold },
                                label = {
                                    Text(
                                        if (threshold == 0) "全部票数" else "≥$threshold 票",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                } else {
                    Text(
                        text = episodeRatingEmptyHint(loadState, hasTmdbBinding),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // —— 对齐可解释性 + IMDb 触发 ——
                loadState?.takeIf { it.isOk }?.let { state ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append("TMDb 第 ${state.seasonNumber ?: 1} 季：")
                            append("${state.matchedCount} 集已对齐")
                            if (state.unmatchedBangumi > 0) append("，${state.unmatchedBangumi} 集未匹配")
                            if (state.withScoreCount != state.matchedCount) {
                                append("（${state.withScoreCount} 集有评分）")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasTmdbBinding && imdbAvailable) {
                    Spacer(Modifier.height(8.dp))
                    AssistChip(
                        onClick = { if (!imdbLoading) onLoadImdb() },
                        label = {
                            Text(
                                if (imdbLoading) "正在加载 IMDb 评分…" else "加载 IMDb 逐集评分",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        leadingIcon = {
                            if (imdbLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else null
                        },
                    )
                }

                // —— IMDb 逐集状态（第 4 轮 H：不再静默隐藏） ——
                ImdbEntryStatusRow(
                    status = imdbEntry,
                    loadResult = imdbLoadResult,
                    onOpenSettings = onOpenSettings,
                )

                Spacer(Modifier.height(12.dp))

                // —— 每集列表 ——
                mainEpisodes.forEachIndexed { index, ep ->
                    EpisodeRow(
                        index = index,
                        ep = ep,
                        tmdb = ratings[ep.id]?.get(ExternalRating.SOURCE_TMDB),
                        imdb = ratings[ep.id]?.get(ExternalRating.SOURCE_IMDB),
                        myRating = myRatings[ep.id],
                        stillUrl = stills[ep.id],
                        isWatched = watchedEpisodes != null && ep.sort <= watchedEpisodes,
                        expanded = expandedEpId == ep.id,
                        onToggleExpand = {
                            expandedEpId = if (expandedEpId == ep.id) null else ep.id
                        },
                        onMarkWatched = { onMarkWatched(ep.sort) },
                        onOpenDetail = { onOpenEpisodeDetail(ep.id) },
                        onOpenStill = {
                            val gallery = stills.values.toList()
                            val urls = gallery.ifEmpty { listOfNotNull(stills[ep.id]) }
                            if (urls.isNotEmpty()) {
                                onOpenImage(urls, urls.indexOf(stills[ep.id]).coerceAtLeast(0), ep.nameCn ?: ep.name)
                            }
                        },
                    )
                }

                if (episodes.any { it.type != 0 }) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "另有 ${episodes.count { it.type != 0 }} 个特别篇 / OP / ED（不参与评分曲线）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val MINE_SOURCE = "mine"

private enum class RatingSourceChoice(val label: String) {
    TMDB("TMDb"),
    IMDB("IMDb"),
    MINE("我的"),
}

/**
 * IMDb 逐集入口的状态行（第 4 轮 H）。
 *
 * 三种形态：
 * - **可用**：只显示一行「IMDb 逐集：可用」，不占版面；
 * - **不可用**：逐条列出缺什么，并提供「去设置」按钮（改造前是完全不显示、不说原因）；
 * - **已加载**：显示本次加载了多少集、其中多少集走了 OMDb 兜底。
 */
@Composable
private fun ImdbEntryStatusRow(
    status: EpisodeRatingRepository.ImdbEntryStatus?,
    loadResult: EpisodeRatingRepository.ImdbEpisodeLoad?,
    onOpenSettings: () -> Unit,
) {
    if (status == null) return

    val resultText = loadResult?.takeIf { it.state == EpisodeRatingRepository.ImdbEpisodeLoad.State.OK }
        ?.let { load ->
            buildString {
                append("已加载 ${load.loaded}/${load.total} 集")
                load.seasonNumber?.let { append("（第 $it 季）") }
                if (load.viaOmdbFallback > 0) {
                    append("，其中 ${load.viaOmdbFallback} 集走 OMDb 季集兜底")
                }
            }
        }

    Spacer(Modifier.height(6.dp))
    if (status.enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = resultText ?: "IMDb 逐集：可用",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column {
        Text(
            text = "IMDb 逐集不可用，还缺：",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        status.missing.forEach { item ->
            Text(
                text = "· $item",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        TextButton(onClick = onOpenSettings) {
            Text("去设置", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EpisodeRow(
    index: Int,
    ep: EpisodeInfo,
    tmdb: EpisodeRatingEntity?,
    imdb: EpisodeRatingEntity?,
    myRating: Float?,
    stillUrl: String?,
    isWatched: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onMarkWatched: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenStill: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleExpand() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 集号
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isWatched) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatEpisodeNumber(ep.ep.takeIf { it > 0 } ?: ep.sort),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ep.nameCn?.takeIf { it.isNotBlank() } ?: ep.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    ep.airdate?.let { add(it) }
                    ep.duration?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (ep.comment > 0) add("${ep.comment} 讨论")
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            tmdb?.score?.let { RatingBadge("TMDb", it) }
            imdb?.score?.let { Spacer(Modifier.width(4.dp)); RatingBadge("IMDb", it) }
            myRating?.let { Spacer(Modifier.width(4.dp)); RatingBadge("我的", it, highlight = true) }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Row {
                if (stillUrl != null) {
                    AsyncImage(
                        model = stillUrl,
                        contentDescription = ep.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(132.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenStill() },
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    ep.desc?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = onMarkWatched,
                            label = { Text("看到这里", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                if (isWatched) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                } else {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        // 修复 BUG-13：原来这里的「标记 8 分」是写死的假评分输入。
                        // 真正的评分/评价现在在单集二级页里。
                        AssistChip(
                            onClick = onOpenDetail,
                            label = { Text("单集详情 ›", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingBadge(label: String, score: Float, highlight: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlight) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = "%.1f".format(score),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 剧照 / 截图横滑区（TMDb 背景图 + 每集剧照 + Steam 截图由调用方合并后传入）。 */
@Composable
fun ThumbsSection(
    items: List<ThumbItem>,
    onOpen: (List<String>, Int) -> Unit,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "剧照 / 截图",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "全部 ${items.size} 张",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    onOpen(items.map { it.url }, 0)
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(items) { index, item ->
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpen(items.map { it.url }, index) },
                ) {
                    val imageContext = androidx.compose.ui.platform.LocalContext.current
                    val itemRequest = remember(item.url, item.referer, imageContext) {
                        coil.request.ImageRequest.Builder(imageContext)
                            .data(item.url)
                            .apply {
                                item.referer?.let { value ->
                                    headers(okhttp3.Headers.Builder().add("Referer", value).build())
                                }
                            }
                            .build()
                    }
                    AsyncImage(
                        model = itemRequest,
                        contentDescription = item.caption,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(124.dp),
                    )
                    item.caption?.let { caption ->
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 剧照条目。 */
data class ThumbItem(
    val url: String,
    val caption: String? = null,
    val source: String = "tmdb",
    /**
     * 加载该图所需的 Referer（豆瓣图床有防盗链）。
     * 其它来源为 null，Coil 用默认请求即可。
     */
    val referer: String? = null,
)

/** 空态提示：把"为什么没有曲线"说清楚，而不是静默留白。 */
private fun episodeRatingEmptyHint(
    loadState: EpisodeRatingRepository.TmdbEpisodeLoad?,
    hasTmdbBinding: Boolean,
): String = when {
    !hasTmdbBinding ->
        "尚未绑定 TMDb 条目，无法获取每集评分。绑定后这里会显示整季评分走势曲线。"
    loadState == null ->
        "正在获取 TMDb 每集评分…"
    loadState.state == EpisodeRatingRepository.TmdbEpisodeLoad.State.FAILED ->
        "TMDb 请求失败或被限流，稍后重试即可。"
    loadState.state == EpisodeRatingRepository.TmdbEpisodeLoad.State.UNAVAILABLE ->
        "未配置 TMDb API Key（设置 → 权威数据源）。"
    else ->
        "TMDb 该季暂无逐集评分（冷门番常见）。"
}
