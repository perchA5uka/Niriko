package com.otakup.niriko.ui.person

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.remote.PersonDetailInfo
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.components.LoadingContent
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.viewmodel.PersonDetailViewModel

/** 人物（声优/导演等）详情页。 */
@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    onBack: () -> Unit,
    onSubjectClick: (Long) -> Unit = {},
    onCharacterClick: (Long) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("人物详情", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                // 收藏按钮（需 Bangumi token）
                if (state.detail != null) {
                    OutlinedButton(
                        onClick = viewModel::collect,
                        enabled = !state.isCollecting,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(if (state.isCollecting) "收藏中…" else if (state.isCollected) "已收藏" else "收藏")
                    }
                }
            }
            when {
                state.isLoading -> LoadingContent()
                state.error != null -> ErrorContent(message = state.error ?: "", onRetry = viewModel::retry)
                state.detail != null -> {
                    val detail = state.detail!!
                    PersonDetailBody(
                        detail = detail,
                        subjects = state.subjects,
                        characters = state.characters,
                        onSubjectClick = onSubjectClick,
                        onCharacterClick = onCharacterClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                else -> ErrorContent(message = "无法加载人物信息", onRetry = viewModel::retry)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun PersonDetailBody(
    detail: PersonDetailInfo,
    subjects: List<PersonSubjectInfo>,
    characters: List<CharacterInfo>,
    onSubjectClick: (Long) -> Unit,
    onCharacterClick: (Long) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // 头像 + 名称
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 共享元素：与作品详情页制作人员卡头像配对（key 一致）
            val avatarModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("person_avatar_${detail.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            } else {
                Modifier
            }
            AsyncImage(
                model = detail.imageUrl,
                contentDescription = detail.nameCn ?: detail.name,
                modifier = Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).then(avatarModifier),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(detail.nameCn ?: detail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                detail.nameCn?.let { if (it != detail.name) Text(detail.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // 职业标签
        if (detail.career.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                detail.career.forEach { career ->
                    FilterChip(selected = false, onClick = {}, label = { Text(careerLabel(career), style = MaterialTheme.typography.labelSmall) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 简介（可折叠）
        if (!detail.summary.isNullOrBlank()) {
            var summaryExpanded by remember { mutableStateOf(false) }
            Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                detail.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (summaryExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            // 简介较长时显示展开/收起
            if (detail.summary.length > 60) {
                Text(
                    text = if (summaryExpanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { summaryExpanded = !summaryExpanded },
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // 参与作品（按类型分组，每组横向滑动）
        if (subjects.isNotEmpty()) {
            Text("参与作品", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // 按 type 分组，组顺序固定：动画→书籍→音乐→游戏→三次元→其他
            val typeOrder = listOf(2, 1, 3, 4, 6)
            val grouped = subjects.groupBy { it.type }
            typeOrder.forEach { type ->
                val items = grouped[type]?.distinctBy { it.subjectId } ?: return@forEach
                if (items.isEmpty()) return@forEach
                Text(
                    typeLabel(type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                ) {
                    items(items, key = { it.subjectId }) { subject ->
                        PersonSubjectCard(
                            subject = subject,
                            onClick = { onSubjectClick(subject.subjectId) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }
            }
            // 其他类型（不在固定顺序里的）
            grouped.filterKeys { it !in typeOrder }.forEach { (type, items) ->
                val distinctItems = items.distinctBy { it.subjectId }
                Text(typeLabel(type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(distinctItems, key = { it.subjectId }) { subject ->
                        PersonSubjectCard(
                            subject = subject,
                            onClick = { onSubjectClick(subject.subjectId) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // 声优：演绎角色（横向滑动，去重避免 LazyRow 重复 key 崩溃）
        if (characters.isNotEmpty()) {
            Text("配音角色", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val distinctCharacters = characters.distinctBy { it.id }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(distinctCharacters, key = { it.id }) { character ->
                    CharacterCard(character = character, onClick = { onCharacterClick(character.id) })
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: CharacterInfo,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(100.dp)
            .appleGlassCard(shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp),
        ) {
            AsyncImage(
                model = character.imageUrl,
                contentDescription = character.nameCn ?: character.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                character.nameCn ?: character.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            character.roleName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private fun careerLabel(career: String): String = when (career) {
    "artist" -> "艺术家"
    "seiyu" -> "声优"
    "director" -> "导演"
    "writer" -> "脚本"
    "illustrator" -> "插画师"
    "composer" -> "作曲家"
    "producer" -> "制作人"
    else -> career
}

private fun typeLabel(type: Int): String = when (type) {
    2 -> "动画"
    1 -> "书籍"
    4 -> "游戏"
    3 -> "音乐"
    6 -> "三次元"
    else -> "其他"
}
