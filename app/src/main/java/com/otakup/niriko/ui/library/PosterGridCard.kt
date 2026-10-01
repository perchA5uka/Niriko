package com.otakup.niriko.ui.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.otakup.niriko.ui.components.skeletonBaseColor
import com.otakup.niriko.ui.components.skeletonShimmer

/**
 * 收藏页 · 海报网格卡片（AniShelf 借鉴）。
 * 2:3 竖版封面 + 左上状态胶囊、右上收藏心、左下评分胶囊、底部进度条；标题在卡下。
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun PosterGridCard(
    subject: SubjectEntity,
    collection: CollectionEntity,
    totalEpisodes: Int?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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
                .clip(RoundedCornerShape(PosterCardMetrics.CornerRadius))
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(PosterCardMetrics.CornerRadius))
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                ),
        ) {
            CoverImage(
                coverUrl = subject.coverUrl,
                contentDescription = subject.displayTitle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PosterCardMetrics.CornerRadius),
                aspectRatio = PosterCardMetrics.CoverAspectRatio,
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
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = PosterCardMetrics.TitlePaddingHorizontal,
                vertical = PosterCardMetrics.TitlePaddingVertical,
            ),
        )
    }
}

/**
 * 海报网格卡的真实几何常量（B4）。
 *
 * 骨架屏（[PosterGridSkeletonCard]）复用同一组数值：加载完成时卡片位置与高度完全一致，
 * 不会出现跳版（CLS）。
 */
internal object PosterCardMetrics {

    /** 卡片与封面圆角。 */
    val CornerRadius = 16.dp

    /** 封面宽高比：2:3 竖版。 */
    const val CoverAspectRatio = 2f / 3f

    /** 卡下标题的行高（labelSmall 的 lineHeight）。 */
    val TitleBlockHeight = 16.dp

    /** 卡下标题左右内边距。 */
    val TitlePaddingHorizontal = 2.dp

    /** 卡下标题上下内边距。 */
    val TitlePaddingVertical = 5.dp
}

/**
 * 海报网格骨架卡（B4）：几何与 [PosterGridCard] 完全一致 —— 16.dp 圆角、2:3 封面、
 * 卡下同高标题行 —— 因此加载完成替换真实卡片时网格不跳版。
 *
 * 扫光分别加在封面块与标题块上（两者都是被完整上色的圆角表面），降级时变静态灰块。
 */
@Composable
fun PosterGridSkeletonCard(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PosterCardMetrics.CoverAspectRatio)
                .clip(RoundedCornerShape(PosterCardMetrics.CornerRadius))
                .background(skeletonBaseColor())
                .skeletonShimmer(),
        )
        // 标题占位（与真实标题行同高，保证纵向节奏一致）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PosterCardMetrics.TitlePaddingHorizontal,
                    vertical = PosterCardMetrics.TitlePaddingVertical,
                )
                .height(PosterCardMetrics.TitleBlockHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f))
                    .skeletonShimmer(),
            )
        }
    }
}
