package com.otakup.niriko.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.stats.StatsUiState
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.chartTypeColor
import com.otakup.niriko.ui.theme.statusTone

/**
 * 统计页 · 收藏概览（AniShelf 借鉴）。
 * 总作品大卡 + 2x2 tonal 状态卡（看过/在看/想看/搁置）+ 分类明细（动画/漫画/游戏/已看集数）。
 *
 * P0a 色彩秩序：状态卡改用 StatusTone（accent × 0.14 叠在 surfaceContainer 上），
 * 数字用 accent（深色取 tone 80），标签 onSurfaceVariant —— 浅粉彩补丁消失。
 * emoji 图标换为 Material Icons（tint 用对应状态色）。
 */
@Composable
fun StatsOverviewSection(state: StatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 总作品大卡（大数字 + 均分/完成率并排）
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.weight(1.4f).appleGlassCard(shape = MaterialTheme.shapes.medium),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${state.totalCount}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text("收藏作品", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                SmallStat("均分", if (state.averageMyRating > 0) "%.1f".format(state.averageMyRating) else "-")
                SmallStat("完成率", "%.0f%%".format(state.completionRate * 100))
            }
        }

        // 2x2 tonal 状态卡（P0a 色彩秩序）
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            statusCard(state, WatchStatus.COMPLETED, "看过", Icons.Outlined.CheckCircle, Modifier.weight(1f))
            statusCard(state, WatchStatus.WATCHING, "在看", Icons.Outlined.PlayArrow, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            statusCard(state, WatchStatus.PLAN_TO_WATCH, "想看", Icons.Outlined.BookmarkBorder, Modifier.weight(1f))
            statusCard(state, WatchStatus.ON_HOLD, "搁置", Icons.Outlined.Pause, Modifier.weight(1f))
        }

        // 分类明细
        Column(
            modifier = Modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.medium).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("分类明细", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            typeRow(color = chartTypeColor(SubjectType.ANIME), label = "动画", count = countOfType(state, SubjectType.ANIME))
            typeRow(color = chartTypeColor(SubjectType.MANGA), label = "漫画", count = countOfType(state, SubjectType.MANGA))
            typeRow(color = chartTypeColor(SubjectType.GAME), label = "游戏", count = countOfType(state, SubjectType.GAME))
            typeRow(color = chartTypeColor(SubjectType.BOOK), label = "已看集数", count = state.totalWatchedEpisodes)
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String) {
    Box(
        modifier = Modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.small),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun statusCard(
    state: StatsUiState,
    status: WatchStatus,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val tone = statusTone(status)
    val count = state.statusDistribution.firstOrNull { it.status == status }?.count ?: 0
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .drawBehind { drawRect(tone.accent.copy(alpha = 0.14f)) },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tone.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = tone.onContainer, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
            }
            Text("$count", fontSize = 26.sp, color = if (LocalDarkTheme.current) tone.onContainer else tone.accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun typeRow(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(color))
        Text("  " + label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun countOfType(state: StatsUiState, type: SubjectType): Int =
    state.typeDistribution.firstOrNull { it.type == type }?.count ?: 0
