package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.local.entity.CollectionWithSubject
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.stats.AiringSubject
import com.otakup.niriko.data.model.stats.CalendarDayEvents
import com.otakup.niriko.data.model.stats.CalendarMode
import com.otakup.niriko.data.model.stats.MonthlyStats
import com.otakup.niriko.data.model.stats.MosaicItem
import com.otakup.niriko.data.model.stats.RatingComparison
import com.otakup.niriko.data.model.stats.RatingDistribution
import com.otakup.niriko.data.model.stats.StatusDistItem
import com.otakup.niriko.data.model.stats.StatsUiState
import com.otakup.niriko.data.model.stats.TagStat
import com.otakup.niriko.data.model.stats.TimelineAction
import com.otakup.niriko.data.model.stats.TimelineEvent
import com.otakup.niriko.data.model.stats.TypeDistItem
import com.otakup.niriko.data.model.stats.YearlyStats
import com.otakup.niriko.util.AiringStatus
import com.otakup.niriko.util.TimeUtils
import com.otakup.niriko.util.TitleResolver
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 统计页面纯计算逻辑。
 *
 * 所有方法都是纯函数——不持有状态，不依赖 ViewModel 作用域。
 * 传入参数，返回结果，可独立单元测试。
 */
object StatsCalculator {

    /**
     * 计算完整的 StatsUiState（不含日历部分，日历由 [computeCalendarEvents] 单独填充）。
     */
    fun computeStats(items: List<CollectionWithSubject>): StatsUiState {
        if (items.isEmpty()) return StatsUiState(isLoading = false)

        val totalCount = items.size
        val myRatings = items.mapNotNull { it.collection.rating }
        val averageMyRating = if (myRatings.isEmpty()) 0.0 else myRatings.average()
        val totalWatchedEpisodes = items.sumOf { it.collection.watchedEpisodes ?: 0 }
        val completedCount = items.count { it.collection.status == WatchStatus.COMPLETED }
        val completionRate = completedCount.toFloat() / totalCount

        val statusCounts = items.groupBy { it.collection.status }.mapValues { it.value.size }
        val statusDistribution = WatchStatus.entries.map { status ->
            StatusDistItem(
                status = status, count = statusCounts[status] ?: 0,
                percentage = if (totalCount > 0) (statusCounts[status]?.toFloat() ?: 0f) / totalCount else 0f,
            )
        }
        val typeCounts = items.groupBy { it.subject.type }.mapValues { it.value.size }
        val typeDistribution = SubjectType.entries.map { type ->
            TypeDistItem(
                type = type, count = typeCounts[type] ?: 0,
                percentage = if (totalCount > 0) (typeCounts[type]?.toFloat() ?: 0f) / totalCount else 0f,
            )
        }

        val monthlyMap = mutableMapOf<String, MutableList<CollectionWithSubject>>()
        items.forEach { item ->
            val instant = Instant.ofEpochMilli(item.collection.createTime).atZone(ZoneId.systemDefault()).toLocalDate()
            val key = "${instant.year}-${"%02d".format(instant.monthValue)}"
            monthlyMap.getOrPut(key) { mutableListOf() }.add(item)
        }
        val monthlyTrend = monthlyMap.entries.sortedBy { it.key }.map { (key, list) ->
            val parts = key.split("-")
            MonthlyStats(label = key, year = parts[0].toInt(), month = parts[1].toInt(), count = list.size)
        }

        val myRatingDist = buildRatingDistribution(myRatings)
        val bangumiRatings = items.mapNotNull { it.subject.ratingScore }
        val bangumiRatingDist = buildRatingDistribution(bangumiRatings)

        val yearGroups = items.groupBy {
            Instant.ofEpochMilli(it.collection.createTime).atZone(ZoneId.systemDefault()).toLocalDate().year
        }
        val yearlyStats = yearGroups.entries.sortedBy { it.key }.map { (year, yearItems) ->
            val completedInYear = yearItems.count { it.collection.status == WatchStatus.COMPLETED }
            val yearRatings = yearItems.mapNotNull { it.collection.rating }
            YearlyStats(
                year = year, totalAdded = yearItems.size, completedCount = completedInYear,
                totalEpisodes = yearItems.sumOf { it.collection.watchedEpisodes ?: 0 },
                averageRating = if (yearRatings.isEmpty()) 0.0 else yearRatings.average(),
            )
        }
        val currentYear = LocalDate.now().year
        val currentYearStats = yearlyStats.find { it.year == currentYear }

        val tagCountMap = mutableMapOf<String, Int>()
        items.forEach { item -> item.collection.personalTags.forEach { tag -> tagCountMap[tag] = (tagCountMap[tag] ?: 0) + 1 } }
        val tagStats = tagCountMap.entries.sortedByDescending { it.value }.map { TagStat(name = it.key, count = it.value) }

        val comparison = items
            .filter { it.collection.rating != null && it.subject.ratingScore != null }
            .sortedByDescending { it.collection.rating }.take(20)
            .map {
                val primaryTitle = TitleResolver.resolve(it.subject.titleCN, it.subject.title).primary
                RatingComparison(
                    subjectId = it.subject.subjectId, title = primaryTitle,
                    myRating = it.collection.rating, bangumiRating = it.subject.ratingScore,
                )
            }

        val timelineEvents = computeTimelineEvents(items)

        return StatsUiState(
            isLoading = false,
            totalCount = totalCount, averageMyRating = averageMyRating,
            totalWatchedEpisodes = totalWatchedEpisodes, completionRate = completionRate,
            statusDistribution = statusDistribution, typeDistribution = typeDistribution,
            monthlyTrend = monthlyTrend, myRatingDistribution = myRatingDist,
            bangumiRatingDistribution = bangumiRatingDist,
            yearlyStats = yearlyStats, currentYearStats = currentYearStats,
            tagStats = tagStats, ratingComparison = comparison, timelineEvents = timelineEvents,
            mosaic = computeMosaic(items),
        )
    }

    /** 构建评分分布数组（10个桶）。 */
    fun buildRatingDistribution(ratings: List<Float>): List<RatingDistribution> {
        if (ratings.isEmpty()) return emptyList()
        val buckets = IntArray(10) { 0 }
        ratings.forEach { r -> val idx = (r.toInt()).coerceIn(0, 9); buckets[idx]++ }
        return buckets.mapIndexed { index, count -> RatingDistribution(range = "${index}-${index + 1}", count = count) }
    }

    /** 照片墙（阶段 E）：有封面的收藏按收藏时间倒序取前 N 个。 */
    fun computeMosaic(items: List<CollectionWithSubject>, limit: Int = 120): List<MosaicItem> {
        return items.mapNotNull { item ->
            val cover = item.subject.coverUrl ?: return@mapNotNull null
            val createDate = Instant.ofEpochMilli(item.collection.createTime).atZone(ZoneId.systemDefault()).toLocalDate()
            MosaicItem(
                subjectId = item.subject.subjectId,
                title = TitleResolver.resolve(item.subject.titleCN, item.subject.title).primary,
                coverUrl = cover,
                type = item.subject.type,
                status = item.collection.status,
                createDate = createDate,
            )
        }.sortedByDescending { it.createDate }.take(limit)
    }

    /**
     * 计算指定月份的日历事件。
     *
     * 核心修复：每个放送作品只出现在 [airDate, estimatedEndDate] 范围内且匹配 weekdays 的日子。
     * 解决了跨年/跨月时作品错误出现在无关月份的问题。
     */
    fun computeCalendarEvents(
        items: List<CollectionWithSubject>,
        year: Int,
        month: Int,
        mode: CalendarMode,
        broadcast: Map<DayOfWeek, List<AiringSubject>>,
        seasonal: Map<String, List<AiringSubject>>,
    ): Map<LocalDate, CalendarDayEvents> {
        val firstOfMonth = LocalDate.of(year, month, 1)
        val lastOfMonth = LocalDate.of(year, month, firstOfMonth.lengthOfMonth())
        val now = LocalDate.now()

        val personalStarted = mutableMapOf<LocalDate, MutableList<CollectionWithSubject>>()
        val personalCompleted = mutableMapOf<LocalDate, MutableList<CollectionWithSubject>>()
        if (mode == CalendarMode.PERSONAL || mode == CalendarMode.ALL) {
            items.forEach { item ->
                item.collection.startDate?.let { sd ->
                    if (!sd.isBefore(firstOfMonth) && !sd.isAfter(lastOfMonth))
                        personalStarted.getOrPut(sd) { mutableListOf() }.add(item)
                }
                item.collection.finishDate?.let { fd ->
                    if (!fd.isBefore(firstOfMonth) && !fd.isAfter(lastOfMonth))
                        personalCompleted.getOrPut(fd) { mutableListOf() }.add(item)
                }
            }
        }

        val broadcastByDate = mutableMapOf<LocalDate, MutableList<SubjectEntity>>()
        val releaseByDate = mutableMapOf<LocalDate, MutableList<SubjectEntity>>()

        val monthKey = "%04d-%02d".format(year, month)
        val coverFallbackMap = seasonal[monthKey]
            ?.filter { it.subject.coverUrl != null }
            ?.associate { it.subject.subjectId to it.subject.coverUrl }
            ?: emptyMap()

        val episodeFallbackMap = seasonal[monthKey]
            ?.filter { it.subject.totalEpisodes != null && it.subject.totalEpisodes > 0 }
            ?.associate { it.subject.subjectId to it.subject.totalEpisodes!! }
            ?: emptyMap()

        if (mode == CalendarMode.BROADCAST || mode == CalendarMode.ALL) {
            for (day in 1..firstOfMonth.lengthOfMonth()) {
                val date = LocalDate.of(year, month, day)
                val dow = date.dayOfWeek

                broadcast[dow]?.forEach { airing ->
                    val effectiveEnd = airing.estimatedEndDate
                        ?: airing.airDate.plusMonths(3)
                    val inRange = !date.isBefore(airing.airDate) && !date.isAfter(effectiveEnd)

                    if (inRange) {
                        when {
                            AiringStatus.isReleaseDateType(airing.subject) -> {
                                if (date == airing.airDate)
                                    releaseByDate.getOrPut(date) { mutableListOf() }.add(airing.subject)
                            }
                            AiringStatus.isMovieOrOva(airing.subject) -> {
                                if (date == airing.airDate)
                                    releaseByDate.getOrPut(date) { mutableListOf() }.add(airing.subject)
                            }
                            AiringStatus.isWeeklyAnime(airing.subject) -> {
                                broadcastByDate.getOrPut(date) { mutableListOf() }.add(airing.subject)
                            }
                        }
                    }
                }
            }

            val seasonalSubjects = seasonal[monthKey] ?: emptyList()

            seasonalSubjects.forEach { airing ->
                if (AiringStatus.isReleaseDateType(airing.subject)) {
                    val date = airing.airDate
                    if (!date.isBefore(firstOfMonth) && !date.isAfter(lastOfMonth)) {
                        releaseByDate.getOrPut(date) { mutableListOf() }.add(airing.subject)
                    }
                }
            }

            val weeklySeasonal = seasonalSubjects.filter { AiringStatus.isWeeklyAnime(it.subject) }
            val movieSeasonal = seasonalSubjects.filter { AiringStatus.isMovieOrOva(it.subject) }

            movieSeasonal.forEach { airing ->
                val date = airing.airDate
                if (!date.isBefore(firstOfMonth) && !date.isAfter(lastOfMonth)) {
                    releaseByDate.getOrPut(date) { mutableListOf() }.add(airing.subject)
                }
            }

            if (weeklySeasonal.isNotEmpty()) {
                // 按播出星期预分组：airDayOfWeek 只依赖作品自身字段，与具体日期无关，
                // 提前分组后 31 天循环内直接查表，避免每天对全部 weekly 作品重复解析
                val weeklyByDay = weeklySeasonal.groupBy { airing ->
                    airing.subject.airWeekday?.let { wd ->
                        if (wd in 1..7) DayOfWeek.of(wd)
                        else if (wd == 0) DayOfWeek.SUNDAY
                        else airing.airDate.dayOfWeek
                    } ?: airing.airDate.dayOfWeek
                }
                for (day in 1..firstOfMonth.lengthOfMonth()) {
                    val date = LocalDate.of(year, month, day)
                    val dow = date.dayOfWeek
                    weeklyByDay[dow]?.forEach { airing ->
                        val effectiveEnd = airing.estimatedEndDate
                            ?: airing.airDate.plusMonths(3)
                        if (!date.isBefore(airing.airDate) && !date.isAfter(effectiveEnd)) {
                            broadcastByDate.getOrPut(date) { mutableListOf() }.add(airing.subject)
                        }
                    }
                }
            }
        }

        val allDates = personalStarted.keys + personalCompleted.keys + broadcastByDate.keys + releaseByDate.keys
        val result = mutableMapOf<LocalDate, CalendarDayEvents>()
        for (date in allDates) {
            val started = personalStarted[date]?.toList() ?: emptyList()
            val completed = personalCompleted[date]?.toList() ?: emptyList()
            val broadcastSubjects = broadcastByDate[date]?.toList() ?: emptyList()
            val release = releaseByDate[date]?.toList() ?: emptyList()

            val personalSubjects = (started + completed).mapNotNull { it.subject }.distinctBy { it.subjectId }
                .sortedByDescending { it.ratingScore ?: 0f }
            val broadcastSorted = broadcastSubjects.sortedByDescending { it.ratingScore ?: 0f }
            val releaseSorted = release.sortedByDescending { it.ratingScore ?: 0f }

            val covers = when (mode) {
                // 每格最多 5 张封面（热度前 5，ratingScore 降序）：番剧每周播一集，单封面整月重复会视觉疲劳
                CalendarMode.PERSONAL -> personalSubjects.take(5)
                CalendarMode.BROADCAST -> {
                    val fb = broadcastSorted.take(5)
                    if (fb.size >= 5) fb else fb + releaseSorted.take(5 - fb.size)
                }
                CalendarMode.ALL -> {
                    val fp = personalSubjects.take(5)
                    if (fp.size >= 5) fp else {
                        val fill = (broadcastSorted + releaseSorted).filter { s -> fp.none { it.subjectId == s.subjectId } }.take(5 - fp.size)
                        fp + fill
                    }
                }
            }.map { subject ->
                if (subject.coverUrl == null && coverFallbackMap.containsKey(subject.subjectId)) {
                    subject.copy(coverUrl = coverFallbackMap[subject.subjectId])
                } else subject
            }

            // 跨月延续标记：该日有 airDate 早于当月 1 日的放送/发售作品（原在 UI 组合期解析，现移入计算层）
            val hasContinuing = (broadcastByDate[date].orEmpty() + releaseByDate[date].orEmpty()).any {
                it.airDate?.let { ad ->
                    val parsed = runCatching { LocalDate.parse(ad) }.getOrNull()
                    parsed != null && parsed.isBefore(firstOfMonth)
                } == true
            }

            result[date] = CalendarDayEvents(
                date = date, startedItems = started, completedItems = completed,
                broadcastSubjects = broadcastSubjects.map { s ->
                    if (s.totalEpisodes == null || s.totalEpisodes <= 0) {
                        episodeFallbackMap[s.subjectId]?.let { eps -> s.copy(totalEpisodes = eps) } ?: s
                    } else s
                },
                releaseDateSubjects = release.map { s ->
                    if (s.totalEpisodes == null || s.totalEpisodes <= 0) {
                        episodeFallbackMap[s.subjectId]?.let { eps -> s.copy(totalEpisodes = eps) } ?: s
                    } else s
                },
                coverCandidates = covers,
                displayCover = pickDisplayCover(date, covers),
                hasContinuing = hasContinuing,
            )
        }
        return result
    }

    /**
     * 按“当月第 N 个该星期几”挑选单张封面（用户要求的一图一格轮换）。
     * occurrenceIndex = (dayOfMonth - 1) / 7：同一 dayOfWeek 在当月出现的次序（0-based，例如 8/2→0、8/9→1、8/16→2……）。
     * 候选不足一轮时取模循环；无候选返回 null。
     */
    private fun pickDisplayCover(date: LocalDate, covers: List<SubjectEntity>): SubjectEntity? {
        if (covers.isEmpty()) return null
        val occurrenceIndex = (date.dayOfMonth - 1) / 7
        return covers[occurrenceIndex % covers.size]
    }

    /** 解析 Date 字符串。使用 [TimeUtils.parseDate]。 */
    fun parseDate(dateStr: String?): LocalDate? = TimeUtils.parseDate(dateStr)

    /** 生成时间线条目，按时间倒序。 */
    fun computeTimelineEvents(items: List<CollectionWithSubject>): List<TimelineEvent> {
        if (items.isEmpty()) return emptyList()

        val events = mutableListOf<TimelineEvent>()

        fun actionText(status: WatchStatus): String = when (status) {
            WatchStatus.WATCHING -> "开始看"
            WatchStatus.COMPLETED -> "看过了"
            WatchStatus.ON_HOLD -> "搁置了"
            WatchStatus.DROPPED -> "抛弃了"
            WatchStatus.PLAN_TO_WATCH -> "想看"
            else -> "更新了"
        }

        items.forEach { item ->
            val c = item.collection
            val s = item.subject

            val createdDate = Instant.ofEpochMilli(c.createTime).atZone(ZoneId.systemDefault()).toLocalDate()
            events.add(TimelineEvent(
                date = createdDate,
                action = TimelineAction.ADDED,
                collectionWithSubject = item,
                actionLabel = "收藏了 ${s.displayTitle}",
            ))

            c.startDate?.let { sd ->
                events.add(TimelineEvent(
                    date = sd,
                    action = TimelineAction.STARTED,
                    collectionWithSubject = item,
                    actionLabel = "${actionText(c.status)} ${s.displayTitle}",
                ))
            }

            c.finishDate?.let { fd ->
                events.add(TimelineEvent(
                    date = fd,
                    action = TimelineAction.COMPLETED,
                    collectionWithSubject = item,
                    actionLabel = "看过了 ${s.displayTitle}",
                ))
            }
        }

        return events.sortedByDescending { it.date }
    }

    /**
     * 根据每集数据计算「已播到第几集 / 总集数 / 下一集 / 每集讨论热度」。
     * 每集无数据时回退 [subjectTotalEpisodes]；两者都无则返回空态（UI 显示集数未知）。
     */
    fun computeEpisodeAirState(
        episodes: List<EpisodeInfo>,
        subjectTotalEpisodes: Int?,
        today: LocalDate = LocalDate.now(),
    ): EpisodeAirState {
        val main = episodes
            .filter { it.type == 0 && it.sort > 0 }
            .sortedBy { it.sort }
        val total = if (main.isNotEmpty()) {
            main.maxOf { it.sort }.toInt().coerceAtLeast(subjectTotalEpisodes ?: 0)
        } else {
            subjectTotalEpisodes ?: 0
        }
        val aired = main.count { ep ->
            if (ep.status == "Air" || ep.status == "Today") true
            else {
                val d = ep.airdate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                d != null && !d.isAfter(today)
            }
        }
        val nextAirDate = main.firstOrNull { ep ->
            !(ep.status == "Air" || ep.status == "Today") &&
                ep.airdate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it.isAfter(today) } == true
        }?.airdate
        val comments = main.map { it.comment }
        val maxC = comments.maxOrNull() ?: 0
        val minC = comments.minOrNull() ?: 0
        val heat = if (maxC > minC) {
            comments.map { (it - minC).toFloat() / (maxC - minC) }
        } else {
            comments.map { 0f }
        }
        return EpisodeAirState(
            airedCount = aired,
            totalCount = total,
            nextAirDate = nextAirDate,
            heat = heat,
        )
    }
}

/** 每集播出/热力状态（统计页放送条目）。 */
data class EpisodeAirState(
    val airedCount: Int = 0,
    val totalCount: Int = 0,
    val nextAirDate: String? = null,
    val heat: List<Float> = emptyList(),
)
