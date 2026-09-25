package com.otakup.niriko.ui.character

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.CharacterDetailInfo
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.data.remote.displayTitle
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.components.LoadingContent
import com.otakup.niriko.viewmodel.CharacterDetailViewModel

/** 角色详情页。 */
@Composable
fun CharacterDetailScreen(
    viewModel: CharacterDetailViewModel,
    onBack: () -> Unit,
    onSubjectClick: (Long) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(modifier = modifier) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("角色详情", style = MaterialTheme.typography.titleLarge)
            }
            when {
                state.isLoading -> LoadingContent()
                state.error != null -> ErrorContent(message = state.error ?: "", onRetry = viewModel::retry)
                state.detail != null -> {
                    val detail = state.detail!!
                    CharacterDetailBody(
                        detail = detail,
                        subjects = state.subjects,
                        onSubjectClick = onSubjectClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                else -> ErrorContent(message = "无法加载角色信息", onRetry = viewModel::retry)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CharacterDetailBody(
    detail: CharacterDetailInfo,
    subjects: List<PersonSubjectInfo>,
    onSubjectClick: (Long) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // 头像 + 名称
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 共享元素：与作品详情页角色卡头像配对（key 一致）
            val avatarModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("character_avatar_${detail.id}"),
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
                detail.relation?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ===== 基本信息（阶段 6：字段一直在 Bangumi API 里，此前映射与展示都缺） =====
        val basicRows = buildList {
            detail.gender?.let { add("性别" to genderLabel(it)) }
            detail.birthday?.let { add("生日" to it) }
            detail.bloodType?.let { add("血型" to it) }
            detail.infoBox.forEach { add(it.key to it.value) }
        }
        if (basicRows.isNotEmpty()) {
            Text("基本信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            basicRows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(88.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ===== 收藏数 / 更多资料 =====
        Text(
            text = buildList {
                detail.collects?.let { add("收藏 ${formatCount(it)}") }
                detail.comments?.let { add("评论 ${formatCount(it)}") }
            }.joinToString(" · ").ifBlank { "—" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        Text(
            text = "在 Bangumi 查看角色页",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .clickable { runCatching { uriHandler.openUri(detail.moreInfoUrl) } }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Spacer(Modifier.height(20.dp))

        // 简介
        if (!detail.summary.isNullOrBlank()) {
            Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(detail.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }

        // 出演作品
        if (subjects.isNotEmpty()) {
            Text("出演作品", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            subjects.forEach { subject ->
                CharacterSubjectRow(
                    subject = subject,
                    onClick = { onSubjectClick(subject.subjectId) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CharacterSubjectRow(
    subject: PersonSubjectInfo,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 共享元素：参与作品封面 → 作品详情封面（key 与全局 "cover_{subjectId}" 约定一致）
            val coverModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("cover_${subject.subjectId}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            } else {
                Modifier
            }
            AsyncImage(
                model = subject.imageUrl,
                contentDescription = subject.displayTitle,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).then(coverModifier),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(subject.displayTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subject.staff?.let { Text("饰演：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(typeLabel(subject.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun genderLabel(raw: String): String = when (raw.lowercase()) {
    "male", "m" -> "男"
    "female", "f" -> "女"
    else -> raw
}

private fun formatCount(value: Int): String = when {
    value >= 10_000 -> "%.1f万".format(value / 10_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}

private fun typeLabel(type: Int): String = when (type) {
    2 -> "动画"
    1 -> "书籍"
    4 -> "游戏"
    3 -> "音乐"
    6 -> "三次元"
    else -> "其他"
}
