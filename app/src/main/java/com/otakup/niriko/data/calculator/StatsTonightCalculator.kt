package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import com.otakup.niriko.data.model.stats.Lag
import com.otakup.niriko.data.model.stats.LagQuality
import com.otakup.niriko.data.model.stats.TonightItem
import com.otakup.niriko.data.model.stats.TonightReason
import java.time.LocalDate

/**
 * 「今晚看什么」：今日更新的在看作品 ∪ 落后 ≥1 集的在看作品。
 *
 * 两条硬规则（照抄 bandi 的产品结论，别自己发明）：
 * 1. **同一作品同时命中时只保留「漏看」一条**——否则用户会以为自己只看今天这集就够了；
 * 2. 排序：漏看在前（落后集数降序），今日更新在后（放送时刻升序）。
 *
 * 注意：这里只做聚合与排序，不做集数推算——已播集数来自 [CatchUpCalculator]
 * 与 [StatsCalculator.computeEpisodeAirState]，保证与通知、日历口径一致。
 */
object StatsTonightCalculator {

    /** 卡片最多展示几行，其余在页脚汇总。 */
    const val MAX_ROWS = 8

    fun tonight(
        items: List<CollectionWithSubject>,
        today: LocalDate,
        events: Map<LocalDate, CalendarDayEvents>,
        lags: List<Lag>,
        episodesBySubject: Map<Long, List<EpisodeInfo>> = emptyMap(),
    ): List<TonightItem> {
        val watching = items.filter { it.collection.status == WatchStatus.WATCHING }
        if (watching.isEmpty()) return emptyList()
        val byId = watching.associateBy { it.subject.subjectId }

        val behindRows = lags.mapNotNull { lag ->
            val item = byId[lag.subjectId] ?: return@mapNotNull null
            val subject = item.subject
            TonightItem(
                subjectId = subject.subjectId,
                title = subject.displayTitle,
                coverUrl = subject.coverUrl,
                reason = TonightReason.BEHIND,
                watched = lag.watched,
                aired = lag.aired,
                behind = lag.behind,
                // 落后时"该补"的就是已播到的最新一集
                episodeNumber = lag.aired.takeIf { it > 0 },
                airMinutes = subject.airTimeMinutes,
                airZone = subject.airTimeZone,
                estimated = lag.quality == LagQuality.ESTIMATED,
            )
        }.sortedWith(compareByDescending<TonightItem> { it.behind }.thenByDescending { ratingOf(byId, it.subjectId) })

        val laggingIds = lags.mapTo(HashSet()) { it.subjectId }
        val todayRows = events[today]?.broadcastSubjects.orEmpty()
            .distinctBy { it.subjectId }
            .filter { it.subjectId in byId && it.subjectId !in laggingIds }
            .sortedWith(
                compareBy(
                    { it.airTimeMinutes ?: Int.MAX_VALUE },
                    { -(it.ratingScore ?: 0f) },
                )
            )
            .map { subject ->
                val aired = airedCount(subject, episodesBySubject, today)
                val watched = (byId[subject.subjectId]?.collection?.watchedEpisodes ?: 0).coerceAtLeast(0)
                TonightItem(
                    subjectId = subject.subjectId,
                    title = subject.displayTitle,
                    coverUrl = subject.coverUrl,
                    reason = TonightReason.AIRING_TODAY,
                    watched = watched,
                    aired = aired,
                    behind = 0,
                    episodeNumber = aired.takeIf { it > 0 },
                    airMinutes = subject.airTimeMinutes,
                    airZone = subject.airTimeZone,
                    estimated = !hasRealAirDates(subject.subjectId, episodesBySubject),
                )
            }

        return (behindRows + todayRows).take(MAX_ROWS)
    }

    private fun ratingOf(byId: Map<Long, CollectionWithSubject>, subjectId: Long): Float =
        byId[subjectId]?.subject?.ratingScore ?: 0f

    private fun airedCount(
        subject: SubjectEntity,
        episodesBySubject: Map<Long, List<EpisodeInfo>>,
        today: LocalDate,
    ): Int {
        val episodes = episodesBySubject[subject.subjectId].orEmpty()
        if (episodes.isEmpty()) return CatchUpCalculator.estimatedAired(subject, today) ?: 0
        return StatsCalculator.computeEpisodeAirState(episodes, subject.totalEpisodes, today).airedCount
    }

    private fun hasRealAirDates(subjectId: Long, episodesBySubject: Map<Long, List<EpisodeInfo>>): Boolean =
        episodesBySubject[subjectId].orEmpty().any { it.type == 0 && !it.airdate.isNullOrBlank() }
}
