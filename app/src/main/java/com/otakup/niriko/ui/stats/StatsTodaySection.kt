package com.otakup.niriko.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.otakup.niriko.data.model.stats.TonightItem
import com.otakup.niriko.data.model.stats.TonightReason
import com.otakup.niriko.data.model.stats.UpcomingDay
import com.otakup.niriko.data.onair.AirTime
import com.otakup.niriko.ui.components.appleGlassCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 「今晚看什么」：统计页第一张卡。
 *
 * 为什么放在日历上面：用户打开统计页的真实目的是"我今晚该看什么"，
 * 日历回答的是"哪天播什么"。空态（没有任何在看的作品要更新）直接不渲染卡片，
 * 而不是留一张"暂无"——统计页已经够长了。
 */
@Composable
fun TonightSection(
    items: List<TonightItem>,
    laggingCount: Int,
    onSubjectClick: (Long) -> Unit = {},
    onOpenLagging: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val behindCount = items.count { it.reason == TonightReason.BEHIND }
    Box(modifier = modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "今晚看什么",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = today.monthValue.toString() + " 月 " + today.dayOfMonth + " 日 · " + items.size + " 部",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                // 漏看是"会掉队"的信号，入口放在卡头而不是行内：行内点击是去详情
                if (laggingCount > 0) {
                    Text(
                        text = "落后 " + laggingCount + " 部 →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onOpenLagging),
                    )
                }
            }
            items.forEach { item -> TonightRow(item, today, onSubjectClick) }
            if (behindCount == 0) {
                Text(
                    text = "今天更新的都在进度上，追得上",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TonightRow(item: TonightItem, today: LocalDate, onSubjectClick: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSubjectClick(item.subjectId) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(item.coverUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle(item, today),
                style = MaterialTheme.typography.labelSmall,
                color = when (item.reason) {
                    TonightReason.BEHIND -> MaterialTheme.colorScheme.error
                    TonightReason.AIRING_TODAY -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 行内说明：漏看要说清"已播/已看"（否则用户不知道差多少），今天更新要说清第几集与几点。 */
private fun subtitle(item: TonightItem, today: LocalDate): String {
    val approx = if (item.estimated) "约 " else ""
    val time = item.airMinutes?.let { minutes ->
        AirTime(minutes, item.airZone ?: AirTime.DEFAULT_ZONE_ID).displayText(today)
    }
    return when (item.reason) {
        TonightReason.BEHIND -> buildString {
            append("落后 " + item.behind + " 集（已播 " + approx + item.aired + " / 已看 " + item.watched + "）")
            if (time != null) append(" · " + time + " 放送")
        }
        TonightReason.AIRING_TODAY -> buildString {
            append(item.episodeNumber?.let { "第 " + approx + it + " 集" } ?: "今日更新")
            if (time != null) append(" · " + time + " 放送")
        }
    }
}

/**
 * 「接下来七天」：7 列预告条。
 *
 * 每列只给三个信息（周几 / 日期 / 更新数 + 首封封面），因为它是"扫一眼"而不是"读列表"；
 * 没有更新的一天保留列并显示「—」，否则列宽会随数据跳动，用户以为日历错位。
 */
@Composable
fun UpcomingWeekStrip(
    days: List<UpcomingDay>,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    Box(modifier = modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.medium)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "接下来七天",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "共 " + days.sumOf { it.count } + " 部更新",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    UpcomingColumn(
                        day = day,
                        label = if (day.date == today) "今天" else weekdayLabels[day.date.dayOfWeek.value - 1],
                        isToday = day.date == today,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingColumn(
    day: UpcomingDay,
    label: String,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
        )
        Text(
            text = day.date.format(dayDateFormatter),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (day.firstCoverUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(day.firstCoverUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)),
            )
        }
        Text(
            text = if (day.count > 0) day.count.toString() else "—",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (day.count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 「M/d」只在列头出现一次，提到顶层避免每重组合成一次 Formatter。 */
private val dayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")
