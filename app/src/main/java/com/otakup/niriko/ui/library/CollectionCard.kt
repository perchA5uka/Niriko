@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectCardDisplayModel
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.ui.subject.UniversalSubjectCard
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.util.toCardDisplayModel

/**
 * 收藏列表卡片。将 Entity + 收藏字段转换为 DisplayModel，委托给 UniversalSubjectCard。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CollectionCard(
    subject: SubjectEntity,
    status: WatchStatus,
    watchedEpisodes: Int?,
    totalEpisodes: Int?,
    myRating: Float?,
    updateTime: Long,
    personalTags: List<String> = emptyList(),
    steam: SteamGameEntity? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    // 仅 subject/steam 变化时重建展示模型，避免滚动/重组时每项重复映射（Card 在 Lazy 列表中稳定）
    val model = remember(subject, steam) { subject.toCardDisplayModel(steam) }
    // game 时长存于 watchedEpisodes
    val isGame = subject.type == SubjectType.GAME

    UniversalSubjectCard(
        model = model,
        status = status,
        watchedEpisodes = if (isGame) null else watchedEpisodes,
        totalEpisodes = totalEpisodes,
        completionTime = if (isGame) watchedEpisodes else null,
        myRating = myRating,
        personalTags = personalTags,
        updateTime = updateTime,
        onClick = onClick,
        onLongClick = onLongClick,
        sharedElementKey = sharedElementKey,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        subjectId = subject.subjectId,
        isCollectionCard = true,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun CollectionCardPreview() {
    NirikoTheme {
        CollectionCard(
            subject = SubjectEntity(subjectId = 328609, title = "ぼっち・ざ・ろっく！", titleCN = "孤独摇滚！", type = SubjectType.ANIME, coverUrl = null, ratingScore = 8.4f, totalEpisodes = 12),
            status = WatchStatus.WATCHING,
            watchedEpisodes = 6,
            totalEpisodes = 12,
            myRating = 8.5f,
            updateTime = System.currentTimeMillis(),
            onClick = {},
        )
    }
}
