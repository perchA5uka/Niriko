package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.Lag
import com.otakup.niriko.data.model.stats.LagQuality
import com.otakup.niriko.util.AiringStatus
import com.otakup.niriko.util.TimeUtils
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 「漏看」判定：已播集数 − 本地进度。
 *
 * 为什么单独抽出来：通知 Worker 与统计页的「今晚看什么 / 漏看」必须共用一份判定，
 * 否则会出现「卡片说落后 3 集、推送说落后 1 集」这种自相矛盾。
 *
 * 门槛（缺一不可，任何一条都会把"漏看"变成骚扰）：
 * - 状态必须是**在看**；已完结（finishDate 非空）的历史坑不算漏看；
 * - 作品当前仍在**放送中**（[AiringStatus.getPhase] == AIRING）——已完结作品的进度差是"补番"，不是漏看；
 * - 已播集数必须来自真实集日期，否则按首播日推算并标 [LagQuality.ESTIMATED]。
 */
object CatchUpCalculator {

    /** 默认只提示"落后 ≥1 集"。 */
    const val DEFAULT_MIN_BEHIND = 1

    /** 「漏看」徽标只统计落后 ≥3 集的：差 1 集是正常的追番节奏，差 3 集才是真的要掉队。 */
    const val SEVERE_BEHIND = 3

    /** 单部作品的漏看判定；不该提示时返回 null。 */
    fun lagOf(
        item: CollectionWithSubject,
        episodes: List<EpisodeInfo>,
        today: LocalDate,
        minBehind: Int = DEFAULT_MIN_BEHIND,
    ): Lag? {
        val collection = item.collection
        val subject = item.subject
        if (collection.status != WatchStatus.WATCHING) return null
        if (collection.finishDate != null) return null
        // 已完结的作品不提示：那是补番，不是"这周漏了"
        if (AiringStatus.getPhase(subject, today) != AiringStatus.AiringPhase.AIRING) return null

        val main = episodes.filter { it.type == 0 && it.sort > 0 }
        val exact = main.any { !it.airdate.isNullOrBlank() }
        val aired = if (exact) {
            StatsCalculator.computeEpisodeAirState(episodes, subject.totalEpisodes, today).airedCount
        } else {
            estimatedAired(subject, today) ?: return null
        }
        val watched = (collection.watchedEpisodes ?: 0).coerceAtLeast(0)
        val behind = aired - watched
        if (behind < minBehind) return null
        return Lag(
            subjectId = subject.subjectId,
            title = subject.displayTitle,
            aired = aired,
            watched = watched,
            behind = behind,
            quality = if (exact) LagQuality.EXACT else LagQuality.ESTIMATED,
        )
    }

    /** 全库漏看列表，按落后集数降序（同集数按标题，保证输出稳定可测）。 */
    fun lagging(
        items: List<CollectionWithSubject>,
        episodesBySubject: Map<Long, List<EpisodeInfo>>,
        today: LocalDate,
        minBehind: Int = DEFAULT_MIN_BEHIND,
    ): List<Lag> = items
        .mapNotNull { item ->
            lagOf(item, episodesBySubject[item.subject.subjectId].orEmpty(), today, minBehind)
        }
        .sortedWith(compareByDescending<Lag> { it.behind }.thenBy { it.title })

    /** 过滤出"值得提醒"的落后条目（默认落后 ≥3 集）。 */
    fun severeLags(lags: List<Lag>, threshold: Int = SEVERE_BEHIND): List<Lag> =
        lags.filter { it.behind >= threshold }

    /**
     * 没有每集数据时按首播日推算已播集数：首播当周算第 1 集，之后每 7 天 +1。
     *
     * 推算值永远不超过声明总集数；没有首播日（或尚未开播）时返回 null / 0，
     * 由调用方决定不提示，而不是猜一个数出来。
     */
    fun estimatedAired(subject: SubjectEntity, today: LocalDate): Int? {
        val airDate = TimeUtils.parseDate(subject.airDate) ?: return null
        if (airDate.isAfter(today)) return 0
        val weeks = ChronoUnit.DAYS.between(airDate, today) / 7 + 1
        val total = subject.totalEpisodes ?: return weeks.toInt()
        if (total <= 0) return 0
        return weeks.coerceIn(0, total.toLong()).toInt()
    }
}
