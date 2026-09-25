package com.otakup.niriko.ui.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.ui.components.CoverImage
import com.otakup.niriko.ui.components.FavoriteBadge
import com.otakup.niriko.ui.components.RatingBadge
import com.otakup.niriko.ui.components.StatusBadge

/**
 * 收藏页 · 海报网格卡片（AniShelf 借鉴）。
 * 2:3 竖版封面 + 左上状态胶囊、右上收藏心、左下评分胶囊、底部进度条；标题在卡下。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PosterGridCard(
    subject: SubjectEntity,
    collection: CollectionEntity,
    totalEpisodes: Int?,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress = if (totalEpisodes != null && totalEpisodes > 0)
        (collection.watchedEpisodes ?: 0).toFloat() / totalEpisodes.toFloat()
    else null

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
                .clickable(onClick = onClick),
        ) {
            CoverImage(
                coverUrl = subject.coverUrl,
                contentDescription = subject.displayTitle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                aspectRatio = 2f / 3f,
                sharedElementKey = "cover_" + subject.subjectId,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                subjectId = subject.subjectId,
            )
            // 左上状态
            StatusBadge(
                status = collection.status,
                subjectType = subject.type,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
            )
            // 右上收藏心
            FavoriteBadge(
                filled = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
            )
            // 左下评分（略微上移避开进度条）
            if (collection.rating != null) {
                RatingBadge(
                    rating = collection.rating,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp).padding(bottom = 12.dp),
                )
            }
            // 底部进度条
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(vertical = 2.dp),
                    ) {}
                }
            }
        }
        // 标题（卡下）
        Text(
            text = subject.displayTitle,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
        )
    }
}
