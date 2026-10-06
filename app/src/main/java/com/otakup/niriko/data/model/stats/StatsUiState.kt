package com.otakup.niriko.data.model.stats

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class BroadcastMonthStatus { LOADING, SUCCESS, EMPTY, ERROR }

/** Success time belongs to this month and only advances after both sources succeed. */
data class BroadcastMonthState(
    val key: String = YearMonth.now().toString(),
    val status: BroadcastMonthStatus = BroadcastMonthStatus.LOADING,
    val lastSuccessAt: Long = 0L,
    val error: String? = null,
    val isPartial: Boolean = false,
    val eventCount: Int = 0,
)

/** 统计页 UI 快照。 */
data class StatsUiState(
    val totalCount: Int = 0,
    val averageMyRating: Double = 0.0,
    val totalWatchedEpisodes: Int = 0,
    val completionRate: Float = 0f,
    val statusDistribution: List<StatusDistItem> = emptyList(),
    val typeDistribution: List<TypeDistItem> = emptyList(),
    val monthlyTrend: List<MonthlyStats> = emptyList(),
    val myRatingDistribution: List<RatingDistribution> = emptyList(),
    val bangumiRatingDistribution: List<RatingDistribution> = emptyList(),
    val yearlyStats: List<YearlyStats> = emptyList(),
    val currentYearStats: YearlyStats? = null,
    val tagStats: List<TagStat> = emptyList(),
    val ratingComparison: List<RatingComparison> = emptyList(),
    val calendarYear: Int = LocalDate.now().year,
    val calendarMonth: Int = LocalDate.now().monthValue,
    /**
     * 日历锚点日期：月视图显示其所在月，周视图显示其所在周（周日起始）。
     *
     * 周 / 月切换只改 [calendarExpanded]，锚点不变 → 切换前后停留在同一周 / 同一月，不跳变。
     */
    val calendarAnchorDate: LocalDate = LocalDate.now(),
    /** 日历是否为整月视图：true = 月视图（默认，与周 / 月双模改造前一致），false = 收起的周视图。 */
    val calendarExpanded: Boolean = true,
    val calendarDayEvents: Map<LocalDate, CalendarDayEvents> = emptyMap(),
    val calendarMode: CalendarMode = CalendarMode.PERSONAL,
    val broadcastSchedule: Map<DayOfWeek, List<AiringSubject>> = emptyMap(),
    val broadcastError: String? = null,
    val selectedBroadcastMonth: BroadcastMonthState = BroadcastMonthState(),
    val isLoading: Boolean = true,
    val timelineEvents: List<TimelineEvent> = emptyList(),
    /** 照片墙（阶段 E）。 */
    val mosaic: List<MosaicItem> = emptyList(),
    /** 每集数据（统计页放送信息：已播出状态与热力图）。 */
    val episodesBySubject: Map<Long, List<EpisodeInfo>> = emptyMap(),
)
