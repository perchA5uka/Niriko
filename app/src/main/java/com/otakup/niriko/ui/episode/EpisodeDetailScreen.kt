package com.otakup.niriko.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.remote.rating.ExternalRating
import com.otakup.niriko.ui.common.ImageViewer
import com.otakup.niriko.ui.subject.formatEpisodeNumber
import com.otakup.niriko.viewmodel.EpisodeDetailViewModel
import kotlinx.coroutines.delay

/**
 * 单集二级页（阶段 B）。
 *
 * 解决的问题：此前「每集」只有一个分数（还是写死的 8 分），没有名字、剧照、简介与评价框。
 * 本页把单集当作一等公民：剧照（可全屏）、简介、我的评分、我的评价、该集权威评分，
 * 并支持「看到这里」直接写回收藏进度、上一集/下一集页内切换（不压栈）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeDetailScreen(
    viewModel: EpisodeDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    var viewerUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // —— 顶栏：返回 + 集号 + 上一集/下一集 ——
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = state.episode?.let {
                        "第 " + formatEpisodeNumber(it.ep.takeIf { e -> e > 0 } ?: it.sort) + " 集"
                    } ?: "单集详情",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { state.prevEpId?.let(viewModel::switchEpisode) },
                    enabled = state.prevEpId != null,
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一集")
                }
                IconButton(
                    onClick = { state.nextEpId?.let(viewModel::switchEpisode) },
                    enabled = state.nextEpId != null,
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一集")
                }
            }

            val episode = state.episode
            if (state.isLoading && episode == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            if (episode == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到该集（可能剧集数据尚未同步）")
                }
                return@Column
            }

            EpisodeDetailBody(
                state = state,
                onSaveScore = viewModel::saveMyScore,
                onSaveComment = viewModel::saveMyComment,
                onToggleRewatch = viewModel::toggleRewatch,
                onMarkWatched = viewModel::markWatched,
                onOpenImage = { viewerUrl = it },
                onOpenBangumi = {
                    runCatching { uriHandler.openUri("https://bgm.tv/ep/" + episode.epId) }
                },
            )
        }
    }

    viewerUrl?.let { url ->
        ImageViewer(
            urls = listOf(url),
            initialIndex = 0,
            title = state.episode?.nameCn ?: state.episode?.name,
            onDismiss = { viewerUrl = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeDetailBody(
    state: com.otakup.niriko.viewmodel.EpisodeDetailUiState,
    onSaveScore: (Float?) -> Unit,
    onSaveComment: (String) -> Unit,
    onToggleRewatch: () -> Unit,
    onMarkWatched: () -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenBangumi: () -> Unit,
) {
    val episode = state.episode ?: return
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        // —— 剧照 ——
        val still = episode.stillUrl
        if (still != null) {
            AsyncImage(
                model = still,
                contentDescription = episode.nameCn ?: episode.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenImage(still) },
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Text(
                "本集暂无剧照（可绑定 TMDb 或在设置里开启豆瓣剧照）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        // —— 标题 + 元信息 ——
        val title = episode.nameCn?.takeIf { it.isNotBlank() } ?: episode.name
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (episode.nameCn?.isNotBlank() == true && episode.name.isNotBlank() && episode.name != episode.nameCn) {
            Text(
                episode.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val meta = buildList {
            episode.airdate?.let { add(it) }
            episode.duration?.takeIf { it.isNotBlank() }?.let { add(it) }
            episode.status?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (episode.comment > 0) add(episode.comment.toString() + " 条讨论")
            state.subjectTitle?.let { add(it) }
        }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(18.dp))

        // —— 我的评分 ——
        Text("我的评分", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        var scoreState by remember(episode.epId) {
            mutableFloatStateOf(state.myScore ?: 0f)
        }
        LaunchedEffect(state.myScore, episode.epId) {
            scoreState = state.myScore ?: 0f
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = scoreState,
                onValueChange = { scoreState = it },
                onValueChangeFinished = {
                    onSaveScore(scoreState.takeIf { s -> s > 0f })
                },
                valueRange = 0f..10f,
                steps = 19,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (scoreState > 0f) "%.1f".format(scoreState) else "未评分",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "拖动滑块到 0 即为清除评分",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        // —— 我的评价（防抖自动保存） ——
        Text("我的评价", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        var commentState by remember(episode.epId) { mutableStateOf(state.myComment) }
        LaunchedEffect(state.myComment, episode.epId) {
            if (state.myComment != commentState) commentState = state.myComment
        }
        // 防抖：停止输入 800ms 后才落库，避免每敲一个字写一次数据库
        LaunchedEffect(commentState, episode.epId) {
            if (commentState != state.myComment) {
                delay(800)
                onSaveComment(commentState)
            }
        }
        OutlinedTextField(
            value = commentState,
            onValueChange = { if (it.length <= 2000) commentState = it },
            placeholder = { Text("写下你对这一集的看法…") },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            commentState.length.toString() + " / 2000",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
        Spacer(Modifier.height(18.dp))

        // —— 该集的权威评分 ——
        if (state.ratings.isNotEmpty()) {
            Text("该集评分", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.ratings.values.forEach { rating ->
                    val label = if (rating.sourceId == ExternalRating.SOURCE_IMDB) "IMDb" else "TMDb"
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                        Text(
                            rating.score?.let { "%.1f".format(it) } ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        rating.voteCount?.let {
                            Text(
                                it.toString() + " 票",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // —— 本集简介 ——
        val desc = episode.desc
        if (!desc.isNullOrBlank()) {
            Text("本集简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
        }

        // —— 快捷操作 ——
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onMarkWatched,
                label = { Text("看到这里", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                },
            )
            FilterChip(
                selected = state.rewatch,
                onClick = onToggleRewatch,
                label = { Text("二刷", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    if (state.rewatch) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    } else null
                },
            )
            AssistChip(
                onClick = onOpenBangumi,
                label = { Text("Bangumi 章节页", style = MaterialTheme.typography.labelSmall) },
            )
        }
        if (!state.isInCollection) {
            Spacer(Modifier.height(6.dp))
            Text(
                "该作品尚未收藏，「看到这里」无法写回进度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
