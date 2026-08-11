package com.otakup.niriko.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.CollectionStats
import com.otakup.niriko.ui.theme.NirikoTheme

@Composable
fun CollectionDashboard(
    stats: CollectionStats,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stats.totalCount} 部作品",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 12.dp),
        )

        if (stats.averageRating > 0.0) {
            Text(
                text = "均分 %.1f".format(stats.averageRating),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (stats.totalWatchedEpisodes > 0) {
            Text(
                text = "累计 ${stats.totalWatchedEpisodes} 集",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (stats.completionRate > 0f) {
            Text(
                text = "完成率 %.0f%%".format(stats.completionRate * 100),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        stats.statusCounts.entries.forEach { (status, count) ->
            Text(
                text = "${status.label} $count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
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
