package com.otakup.niriko.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otakup.niriko.ui.components.SkeletonBlock
import com.otakup.niriko.ui.theme.NirikoShapes

/**
 * 统计页首屏加载骨架（ui-upgrade-plan-2026 · B4）。
 *
 * 取代原先的一行文字「正在加载统计数据…」。形状按真实版面排序：
 * 日历卡（月头 + 星期行 + 6 行日格）→ 概览胶囊一排 → 两个图表块。
 *
 * 静止态由 [SkeletonBlock] 内部的 `Modifier.skeletonShimmer()` 自动裁决
 * （reduceMotion 为真或玻璃档位 OFF 时退化为静态灰块），此处不再自行判断动画。
 */
@Composable
internal fun StatsLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CalendarSkeleton()
        OverviewSkeleton()
        ChartSkeleton(chartHeight = 168.dp)
        ChartSkeleton(chartHeight = 132.dp)
    }
}

/** 日历月视图骨架：月头一行、星期一行、日格 6 行 x 7 列。 */
@Composable
private fun CalendarSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(width = 88.dp, height = 20.dp, shape = NirikoShapes.ControlShape)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) {
                    SkeletonBlock(width = 28.dp, height = 28.dp, shape = NirikoShapes.capsule(28.dp))
                }
            }
        }
        WeekdayRow(lineHeight = 12.dp)
        repeat(6) {
            WeekdayRow(lineHeight = 34.dp)
        }
    }
}

/** 一行 7 格（星期标题行与日格行共用，靠高度区分）。 */
@Composable
private fun WeekdayRow(lineHeight: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(7) {
            SkeletonBlock(
                height = lineHeight,
                shape = NirikoShapes.ControlShape,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 概览胶囊一排。 */
@Composable
private fun OverviewSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(4) {
            SkeletonBlock(
                height = 68.dp,
                shape = NirikoShapes.SectionShape,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 图表骨架：标题行 + 一块图表底。 */
@Composable
private fun ChartSkeleton(chartHeight: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBlock(width = 120.dp, height = 16.dp, shape = NirikoShapes.ControlShape)
        SkeletonBlock(
            height = chartHeight,
            shape = NirikoShapes.CardShape,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
