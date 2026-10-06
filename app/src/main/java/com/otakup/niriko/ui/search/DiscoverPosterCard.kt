package com.otakup.niriko.ui.search

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import com.otakup.niriko.ui.animation.pressTilt
import com.otakup.niriko.ui.animation.rememberReturnGlowModifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.ui.components.CoverImage
import com.otakup.niriko.ui.components.FavoriteBadge
import com.otakup.niriko.ui.components.RatingBadge
import com.otakup.niriko.ui.library.PosterCardMetrics

/**
 * 发现页 · 海报视图的单格（第 3 轮参考计划 §3.5，形态参考 open-ani/animeko 探索页海报网格）。
 *
 * 与作品库的 [com.otakup.niriko.ui.library.PosterGridCard] 共用同一套几何
 * （[PosterCardMetrics]：16dp 圆角 + 2:3 竖版封面），但**刻意做减法**：
 * 只保留 封面 · 标题（两行） · 评分胶囊 · 已收藏心形；
 * 类型标签 / 平台·集数 / 简介 / 进度条 / 我的评分 / 个人标签一律不进海报格 —— 那是卡片视图的职责
 * （animeko 的封面卡上同样不放任何角标）。
 *
 * 共享元素 key 与卡片视图、作品库网格一致（`cover_<subjectId>`），点海报进详情同样有封面飞入。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DiscoverPosterCard(
    subject: SubjectEntity,
    isCollected: Boolean,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // F17：从详情页返回时这张海报扫一次淡光（一次性事件，播完即清）
    val returnGlow = rememberReturnGlowModifier(subject.subjectId)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(returnGlow)
            .pressTilt(interactionSource)
            .clip(RoundedCornerShape(PosterCardMetrics.CornerRadius))
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        Box {
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
            // 左下评分：ratingScore 为 null 时 RatingBadge 自己不渲染
            RatingBadge(
                rating = subject.ratingScore,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 6.dp),
            )
            // 右上收藏心：仅已收藏时渲染
            if (isCollected) {
                FavoriteBadge(
                    filled = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 6.dp),
                )
            }
        }
        // 标题：固定两行（minLines = maxLines = 2），三列网格里行高才整齐
        Text(
            text = subject.displayTitle,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PosterCardMetrics.TitlePaddingHorizontal,
                    vertical = PosterCardMetrics.TitlePaddingVertical,
                ),
        )
    }
}
