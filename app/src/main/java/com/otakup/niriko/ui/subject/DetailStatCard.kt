package com.otakup.niriko.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.ui.components.GlassSectionCard
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 详情页 · 统计卡网格（AniShelf 借鉴）。
 * 三张白玻璃小卡（评分 / 总集数或时长 / 评分人数），置于评分分布上方。
 */
@Composable
fun DetailStatGrid(
    subject: SubjectEntity,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier,
    /** 已加载的曲目/章节数（MUSIC 用曲目数）。调用方负责过滤 type。 */
    episodeCount: Int? = null,
    /** VNDB 时长（分钟），GAME 中间卡用。 */
    vndbLengthMinutes: Int? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard(
            icon = { Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.padding(bottom = 6.dp)) },
            value = subject.ratingScore?.let { formatScore(it) } ?: "—",
            label = "Bangumi 评分",
            glassBackdrop = glassBackdrop,
            isScrolling = isScrolling,
            modifier = Modifier.weight(1f),
        )

        // 中间卡按类型自适应：
        // - 动画/三次元 → 总集数
        // - 书籍/漫画 → 单本隐藏；系列显示卷数
        // - 游戏 → 时长（VNDB 分钟优先）
        // - 音乐 → 曲目数
        val showMiddle = when (subject.type) {
            SubjectType.BOOK, SubjectType.MANGA -> {
                subject.series != false && (subject.volumes ?: 0) > 0
            }
            else -> true
        }
        if (showMiddle) {
            val (value, label) = when (subject.type) {
                SubjectType.ANIME, SubjectType.REAL ->
                    (subject.totalEpisodes?.toString() ?: "—") to "总集数"
                SubjectType.BOOK, SubjectType.MANGA ->
                    (subject.volumes?.toString() ?: "—") to "卷数"
                SubjectType.GAME ->
                    (vndbLengthMinutes?.let { formatDuration(it) } ?: "—") to "时长"
                SubjectType.MUSIC ->
                    (episodeCount?.toString() ?: "—") to "曲目数"
                else ->
                    (subject.totalEpisodes?.toString() ?: "—") to "总集数"
            }
            StatCard(
                icon = { Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF3B82F6), modifier = Modifier.padding(bottom = 6.dp)) },
                value = value,
                label = label,
                glassBackdrop = glassBackdrop,
                isScrolling = isScrolling,
                modifier = Modifier.weight(1f),
            )
        }

        StatCard(
            icon = { Icon(Icons.Filled.People, null, tint = Color(0xFF2E7D32), modifier = Modifier.padding(bottom = 6.dp)) },
            value = subject.ratingTotal?.toString() ?: "—",
            label = "评分人数",
            glassBackdrop = glassBackdrop,
            isScrolling = isScrolling,
            modifier = Modifier.weight(1f),
        )
    }
}

/** VNDB 时长（分钟）→ 可读文本（≥60 分钟显示 xh ym）。 */
private fun formatDuration(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    minutes > 0 -> "${minutes}m"
    else -> "—"
}

/** 单个统计小卡（白玻璃圆角 + 彩色图标 + 大数字 + 标签）。 */
@Composable
private fun StatCard(
    icon: @Composable () -> Unit,
    value: String,
    label: String,
    glassBackdrop: Backdrop? = null,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier,
) {
    GlassSectionCard(
        backdrop = glassBackdrop,
        isScrolling = isScrolling,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        contentPadding = 14.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        icon()
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
        )
        }
    }
}

private fun formatScore(score: Float): String =
    if (score == score.toInt().toFloat()) score.toInt().toString()
    else "%.1f".format(score)

