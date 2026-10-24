package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.data.model.stats.UpcomingDay
import com.otakup.niriko.data.model.stats.UpcomingEntry
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 「接下来七天」：把日历的任意区间能力包成固定 7 列的预告条。
 *
 * 复用 [StatsCalculator.computeCalendarEventsForRange]，所以跨月/跨年、停播缺口、
 * 「精确集日期优先」这些既有规则全部继承，不再写第二套日期推算。
 * 零新请求：数据来自已有的 BROADCAST_CALENDAR（30 分钟软 TTL）与季节性缓存。
 */
object UpcomingAiringCalculator {

    const val DEFAULT_DAYS = 7

    /** 取 [today] 起 [days] 天的放送事件（闭区间）。 */
    fun events(
        items: List<CollectionWithSubject>,
        today: LocalDate,
        days: Int = DEFAULT_DAYS,
        broadcast: Map<DayOfWeek, List<AiringSubject>>,
        seasonal: Map<String, List<AiringSubject>>,
        episodesBySubject: Map<Long, List<EpisodeInfo>>,
    ): Map<LocalDate, CalendarDayEvents> {
        if (days <= 0) return emptyMap()
        return StatsCalculator.computeCalendarEventsForRange(
            items = items,
            rangeStart = today,
            rangeEnd = today.plusDays(days - 1L),
            mode = CalendarMode.BROADCAST,
            broadcast = broadcast,
            seasonal = seasonal,
            episodesBySubject = episodesBySubject,
        )
    }

    /**
     * 把事件摊平成固定 [days] 列（没有更新的一天返回空列，由 UI 画「—」）。
     *
     * 列内排序：放送时刻升序（21:30 在 23:00 前），无时刻排最后，同时刻按评分降序。
     */
    fun days(
        events: Map<LocalDate, CalendarDayEvents>,
        items: List<CollectionWithSubject>,
        today: LocalDate,
        days: Int = DEFAULT_DAYS,
        episodesBySubject: Map<Long, List<EpisodeInfo>> = emptyMap(),
    ): List<UpcomingDay> {
        val collectionIds = items.mapTo(HashSet()) { it.subject.subjectId }
        return (0 until days).map { offset ->
            val date = today.plusDays(offset.toLong())
            val entries = events[date]?.broadcastSubjects.orEmpty()
                .distinctBy { it.subjectId }
                .sortedWith(
                    compareBy(
                        { it.airTimeMinutes ?: Int.MAX_VALUE },
                        { -(it.ratingScore ?: 0f) },
                    )
                )
                .map { subject ->
                    UpcomingEntry(
                        subjectId = subject.subjectId,
                        title = subject.displayTitle,
                        coverUrl = subject.coverUrl,
                        inCollection = subject.subjectId in collectionIds,
                        airMinutes = subject.airTimeMinutes,
                        airZone = subject.airTimeZone,
                        // 没有真实集日期的作品，日期来自星期外推 —— 与 CalendarCard 的"约"语义一致
                        estimated = episodesBySubject[subject.subjectId].orEmpty()
                            .none { it.type == 0 && !it.airdate.isNullOrBlank() },
                    )
                }
            UpcomingDay(date = date, entries = entries)
        }
    }
}
