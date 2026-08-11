@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.stats

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.ui.components.CoverImage
import com.otakup.niriko.ui.components.WindowBlurBehindEffect
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.util.TitleResolver
import com.otakup.niriko.util.AiringStatus
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日")

/**
 * 日历日详情底部弹窗。
 * 使用单一 LazyColumn 避免嵌套滚动崩溃。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDaySheet(
    date: LocalDate,
    events: CalendarDayEvents,
    onDismiss: () -> Unit,
    onSubjectClick: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Android 14+：宿主 window 背后模糊（iOS 弹窗效果），关闭自动恢复
    WindowBlurBehindEffect()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            // 标题
            item {
                Text(
                    text = date.format(dateFormat),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item { Spacer(Modifier.height(8.dp)) }

            // 开始观看列表
            if (events.startedItems.isNotEmpty()) {
                item { SectionHeader(title = "开始观看", count = events.startedItems.size) }
                items(events.startedItems, key = { "start_${it.collection.id}" }) { item ->
                    PersonalEventItem(
                        cws = item,
                        actionLabel = "开始",
                        actionColor = MaterialTheme.colorScheme.primary,
                        onClick = { item.subject?.let { onSubjectClick(it.subjectId) } },
                    )
                }
            }

            // 看过列表
            if (events.completedItems.isNotEmpty()) {
                if (events.startedItems.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                item { SectionHeader(title = "看过", count = events.completedItems.size) }
                items(events.completedItems, key = { "done_${it.collection.id}" }) { item ->
                    PersonalEventItem(
                        cws = item,
                        actionLabel = "完成",
                        actionColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { item.subject?.let { onSubjectClick(it.subjectId) } },
                    )
                }
            }

            // 放送中列表
            if (events.broadcastSubjects.isNotEmpty()) {
                if (events.startedItems.isNotEmpty() || events.completedItems.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                item { SectionHeader(title = "放送中", count = events.broadcastSubjects.size) }
                items(events.broadcastSubjects, key = { it.subjectId }) { subject ->
                    BroadcastEventItem(
                        subject = subject,
                        onClick = { onSubjectClick(subject.subjectId) },
                        episodeNumber = estimateEpisode(date, subject),
                    )
                }
            }

            // 发售日列表
            if (events.releaseDateSubjects.isNotEmpty()) {
                if (events.startedItems.isNotEmpty() || events.completedItems.isNotEmpty() || events.broadcastSubjects.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                item { SectionHeader(title = "发售日", count = events.releaseDateSubjects.size) }
                items(events.releaseDateSubjects, key = { it.subjectId }) { subject ->
                    BroadcastEventItem(
                        subject = subject,
                        onClick = { onSubjectClick(subject.subjectId) },
                        episodeNumber = estimateEpisode(date, subject),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 个人事件条目（开始/完成）。 */
@Composable
private fun PersonalEventItem(
    cws: CollectionWithSubject,
    actionLabel: String,
    actionColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val subject = cws.subject ?: return
    val titleInfo = TitleResolver.resolve(subject.titleCN, subject.title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            coverUrl = subject.coverUrl,
            contentDescription = "${subject.titleCN ?: subject.title} 封面",
            shape = RoundedCornerShape(6.dp),
            aspectRatio = 1f,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleInfo.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subject.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = CalendarTypeColors[subject.type] ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cws.collection.rating?.let { rating ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "%.1f".format(rating),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                subject.ratingScore?.let { score ->
                    if (cws.collection.rating == null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "%.1f".format(score),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = actionColor,
        )
    }
}

/** 放送作品条目，按 SubjectType 展示不同信息。 */
@Composable
private fun BroadcastEventItem(
    subject: SubjectEntity,
    onClick: () -> Unit,
    episodeNumber: Int? = null,
) {
    val titleInfo = TitleResolver.resolve(subject.titleCN, subject.title)

    val statusLabel = when (AiringStatus.getPhase(subject)) {
        AiringStatus.AiringPhase.UPCOMING -> "即将开播"
        AiringStatus.AiringPhase.AIRING -> "连载中"
        AiringStatus.AiringPhase.COMPLETED -> "已完结"
        AiringStatus.AiringPhase.UNKNOWN -> "未知"
    }
    val statusColor = when (AiringStatus.getPhase(subject)) {
        AiringStatus.AiringPhase.AIRING -> androidx.compose.ui.graphics.Color(0xFFFDD835)
        AiringStatus.AiringPhase.UPCOMING -> androidx.compose.ui.graphics.Color(0xFF66BB6A)
        AiringStatus.AiringPhase.COMPLETED -> androidx.compose.ui.graphics.Color(0xFF90A4AE)
        AiringStatus.AiringPhase.UNKNOWN -> androidx.compose.ui.graphics.Color(0xFF90A4AE)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            coverUrl = subject.coverUrl,
            contentDescription = "${subject.titleCN ?: subject.title} 封面",
            shape = RoundedCornerShape(6.dp),
            aspectRatio = 1f,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleInfo.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 元信息行（按 SubjectType 分支，复用 SubjectMetaSection 的模式）
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 第 N 话
                episodeNumber?.let { ep ->
                    Text(
                        text = "第${ep}话",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = subject.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = CalendarTypeColors[subject.type] ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 评分
                subject.ratingScore?.let { score ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "%.1f".format(score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 类型专属信息
                Spacer(Modifier.width(6.dp))
                SubjectSecondaryInfo(subject = subject)
            }
        }

        // 状态标签
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
        )
    }
}

/** 按 SubjectType 显示辅助信息（集数/卷数/平台等），详情页 SubjectMetaSection 风格。 */
@Composable
private fun SubjectSecondaryInfo(subject: SubjectEntity) {
    val parts = mutableListOf<String>()
    when (subject.type) {
        SubjectType.ANIME -> {
            subject.platform?.let { parts.add(it) }
            if (subject.totalEpisodes != null && subject.totalEpisodes > 0) {
                parts.add("${subject.totalEpisodes}集")
            } else {
                parts.add("集数未知")
            }
        }
        SubjectType.BOOK, SubjectType.MANGA -> {
            subject.platform?.let { parts.add(it) }
            subject.volumes?.takeIf { it > 0 }?.let { parts.add("${it}卷") }
        }
        SubjectType.GAME -> {
            subject.platform?.let { parts.add(it) }
        }
        SubjectType.MUSIC -> {
            subject.platform?.let { parts.add(it) }
        }
        SubjectType.REAL -> {
            if (subject.totalEpisodes != null && subject.totalEpisodes > 0) {
                parts.add("${subject.totalEpisodes}集")
            } else {
                parts.add("集数未知")
            }
        }
        SubjectType.PERSON, SubjectType.OTHER -> {}
    }
    // 放送日期
    subject.airDate?.let { parts.add(it.take(10)) }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ==================== 预览 ====================

@Preview(showBackground = true)
@Composable
private fun CalendarDaySheetPreview() {
    val today = LocalDate.now()
    val events = CalendarDayEvents(
        date = today,
        startedItems = listOf(
            CollectionWithSubject(
                collection = CollectionEntity(id = 1, subjectId = 328609, status = WatchStatus.WATCHING, watchedEpisodes = 3, startDate = today, rating = 8.5f),
                subject = SubjectEntity(subjectId = 328609, title = "ぼっち・ざ・ろっく！", titleCN = "孤独摇滚！", type = SubjectType.ANIME, totalEpisodes = 12, coverUrl = null, ratingScore = 8.4f),
            ),
        ),
        completedItems = listOf(
            CollectionWithSubject(
                collection = CollectionEntity(id = 2, subjectId = 303, status = WatchStatus.COMPLETED, finishDate = today, rating = 9.0f),
                subject = SubjectEntity(subjectId = 303, title = "葬送のフリーレン", titleCN = "葬送的芙莉莲", type = SubjectType.ANIME, totalEpisodes = 28, coverUrl = null, ratingScore = 8.8f),
            ),
        ),
        broadcastSubjects = listOf(
            SubjectEntity(subjectId = 999, title = "2026年7月新番", type = SubjectType.ANIME, totalEpisodes = 12, coverUrl = null, ratingScore = 7.5f),
            SubjectEntity(subjectId = 998, title = "新番2", type = SubjectType.ANIME, totalEpisodes = 24, coverUrl = null, ratingScore = 8.0f),
        ),
    )

    NirikoTheme {
        CalendarDaySheet(
            date = today,
            events = events,
            onDismiss = {},
            onSubjectClick = {},
        )
    }
}

/** 估算该番在指定日期的播出话数：(日期 − 首播日) / 7 + 1。无首播日或早于首播日返回 null。 */
private fun estimateEpisode(date: LocalDate, subject: SubjectEntity): Int? {
    val airDate = subject.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (date.isBefore(airDate)) return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(airDate, date)
    return (days / 7).toInt() + 1
}
