package com.otakup.niriko.ui.library

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otakup.niriko.data.model.CollectionStats
import com.otakup.niriko.ui.components.NirikoTagPill
import com.otakup.niriko.ui.theme.NirikoTheme

/**
 * 作品库顶部统计条（P2：标签胶囊行）。
 *
 * V-12：这里原来是本文件私有的 DashboardChip（自绘 tonal 底 + labelSmall），而页面别处
 * 还有裸绿字（「抛弃 2」「上次更新」「刷新」）—— 同一个「数字/状态」概念有三种长相。
 * 现在统一改用共享的 [NirikoTagPill]：玻璃底 + 极淡描边 + labelMedium，
 * 与筛选胶囊/动作胶囊同一族材质，且底色与文字对比度由 GlassChipTokens.ensureReadable 保证，
 * 不再每次改壁纸就可能掉到 4.5:1 以下。
 *
 * 强调与否只表达「这是主统计」：部数与均分用 emphatic（更实的底），
 * 其余计数用默认档 —— 信息层级靠底色浓度区分，不再混用三种材质。
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
        NirikoTagPill("${stats.totalCount} 部作品", emphatic = true)
        if (stats.averageRating > 0.0) {
            NirikoTagPill("均分 %.1f".format(stats.averageRating), emphatic = true)
        }
        if (stats.totalWatchedEpisodes > 0) {
            NirikoTagPill("累计 ${stats.totalWatchedEpisodes} 集")
        }
        // 游戏时长与阅读卷数是不同量纲，各自成颗胶囊，不再并进"集"
        if (stats.gameMinutes > 0) {
            NirikoTagPill("游戏 ${formatPlaytime(stats.gameMinutes)}")
        }
        if (stats.readUnits > 0) {
            NirikoTagPill("已读 ${stats.readUnits} 卷")
        }
        if (stats.completionRate > 0f) {
            NirikoTagPill("完成率 %.0f%%".format(stats.completionRate * 100))
        }
        // 状态计数：NirikoTagPill 没有 color 形参（避免每处各传一个品牌色、又掉进
        // 「半透明底 + 任意前景」的对比度陷阱），所以这里统一走中性文字色 ——
        // 状态本身由文案区分，不再靠 secondary 色。
        stats.statusCounts.entries.forEach { (status, count) ->
            NirikoTagPill("${status.label} $count")
        }
    }
}

/** 游戏分钟 → 顶栏文案：不足 1 小时给分钟，否则给一位小数小时（50.0 → 50）。 */
private fun formatPlaytime(minutes: Int): String {
    if (minutes < 60) return "$minutes 分钟"
    val hours = minutes / 60.0
    val text = "%.1f".format(hours).removeSuffix(".0")
    return "$text 小时"
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
                gameMinutes = 3000,
                readUnits = 42,
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
