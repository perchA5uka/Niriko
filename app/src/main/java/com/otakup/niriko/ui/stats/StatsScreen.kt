@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.stats

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.ui.animation.RevealOnScroll
import com.otakup.niriko.ui.common.reportBottomBarScroll
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.ui.theme.chartBarAccentColor
import com.otakup.niriko.ui.theme.chartBarDefaultColor
import com.otakup.niriko.ui.theme.chartStatusColor
import com.otakup.niriko.ui.theme.chartTypeColor
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.data.model.stats.MonthlyStats
import com.otakup.niriko.data.model.stats.RatingComparison
import com.otakup.niriko.data.model.stats.RatingDistribution
import com.otakup.niriko.data.model.stats.StatsUiState
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.viewmodel.StatsViewModel
import com.otakup.niriko.data.model.stats.StatusDistItem
import com.otakup.niriko.data.model.stats.TagStat
import com.otakup.niriko.data.model.stats.TimelineEvent
import com.otakup.niriko.data.model.stats.TypeDistItem
import com.otakup.niriko.data.model.stats.YearlyStats
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ==================== 图表颜色方案（主题派生，P0a） ====================
// 颜色一律来自 ui/theme/ChartPalette.kt：从 colorScheme.primary 经 HCT 派生，
// 不再使用游离字面量。换主题色 → 图表自动协调。

// ==================== 主屏幕 ====================

/**
 * 统计页面主入口。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onSubjectClick: (Long) -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    // 放送日历陈旧度（0 = 从未刷新），显示在日历卡片底部
    val broadcastLastUpdatedAt by viewModel.broadcastLastUpdatedAt.collectAsState()
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    StatsContent(
        state = state,
        onSwitchMonth = viewModel::switchMonth,
        onSwitchMode = viewModel::switchCalendarMode,
        onDayClick = { selectedDay = it },
        onRefreshBroadcast = viewModel::refreshBroadcastSchedule,
        broadcastLastUpdatedAt = broadcastLastUpdatedAt,
        onSubjectClick = onSubjectClick,
        onNavigateToDiscover = onNavigateToDiscover,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        modifier = modifier,
    )

    // 日历日详情底部弹窗
    selectedDay?.let { date ->
        val events = state.calendarDayEvents[date]
        if (events != null && events.hasEvents) {
            CalendarDaySheet(
                date = date,
                events = events,
                episodesBySubject = state.episodesBySubject,
                onDismiss = { selectedDay = null },
                onSubjectClick = onSubjectClick,
            )
        } else {
            // 没有事件的日期也弹空窗？不弹，直接清除选中
            selectedDay = null
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StatsContent(
    state: StatsUiState,
    onSwitchMonth: (Int) -> Unit = {},
    onSwitchMode: (CalendarMode) -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    onRefreshBroadcast: () -> Unit = {},
    /** 放送日历上次成功刷新时间（0 = 从未）。 */
    broadcastLastUpdatedAt: Long = 0L,
    onSubjectClick: (Long) -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    // LazyColumn：区块懒组合，预组合成本大降（修复 Pager 滑动掉帧）
    LazyColumn(
        modifier = modifier.fillMaxSize().reportBottomBarScroll(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "title") {
            Text(
                text = "收藏统计",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        when {
            state.isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("正在加载统计数据…", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.totalCount == 0 -> item(key = "empty") {
                EmptyStatsPlaceholder(onNavigateToDiscover = onNavigateToDiscover)
            }
            else -> {
                // 0. 作品日历（置顶，核心）
                item(key = "calendar") {
                    CalendarCard(
                        year = state.calendarYear,
                        month = state.calendarMonth,
                        dayEvents = state.calendarDayEvents,
                        calendarMode = state.calendarMode,
                        broadcastError = state.broadcastError,
                        onSwitchMonth = onSwitchMonth,
                        onSwitchMode = onSwitchMode,
                        onDayClick = onDayClick,
                        onRefreshBroadcast = onRefreshBroadcast,
                        broadcastLastUpdatedAt = broadcastLastUpdatedAt,
                    )
                }
                item(key = "div1") { HorizontalDivider() }

                // 1. 时间线（截断 + 折叠，见 TimelineSection）
                item(key = "timeline") {
                    TimelineSection(
                        events = state.timelineEvents,
                        onSubjectClick = onSubjectClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                item(key = "div2") { HorizontalDivider() }

                // 2. 概览仪表板
                item(key = "overview") { StatsOverviewSection(state = state) }
                item(key = "div3") { HorizontalDivider() }

                // 3. 收藏状态分布（环形图）
                item(key = "status") { StatusDistributionSection(state = state) }
                item(key = "div4") { HorizontalDivider() }

                // 4. 作品类型分布（降级横向条形，见 TypeDistributionSection）
                item(key = "type") { TypeDistributionSection(state = state) }
                item(key = "div5") { HorizontalDivider() }

                // 5. 评分分布
                item(key = "rating") { RatingDistributionSection(state = state) }
                item(key = "div6") { HorizontalDivider() }

                // 6. 年度总结
                item(key = "yearly") { YearlySummarySection(state = state) }

                // 6b. 标签词云（阶段 C：标签·词云）
                if (state.tagStats.isNotEmpty()) {
                    item(key = "tags") { TagStatsSection(state = state) }
                    item(key = "divTags") { HorizontalDivider() }
                }

                // 7. 照片墙（阶段 E）
                if (state.mosaic.isNotEmpty()) {
                    item(key = "mosaic") { MosaicSection(state = state) }
                    item(key = "div7") { HorizontalDivider() }
                }
            }
        }
        item(key = "bottomSpacer") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun EmptyStatsPlaceholder(onNavigateToDiscover: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有收藏数据", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("添加一些作品收藏后，这里将展示有趣的统计数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onNavigateToDiscover) {
                Text("去发现页搜索作品")
            }
        }
    }
}

// ==================== 2. 收藏状态分布 ====================

@Composable
private fun StatusDistributionSection(state: StatsUiState) {
    val nonZeroItems = state.statusDistribution.filter { it.count > 0 }
    if (nonZeroItems.isEmpty()) return

    StatsSectionCard {
        SectionTitle("收藏状态分布")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 环形图
            DonutChart(
                data = nonZeroItems.map { item ->
                    DonutSlice(
                        label = item.status.label,
                        value = item.count.toFloat(),
                        color = chartStatusColor(item.status),
                    )
                },
                totalText = "${state.totalCount}",
                modifier = Modifier.size(160.dp),
            )
            Spacer(Modifier.width(24.dp))
            // 图例
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                nonZeroItems.forEach { item ->
                    LegendRow(
                        color = chartStatusColor(item.status),
                        label = item.status.label,
                        count = item.count,
                        percentage = item.percentage,
                    )
                }
            }
        }
    }
}

// ==================== 3. 类型分布 ====================

@Composable
private fun TypeDistributionSection(state: StatsUiState) {
    val nonZeroItems = state.typeDistribution.filter { it.count > 0 }
    if (nonZeroItems.isEmpty()) return
    val maxCount = nonZeroItems.maxOf { it.count }.coerceAtLeast(1)

    StatsSectionCard {
        SectionTitle("作品类型分布")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            nonZeroItems.forEach { item ->
                TypeBarRow(
                    color = chartTypeColor(item.type),
                    label = item.type.label,
                    count = item.count,
                    percentage = item.percentage,
                    ratio = item.count.toFloat() / maxCount,
                )
            }
        }
    }
}

/** 类型分布横向条形行：色块 + 名称 + 计数 + 进度条（替代环形图，更轻量）。 */
@Composable
private fun TypeBarRow(
    color: Color,
    label: String,
    count: Int,
    percentage: Float,
    ratio: Float,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count · ${(percentage * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.small),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}

// ==================== 4. 月度趋势 ====================

@Composable
private fun MonthlyTrendSection(state: StatsUiState) {
    SectionTitle("收藏月度趋势")
    if (state.monthlyTrend.isEmpty()) {
        Text("暂无月度数据", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    // 水平滚动柱状图
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        BarChart(
            data = state.monthlyTrend.map { m ->
                BarEntry(label = m.label.takeLast(2), value = m.count.toFloat())
            },
            modifier = Modifier.height(180.dp),
        )
    }
}

// ==================== 5. 评分分布 ====================

@Composable
private fun RatingDistributionSection(state: StatsUiState) {
    StatsSectionCard {
        SectionTitle("评分分布")

        if (state.myRatingDistribution.isNotEmpty()) {
            Text("我的评分", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                BarChart(
                    data = state.myRatingDistribution.map { r ->
                        BarEntry(label = r.range, value = r.count.toFloat(), color = chartBarDefaultColor())
                    },
                    modifier = Modifier.height(160.dp),
                )
            }
        }

        if (state.bangumiRatingDistribution.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Bangumi 评分", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                BarChart(
                    data = state.bangumiRatingDistribution.map { r ->
                        BarEntry(label = r.range, value = r.count.toFloat(), color = chartBarAccentColor())
                    },
                    modifier = Modifier.height(160.dp),
                )
            }
        }

        if (state.myRatingDistribution.isEmpty() && state.bangumiRatingDistribution.isEmpty()) {
            Text("暂无评分数据", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ==================== 6. 年度总结 ====================

@Composable
private fun YearlySummarySection(state: StatsUiState) {
    StatsSectionCard {
        SectionTitle("年度总结")
        state.currentYearStats?.let { yearly ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${yearly.year} 年",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    YearlyStatItem(label = "新增收藏", value = "${yearly.totalAdded}")
                    YearlyStatItem(label = "已完成", value = "${yearly.completedCount}")
                    YearlyStatItem(label = "观看集数", value = "${yearly.totalEpisodes}")
                    YearlyStatItem(label = "均分", value = if (yearly.averageRating > 0) "%.1f".format(yearly.averageRating) else "-")
                }
            }
        } ?: run {
            Text("今年还没有收藏记录", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 往年列表
        if (state.yearlyStats.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text("历年统计", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            state.yearlyStats.reversed().forEach { yearStat ->
                YearlyRow(yearStat = yearStat)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun YearlyStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun YearlyRow(yearStat: YearlyStats) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 0.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${yearStat.year}", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            Text("${yearStat.totalAdded} 部  ·  已完成 ${yearStat.completedCount} 部",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${yearStat.totalEpisodes} 集",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (yearStat.averageRating > 0) {
                Text("均分 %.1f".format(yearStat.averageRating),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ==================== 7. 标签词云 ====================

@Composable
private fun TagStatsSection(state: StatsUiState) {
    StatsSectionCard {
        TagWordCloud(tags = state.tagStats.take(50), onTagClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

// ==================== 7b. 照片墙（阶段 E）====================
@Composable
private fun MosaicSection(state: StatsUiState) {
    // 照片墙拆成小卡片容器：横屏下避免单张巨型玻璃卡超过离屏缓冲上限导致纯黑。
    // 标题单独一张卡，每行照片各一张小玻璃卡。
    StatsSectionCard {
        SectionTitle("照片墙（我的收藏）")
    }
    Spacer(Modifier.height(6.dp))
    val items = state.mosaic
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(4).forEach { rowItems ->
            StatsSectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { m ->
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(3f / 4f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (m.coverUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(m.coverUrl).crossfade(true).build(),
                                    contentDescription = m.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(m.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f).aspectRatio(3f / 4f)) }
                }
            }
        }
    }
}

// ==================== 8. 评分对比 ====================

@Composable
private fun RatingComparisonSection(state: StatsUiState) {
    SectionTitle("评分对比（个人 vs Bangumi）")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.ratingComparison.take(10).forEach { item ->
            RatingComparisonRow(item = item)
        }
    }
}

@Composable
private fun RatingComparisonRow(item: RatingComparison) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        // 个人评分
        val myRatingText = item.myRating?.let { "%.1f".format(it) } ?: "-"
        Text(
            text = myRatingText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text("/", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        // Bangumi 评分
        val bgmText = item.bangumiRating?.let { "%.1f".format(it) } ?: "-"
        Text(
            text = bgmText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ==================== 通用组件 ====================

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** L1「磨砂瓷」统计区块容器，包住单个图表/区块（P1 容器化）。 */
@Composable
private fun StatsSectionCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().appleGlassCard(shape = RoundedCornerShape(20.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            content()
        }
    }
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    count: Int,
    percentage: Float,
) {
    // 入场数字动画降级为静态：Pager 预组合时动画与滑动争帧导致卡顿
    val animatedCount = count

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .padding(end = 0.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = color)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$animatedCount",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "%.0f%%".format(percentage * 100),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ==================== 环形图 (Donut Chart) ====================

data class DonutSlice(
    val label: String,
    val value: Float,
    val color: Color,
)

@Composable
fun DonutChart(
    data: List<DonutSlice>,
    totalText: String,
    modifier: Modifier = Modifier,
) {
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total == 0f) return

    // 环形图绘制动画降级为静态（Pager 预组合时与滑动争帧）
    val animationProgress = 1f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.15f
            val gapAngle = 3f
            var startAngle = -90f

            data.forEachIndexed { _, slice ->
                val fullSweep = (slice.value / total) * 360f - gapAngle
                // 每个弧段按动画进度展开
                val sweep = (fullSweep * animationProgress).coerceAtLeast(0f)
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                )
                // 即使动画中也要保持正确的起止角度布局
                startAngle += fullSweep + gapAngle
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "总计",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ==================== 柱状图 (Bar Chart) ====================

data class BarEntry(
    val label: String,
    val value: Float,
    val color: Color = Color.Unspecified,
)

@Composable
fun BarChart(
    data: List<BarEntry>,
    modifier: Modifier = Modifier,
    barWidth: Float = 24f,
    barSpacing: Float = 8f,
) {
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.value }
    if (maxValue == 0f) return

    // 柱状图生长动画降级为静态（Pager 预组合时与滑动争帧）
    val animationProgress = 1f

    val totalWidth = data.size * (barWidth + barSpacing) + barSpacing

    // 复用 Paint 实例（组合期创建一次，避免每次绘制 new Paint() 的 GC 压力）；
    // 绘制中按原逻辑设置颜色/字号，渲染输出与之前一致
    val density = LocalDensity.current.density
    val textPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#8C8C8C")
            textSize = 10f * density
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    val palette = chartBarDefaultColor()
    Canvas(modifier = modifier.width(totalWidth.dp)) {
        val chartHeight = size.height - 32f
        val barWidthPx = barWidth * density
        val barSpacingPx = barSpacing * density
        val startX = barSpacingPx

        data.forEachIndexed { index, entry ->
            val targetBarHeight = (entry.value / maxValue) * chartHeight
            val barHeight = targetBarHeight * animationProgress
            val x = startX + index * (barWidthPx + barSpacingPx)
            val y = size.height - 16f - barHeight

            // 柱体（未显式指定色时用主题派生主色）
            drawRect(
                color = if (entry.color == Color.Unspecified) palette else entry.color,
                topLeft = Offset(x, y),
                size = Size(barWidthPx, barHeight),
            )

            // 数值（仅当 animationProgress > 0.8 显示）
            if (animationProgress > 0.8f) {
                drawContext.canvas.nativeCanvas.apply {
                    if (entry.value > 0) {
                        drawText(
                            "%.0f".format(entry.value),
                            x + barWidthPx / 2,
                            y - 4f,
                            textPaint,
                        )
                    }
                    // 标签
                    textPaint.color = android.graphics.Color.parseColor("#8C8C8C")
                    textPaint.textSize = 9f * density
                    drawText(
                        entry.label,
                        x + barWidthPx / 2,
                        size.height - 2f,
                        textPaint,
                    )
                }
            } else {
                // 只绘制标签
                drawContext.canvas.nativeCanvas.apply {
                    textPaint.color = android.graphics.Color.parseColor("#8C8C8C")
                    textPaint.textSize = 9f * density
                    drawText(
                        entry.label,
                        x + barWidthPx / 2,
                        size.height - 2f,
                        textPaint,
                    )
                }
            }
        }
    }
}

// ==================== Preview ====================

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun StatsScreenPreview() {
    NirikoTheme {
        StatsContent(
            state = StatsUiState(
                isLoading = false,
                totalCount = 128,
                averageMyRating = 7.8,
                totalWatchedEpisodes = 3560,
                completionRate = 0.62f,
                statusDistribution = listOf(
                    StatusDistItem(WatchStatus.PLAN_TO_WATCH, 20, 0.156f),
                    StatusDistItem(WatchStatus.WATCHING, 15, 0.117f),
                    StatusDistItem(WatchStatus.COMPLETED, 80, 0.625f),
                    StatusDistItem(WatchStatus.ON_HOLD, 8, 0.063f),
                    StatusDistItem(WatchStatus.DROPPED, 5, 0.039f),
                ),
                typeDistribution = listOf(
                    TypeDistItem(SubjectType.ANIME, 60, 0.469f),
                    TypeDistItem(SubjectType.MANGA, 25, 0.195f),
                    TypeDistItem(SubjectType.BOOK, 15, 0.117f),
                    TypeDistItem(SubjectType.GAME, 18, 0.141f),
                    TypeDistItem(SubjectType.MUSIC, 5, 0.039f),
                    TypeDistItem(SubjectType.REAL, 3, 0.023f),
                    TypeDistItem(SubjectType.OTHER, 2, 0.016f),
                ),
                monthlyTrend = listOf(
                    MonthlyStats("2024-01", 2024, 1, 5),
                    MonthlyStats("2024-02", 2024, 2, 3),
                    MonthlyStats("2024-03", 2024, 3, 8),
                    MonthlyStats("2024-04", 2024, 4, 6),
                    MonthlyStats("2024-05", 2024, 5, 10),
                    MonthlyStats("2024-06", 2024, 6, 4),
                    MonthlyStats("2024-07", 2024, 7, 7),
                    MonthlyStats("2024-08", 2024, 8, 12),
                    MonthlyStats("2024-09", 2024, 9, 9),
                    MonthlyStats("2024-10", 2024, 10, 6),
                ),
                myRatingDistribution = listOf(
                    RatingDistribution("0-1", 0), RatingDistribution("1-2", 1),
                    RatingDistribution("2-3", 0), RatingDistribution("3-4", 2),
                    RatingDistribution("4-5", 3), RatingDistribution("5-6", 8),
                    RatingDistribution("6-7", 15), RatingDistribution("7-8", 25),
                    RatingDistribution("8-9", 20), RatingDistribution("9-10", 6),
                ),
                bangumiRatingDistribution = listOf(
                    RatingDistribution("0-1", 0), RatingDistribution("1-2", 0),
                    RatingDistribution("2-3", 0), RatingDistribution("3-4", 1),
                    RatingDistribution("4-5", 2), RatingDistribution("5-6", 5),
                    RatingDistribution("6-7", 12), RatingDistribution("7-8", 30),
                    RatingDistribution("8-9", 28), RatingDistribution("9-10", 10),
                ),
                currentYearStats = YearlyStats(2026, 36, 18, 480, 7.5),
                yearlyStats = listOf(
                    YearlyStats(2024, 45, 28, 1200, 7.2),
                    YearlyStats(2025, 52, 30, 1500, 7.8),
                    YearlyStats(2026, 36, 18, 480, 7.5),
                ),
                tagStats = listOf(
                    TagStat("芳文社", 15), TagStat("日常", 12), TagStat("治愈", 10),
                    TagStat("搞笑", 8), TagStat("奇幻", 7), TagStat("战斗", 6),
                    TagStat("恋爱", 5), TagStat("科幻", 4), TagStat("校园", 4),
                    TagStat("热血", 3), TagStat("悬疑", 3), TagStat("音乐", 2),
                ),
                ratingComparison = listOf(
                    RatingComparison(1, "孤独摇滚！", 9.5f, 8.4f),
                    RatingComparison(2, "葬送的芙莉莲", 9.0f, 8.8f),
                    RatingComparison(3, "跃动青春", 8.5f, 8.2f),
                    RatingComparison(4, "BanG Dream! It's MyGO!!!!!", 9.0f, 8.6f),
                ),
            ),
        )
    }
}
