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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.otakup.niriko.data.model.stats.CompletionCaliber
import com.otakup.niriko.data.model.stats.CompletionDetail
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
                // 5.5：完成率口径显式化——同一份 CompletionDetail 既算数又出说明卡，文案不会与算法分叉
                CompletionStat(state = state)
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
        }

        // 观看量单独成卡：集数/时长/游戏分钟/阅读卷数是四种量纲，
        // 混进「分类明细」会和上面的作品计数看起来同质（改造前"已看集数"就挂在这里且用了书籍色）。
        Column(
            modifier = Modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.medium).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("观看量", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            typeRow(color = chartTypeColor(SubjectType.ANIME), label = "累计集数", count = state.totalWatchedEpisodes)
            if (state.watchMinutes > 0) {
                typeRow(
                    color = chartTypeColor(SubjectType.REAL),
                    label = if (state.estimatedMinutesSubjects > 0) "累计时长（约）" else "累计时长",
                    text = formatDuration(state.watchMinutes),
                )
            }
            if (state.gameMinutes > 0) {
                typeRow(color = chartTypeColor(SubjectType.GAME), label = "游戏时长", text = formatDuration(state.gameMinutes))
            }
            if (state.readUnits > 0) {
                typeRow(color = chartTypeColor(SubjectType.BOOK), label = "已读卷数", count = state.readUnits)
            }
            if (state.trackCount > 0) {
                typeRow(color = chartTypeColor(SubjectType.MUSIC), label = "已听曲目", count = state.trackCount)
            }
        }
    }
}

/** 分钟 → 人类可读时长；只用于概览的「累计时长 / 游戏时长」两行。 */
private fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes} 分钟"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
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

/** 完成率胶囊 + 可点的「?」：点开是口径说明（分子/分母/另两种口径的对照）。 */
@Composable
private fun CompletionStat(state: StatsUiState) {
    var showDialog by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxWidth().appleGlassCard(shape = MaterialTheme.shapes.small),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "%.0f%%".format(state.completionRate * 100),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text("完成率", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                text = "?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showDialog = true },
            )
        }
    }
    if (showDialog) {
        CompletionCaliberDialog(detail = state.completionDetail, onDismiss = { showDialog = false })
    }
}

@Composable
private fun CompletionCaliberDialog(detail: CompletionDetail, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("完成率怎么算的") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "当前：已看完 " + detail.completed + " 部 ÷ " + detail.excludePlanDenominator + " 部（剔除想看）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "全部收藏 " + detail.total + " 部，其中「想看」" + detail.planToWatch + " 部不计入分母。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "若按全部收藏：" + percent(detail.rate(CompletionCaliber.ALL)) +
                        "（" + detail.completed + "/" + detail.total + "）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "严格口径（含搁置/抛弃）：" + percent(detail.rate(CompletionCaliber.STRICT)) +
                        "（" + detail.completed + "/" + detail.strictDenominator + "）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "「想看」只是收藏意愿，还没开始看；算进分母会把完成率压低，所以默认不计。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

private fun percent(rate: Float): String = "%.0f%%".format(rate * 100)

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
private fun typeRow(color: Color, label: String, count: Int) =
    typeRow(color = color, label = label, text = "$count")

/** 数值既可能是计数也可能是时长文本（"22 小时 6 分"），所以留一个文本重载。 */
@Composable
private fun typeRow(color: Color, label: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(color))
        Text("  " + label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun countOfType(state: StatsUiState, type: SubjectType): Int =
    state.typeDistribution.firstOrNull { it.type == type }?.count ?: 0
