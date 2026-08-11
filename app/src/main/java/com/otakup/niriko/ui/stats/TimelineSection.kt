@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.stats

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.CoverThumbnail
import com.otakup.niriko.data.model.stats.TimelineEvent
import com.otakup.niriko.ui.components.appleGlassCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val DateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 时间线卡片数据（按作品聚合，每作品一张）。 */
private data class TimelineCardData(
    val subjectId: Long,
    val primaryTitle: String,
    val secondaryTitle: String?,
    val coverUrl: String?,
    val startDate: LocalDate?,
    val finishDate: LocalDate?,
)

/**
 * 时间线（iOS Smart Stack 风格：卡片堆叠，上下滑动循环切换）。
 * - 每部作品一张卡片（与其他界面一致的卡片样式）
 * - 卡片显示 primaryTitle + secondaryTitle + 开始/结束时间
 * - VerticalPager 上下滑动、跟手 1:1、松手 snap；Int.MAX_VALUE 取模实现循环
 * - contentPadding 露出相邻卡片边缘 + 细边框轮廓暗示可划动
 * - currentPageOffsetFraction 驱动相邻页缩小/淡出（堆叠层次）
 */
@Composable
fun TimelineSection(
    events: List<TimelineEvent>,
    onSubjectClick: (Long) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty()) return

    // 按 subjectId 聚合 → 每作品一张卡片，按最近活动（完成/开始）倒序
    val cards = remember(events) {
        events.map { it.collectionWithSubject }
            .distinctBy { it.subject.subjectId }
            .map { cws ->
                TimelineCardData(
                    subjectId = cws.subject.subjectId,
                    primaryTitle = cws.subject.titleCN ?: cws.subject.title,
                    secondaryTitle = if (cws.subject.titleCN != null) cws.subject.title else null,
                    coverUrl = cws.subject.coverUrl,
                    startDate = cws.collection.startDate,
                    finishDate = cws.collection.finishDate,
                )
            }
            .sortedByDescending { (it.finishDate ?: it.startDate) ?: LocalDate.MIN }
    }
    if (cards.isEmpty()) return

    // 循环分页：初始页对齐到第一张卡片（startPage % size == 0）
    val startPage = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % cards.size
    val pagerState = rememberPagerState(initialPage = startPage) { Int.MAX_VALUE }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "时间线",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        VerticalPager(
            state = pagerState,
            contentPadding = PaddingValues(vertical = 24.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) { page ->
            val realIndex = ((page - startPage) % cards.size + cards.size) % cards.size
            val card = cards[realIndex]
            // 跟手变形：相对本页偏移驱动相邻页缩小/淡出（堆叠层次）
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .coerceIn(-1f, 1f)
            val scale = 1f - 0.08f * abs(pageOffset)
            val alpha = 1f - 0.35f * abs(pageOffset)
            TimelineStackCard(
                card = card,
                onClick = { onSubjectClick(card.subjectId) },
                sharedElementKey = "cover_${card.subjectId}",
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
            )
        }
    }
}

/** 堆叠卡片：80dp 封面 + 主/副标题 + 开始/结束时间 + 细边框轮廓（暗示可划动）。 */
@Composable
private fun TimelineStackCard(
    card: TimelineCardData,
    onClick: () -> Unit,
    sharedElementKey: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .appleGlassCard(shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CoverThumbnail(
                coverUrl = card.coverUrl,
                contentDescription = card.primaryTitle,
                width = 80.dp,
                sharedElementKey = sharedElementKey,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.primaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                card.secondaryTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "开始：${card.startDate?.format(DateFormat) ?: "未记录"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "完成：${card.finishDate?.format(DateFormat) ?: "未记录"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
