package com.otakup.niriko.data.model.stats

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import java.time.DayOfWeek
import java.time.LocalDate

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
    val calendarDayEvents: Map<LocalDate, CalendarDayEvents> = emptyMap(),
    val calendarMode: CalendarMode = CalendarMode.PERSONAL,
    val broadcastSchedule: Map<DayOfWeek, List<AiringSubject>> = emptyMap(),
    val broadcastError: String? = null,
    val isLoading: Boolean = true,
    val timelineEvents: List<TimelineEvent> = emptyList(),
)
