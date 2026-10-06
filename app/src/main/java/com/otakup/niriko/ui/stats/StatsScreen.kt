@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.stats

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.otakup.niriko.data.calculator.ChartSeriesCalculator
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.ui.animation.RevealOnScroll
import com.otakup.niriko.ui.common.reportBottomBarScroll
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.ui.theme.chartBarAccentColor
import com.otakup.niriko.ui.theme.chartBarDefaultColor
import com.otakup.niriko.ui.theme.chartStatusColor
import com.otakup.niriko.ui.theme.chartTypeColor
import com.otakup.niriko.ui.theme.themeChartPalette
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
import com.otakup.niriko.navigation.LocalSearchGestureLock
import com.otakup.niriko.navigation.TabReselectSignal
import com.otakup.niriko.navigation.TopLevelDestination
import com.otakup.niriko.util.beginScrollToTop
import java.time.LocalDate

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
    /** F11：底栏重选统计页事件（不在顶部则回顶；顶部重选无操作）。 */
    tabReselectEventId: Int = 0,
    tabReselect: TabReselectSignal = TabReselectSignal.None,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    // The selected month owns its freshness; /calendar success does not refresh history.
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    // 日历横滑会与顶级页 Pager 抢手势：手指落在日历上时锁住 Pager（与搜索页共用同一把锁）。
    // 只在 StatsScreen 读取（StatsContent 的 @Preview 直接调用它，读 CompositionLocal 会崩）。
    val searchGestureLock = LocalSearchGestureLock.current

    // 根级 Box：日详情面板是**非模态**浮层（无独立窗口），必须与内容同层叠放（R6）
    Box(modifier = modifier.fillMaxSize()) {
        StatsContent(
            state = state,
            onSwitchMonth = viewModel::switchMonth,
            onSwitchMode = viewModel::switchCalendarMode,
            onDayClick = { selectedDay = it },
            onRefreshBroadcast = viewModel::refreshBroadcastSchedule,
            onSwitchView = viewModel::switchCalendarView,
            onAnchorDateChange = viewModel::setCalendarAnchor,
            gestureLock = searchGestureLock,
            onSubjectClick = onSubjectClick,
            onNavigateToDiscover = onNavigateToDiscover,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            tabReselectEventId = tabReselectEventId,
            tabReselect = tabReselect,
            modifier = Modifier.fillMaxSize(),
        )

        // 日历日详情面板（非模态：面板之外仍可滚动日历）
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StatsContent(
    state: StatsUiState,
    onSwitchMonth: (Int) -> Unit = {},
    onSwitchMode: (CalendarMode) -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    onRefreshBroadcast: () -> Unit = {},
    /** 日历视图粒度：true = 月视图，false = 周视图。 */
    onSwitchView: (Boolean) -> Unit = {},
    /** 日历锚点变化（手势翻页回写）。 */
    onAnchorDateChange: (LocalDate) -> Unit = {},
    /** 顶级页 Pager 手势锁；为 null 时日历不干预 Pager（预览 / 未提供 CompositionLocal）。 */
    gestureLock: MutableState<Boolean>? = null,
    onSubjectClick: (Long) -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    tabReselectEventId: Int = 0,
    tabReselect: TabReselectSignal = TabReselectSignal.None,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // F11：底栏重选统计页 —— 不在顶部则回顶。统计页没有「重选即刷新」语义（顶部重选无操作），
    // 它的刷新入口是日历卡片自己的刷新按钮与下拉。
    LaunchedEffect(tabReselectEventId) {
        if (tabReselectEventId <= 0 || tabReselect.id <= 0) return@LaunchedEffect
        if (tabReselect.page != TopLevelDestination.Stats.ordinal) return@LaunchedEffect
        listState.beginScrollToTop()
    }
    // LazyColumn：区块懒组合，预组合成本大降（修复 Pager 滑动掉帧）
    LazyColumn(
        state = listState,
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
                // B4：文字加载态 → 骨架组合（日历格 + 概览胶囊 + 图表块），
                // 形状按真实版面排序；reduceMotion / 玻璃档位 OFF 时由 SkeletonBlock 自动退成静态灰块。
                StatsLoadingSkeleton()
            }
            state.totalCount == 0 -> item(key = "empty") {
                EmptyStatsPlaceholder(onNavigateToDiscover = onNavigateToDiscover)
            }
            else -> {
                // 0. 作品日历（置顶，核心）
                item(key = "calendar") {
                    CalendarCard(
                        anchorDate = state.calendarAnchorDate,
                        expanded = state.calendarExpanded,
                        dayEvents = state.calendarDayEvents,
                        calendarMode = state.calendarMode,
                        broadcastError = state.broadcastError,
                        onSwitchMonth = onSwitchMonth,
                        onSwitchMode = onSwitchMode,
                        onDayClick = onDayClick,
                        onSwitchView = onSwitchView,
                        onAnchorDateChange = onAnchorDateChange,
                        onRefreshBroadcast = onRefreshBroadcast,
                        broadcastLastUpdatedAt = state.selectedBroadcastMonth.lastSuccessAt,
                        broadcastMonthState = state.selectedBroadcastMonth,
                        gestureLock = gestureLock,
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

                // 4c. 口味雷达（R4：题材标签自绘雷达，见 ui/stats/NirikoRadarChart.kt）
                if (tasteRadarTags(state).size >= MIN_RADAR_AXES) {
                    item(key = "tasteRadar") { TasteRadarSection(state = state) }
                    item(key = "divTasteRadar") { HorizontalDivider() }
                }

                // 4b. 收藏月度趋势（本轮接入：此前已实现但从未被渲染）
                item(key = "monthly") { MonthlyTrendSection(state = state) }
                item(key = "divMonthly") { HorizontalDivider() }

                // 5. 评分分布
                item(key = "rating") { RatingDistributionSection(state = state) }
                item(key = "div6") { HorizontalDivider() }

                // 5b. 评分对比（个人 vs Bangumi，本轮接入：此前已实现但从未被渲染）
                item(key = "compare") { RatingComparisonSection(state = state) }
                item(key = "divCompare") { HorizontalDivider() }

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
            // 环形图（Vico 承载，见 ui/stats/NirikoDonutChart.kt）
            NirikoDonutChart(
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

// ==================== 4b. 口味雷达（R4 参考项） ====================

/**
 * 口味雷达取图规则：只要「有值」的标签，最多 [MAX_RADAR_AXES] 个
 * （[StatsUiState.tagStats] 本身已按出现次数降序）。
 */
private fun tasteRadarTags(state: StatsUiState): List<TagStat> =
    state.tagStats.filter { it.count > 0 }.take(MAX_RADAR_AXES)

/**
 * 口味雷达：题材标签分布换个形状看——词云看「最热的几个」，雷达看「口味形状」。
 * 图型与入场动画借用 ehsannarmani/ComposeCharts 的设计，但绘制是本工程自绘
 * （见 [NirikoRadarChart] 的注释：compose-charts 已发布版本里并没有雷达图）。
 */
@Composable
private fun TasteRadarSection(state: StatsUiState) {
    val tags = tasteRadarTags(state)
    if (tags.size < MIN_RADAR_AXES) return
    val palette = themeChartPalette()
    val colors = tags.indices.map { palette[it % palette.size] }
    val rows = tags.chunked(2)

    StatsSectionCard {
        SectionTitle("口味雷达")
        Text(
            "出现次数最多的 ${tags.size} 个题材标签；越靠外代表这个题材在你的收藏里出现得越多。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        NirikoRadarChart(
            axes = tags.mapIndexed { index, tag ->
                RadarAxis(label = tag.name, value = tag.count.toFloat(), color = colors[index])
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        )
        Spacer(Modifier.height(12.dp))
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEachIndexed { columnIndex, tag ->
                    val color = colors[rowIndex * 2 + columnIndex]
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tag.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${tag.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            if (rowIndex < rows.lastIndex) Spacer(Modifier.height(6.dp))
        }
    }
}

// ==================== 4. 月度趋势 ====================


@Composable
private fun MonthlyTrendSection(state: StatsUiState) {
    StatsSectionCard {
        SectionTitle("收藏月度趋势")
        if (state.monthlyTrend.isEmpty()) {
            Text("暂无月度数据", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@StatsSectionCard
        }
        // 月度增量柱 + 累计折线；累计值由 ChartSeriesCalculator 纯函数计算（有单测）
        val cumulative = remember(state.monthlyTrend) {
            ChartSeriesCalculator.monthlyCumulative(state.monthlyTrend)
        }
        val barColor = chartBarDefaultColor()
        val lineColor = chartBarAccentColor()
        Spacer(Modifier.height(8.dp))
        ChartLegendRow(
            entries = listOf(
                barColor to "每月收藏",
                lineColor to "累计收藏",
            ),
        )
        NirikoBarChart(
            // 保留 label.takeLast(2) 的截断语义（"2024-01" -> "01"）
            labels = state.monthlyTrend.map { it.label.takeLast(2) },
            bars = listOf(
                BarSeries(
                    label = "每月收藏",
                    values = state.monthlyTrend.map { it.count.toFloat() },
                    color = barColor,
                ),
            ),
            line = BarLine(
                label = "累计收藏",
                values = cumulative.map { it.toFloat() },
                color = lineColor,
                dashed = true,
            ),
            chartHeight = 180.dp,
        )
    }
}

// ==================== 5. 评分分布 ====================

@Composable
private fun RatingDistributionSection(state: StatsUiState) {
    StatsSectionCard {
        SectionTitle("评分分布")
        val hasMine = state.myRatingDistribution.isNotEmpty()
        val hasBangumi = state.bangumiRatingDistribution.isNotEmpty()
        if (!hasMine && !hasBangumi) {
            Text("暂无评分数据", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@StatsSectionCard
        }
        // 个人与 Bangumi 合并到同一坐标系做分组柱；分桶合并 / 排序走纯函数（有单测）。
        // 分桶数 > 12 时 Vico 侧按比例放大内容宽度并可横向滚动，柱宽不被压缩。
        val merged = remember(state.myRatingDistribution, state.bangumiRatingDistribution) {
            ChartSeriesCalculator.mergeRatingBuckets(
                state.myRatingDistribution,
                state.bangumiRatingDistribution,
            )
        }
        val myColor = chartBarDefaultColor()
        val bangumiColor = chartBarAccentColor()
        Spacer(Modifier.height(8.dp))
        ChartLegendRow(
            entries = buildList {
                if (hasMine) add(myColor to "我的评分")
                if (hasBangumi) add(bangumiColor to "Bangumi 评分")
            },
        )
        NirikoBarChart(
            labels = merged.map { it.range },
            bars = buildList {
                if (hasMine) {
                    add(BarSeries("我的评分", merged.map { it.myCount.toFloat() }, myColor))
                }
                if (hasBangumi) {
                    add(BarSeries("Bangumi 评分", merged.map { it.bangumiCount.toFloat() }, bangumiColor))
                }
            },
            chartHeight = 170.dp,
            showValueLabels = false,
        )
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
    val rows = state.ratingComparison.take(10)
    StatsSectionCard {
        SectionTitle("评分对比（个人 vs Bangumi）")
        if (rows.isEmpty()) {
            Text("暂无评分对比数据", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@StatsSectionCard
        }
        // 对柱（个人 / Bangumi）+ 差值折线；差值走纯函数（有单测）。
        // x 轴用序号，标题/分数在下方 RatingComparisonRow 列表里逐条列出，避免长标题压轴。
        val differences = remember(rows) { ChartSeriesCalculator.ratingDifferences(rows) }
        val myColor = chartBarDefaultColor()
        val bangumiColor = chartBarAccentColor()
        val diffColor = MaterialTheme.colorScheme.tertiary
        Spacer(Modifier.height(8.dp))
        ChartLegendRow(
            entries = listOf(
                myColor to "我的评分",
                bangumiColor to "Bangumi 评分",
                diffColor to "差值（个人 - Bangumi）",
            ),
        )
        NirikoBarChart(
            labels = rows.mapIndexed { index, _ -> "${index + 1}" },
            bars = listOf(
                BarSeries("我的评分", rows.map { it.myRating ?: 0f }, myColor),
                BarSeries("Bangumi 评分", rows.map { it.bangumiRating ?: 0f }, bangumiColor),
            ),
            line = BarLine(
                label = "差值（个人 - Bangumi）",
                values = differences.map { it ?: 0f },
                color = diffColor,
                dashed = true,
            ),
            chartHeight = 190.dp,
            showValueLabels = false,
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { item ->
                RatingComparisonRow(item = item)
            }
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

/** 图表图例行：色点 + 说明文字（分组柱 / 折线的颜色对照）。 */
@Composable
private fun ChartLegendRow(entries: List<Pair<Color, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
