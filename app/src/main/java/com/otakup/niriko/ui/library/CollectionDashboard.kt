package com.otakup.niriko.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.CollectionStats
import com.otakup.niriko.ui.theme.NirikoTheme

/**
 * 作品库顶部统计条（P2：标签胶囊行）。
 * 从「180 部作品 均分…」纯文本改为 labelSmall + tnum 数字的胶囊，FlowRow 自动换行，
 * 彻底避免最右端被屏幕裁切（比横向滚动更稳妥）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionDashboard(
    stats: CollectionStats,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        DashboardChip("${stats.totalCount} 部作品", emphasized = true)
        if (stats.averageRating > 0.0) {
            DashboardChip("均分 %.1f".format(stats.averageRating), emphasized = true)
        }
        if (stats.totalWatchedEpisodes > 0) {
            DashboardChip("累计 ${stats.totalWatchedEpisodes} 集")
        }
        if (stats.completionRate > 0f) {
            DashboardChip("完成率 %.0f%%".format(stats.completionRate * 100))
        }
        stats.statusCounts.entries.forEach { (status, count) ->
            DashboardChip("${status.label} $count", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun DashboardChip(
    text: String,
    emphasized: Boolean = false,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (emphasized) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionDashboardPreview() {
    NirikoTheme {
        CollectionDashboard(
            stats = CollectionStats(
                totalCount = 128,
                averageRating = 8.4,
                totalWatchedEpisodes = 350,
                completionRate = 0.65f,
                statusCounts = mapOf(
                    com.otakup.niriko.data.model.WatchStatus.PLAN_TO_WATCH to 20,
                    com.otakup.niriko.data.model.WatchStatus.WATCHING to 15,
                    com.otakup.niriko.data.model.WatchStatus.COMPLETED to 80,
                ),
            ),
        )
    }
}
