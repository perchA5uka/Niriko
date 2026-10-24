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
    /** 视频集数（ANIME + REAL）。游戏分钟与音乐曲目另计，见下方字段。 */
    val totalWatchedEpisodes: Int = 0,
    val completionRate: Float = 0f,
    /** 视频总分钟数（集数 × 单集分钟数）。 */
    val watchMinutes: Int = 0,
    /** [watchMinutes] 里用了类型默认单集时长的作品数：大于 0 时 UI 应显示"约"。 */
    val estimatedMinutesSubjects: Int = 0,
    /** 游戏游玩分钟数（GAME 的 watchedEpisodes 真实单位就是分钟）。 */
    val gameMinutes: Int = 0,
    /** 阅读单位（书籍/漫画的卷数，缺失时退回集数）。 */
    val readUnits: Int = 0,
    /** 已听曲目数（MUSIC 的 watchedEpisodes 真实单位）。 */
    val trackCount: Int = 0,
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
    /** 今晚看什么：漏看在前、今日更新在后，最多 [com.otakup.niriko.data.calculator.StatsTonightCalculator.MAX_ROWS] 条。 */
    val tonight: List<TonightItem> = emptyList(),
    /** 接下来 7 天，固定 7 列（没有更新的一天是空列，UI 画「—」）。 */
    val upcoming: List<UpcomingDay> = emptyList(),
    /** 全库漏看列表；今晚卡与「落后 N 部」入口共用，避免两处各算一套。 */
    val lagging: List<Lag> = emptyList(),
    /** 收藏健康度四类计数。 */
    val hygiene: HygieneReport = HygieneReport(),
    /** 完成率分子分母快照，供概览的「?」说明卡读取（文案与算法同源）。 */
    val completionDetail: CompletionDetail = CompletionDetail(),
)
