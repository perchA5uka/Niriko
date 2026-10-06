package com.otakup.niriko.data.seasonal

import com.otakup.niriko.data.discover.BrowseFilter
import com.otakup.niriko.data.discover.DiscoveryFeed
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.remote.CalendarDaySchedule
import com.otakup.niriko.util.AiringStatus
import com.otakup.niriko.util.TimeUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 「当季热门」的纯计算部分（第 5 轮 D26/D27）。
 *
 * ## 为什么需要它
 *
 * 改造前「当季热门」被实现成了「**本季度开播**」：
 * `TrendingCalculator.computeSeasonDateRange()` 取当前季度的 air_date 窗口，
 * 再配合 `subjectRepository.search(airDate = 本季度)` —— 于是上季度开播、
 * 至今仍在播的长连载（如播一年的特摄《假面骑士zzz》）**完全不在窗口里**，永不展示。
 *
 * 正确的语义是「**本季**」= 本季度内还在播、或在本季度内完结的作品
 * （与 Bangumi 的 /calendar（每日放送）同源，另外放宽到「本季刚完结」）。
 * 放宽这一步是用户实测倒逼出来的：播一年的特摄《假面骑士zzz》在实测当天
 * 已经播完，按「必须仍在播」它永远进不来，而它在 Bangumi-master 里是看得到的。
 *
 * ## 为什么不能复用 AiringStatus.getPhase
 *
 * `getPhase` 对「无总集数的长期连载」用「180 天内首播」当 fallback，
 * 会把播一年的特摄误判成 COMPLETED —— 这正是漏掉长连载的同一个坑。
 * 这里用独立的 [isProbablyAiring]：有集数就按集数估算完结日，没集数就给足 [DEFAULT_WINDOW_DAYS]。
 *
 * 全部方法都是纯函数，可在 JVM 单测里直接跑。
 */
object SeasonalTrendingCalculator {

    /** 无总集数时的「可能仍在放送」窗口（天）。一整年番约 365 天，留出余量。 */
    const val DEFAULT_WINDOW_DAYS = 400L

    /**
     * 「本季」的起始日 = 当前季度的第一天（1/4/7/10 月 1 日）。
     *
     * ## 为什么用季度而不是「今天」
     *
     * 用户点名要求《假面骑士zzz》必须出现在当季热门里 —— 它是每周一集、播一年的特摄。
     * 但**按「必须仍在播」它是进不来的**：实测当天（2026-09-19）它的估算完结日已经过去
     * （2025-09 开播 + 50 话 x 7 天 ≈ 2026-08 下旬），它属于「**本季刚完结**」。
     *
     * 因此判定放宽为「**本季度内还在播、或在本季度内完结**」：
     * - 播到一半的长连载 → 完结日在未来 → 收；
     * - 本季刚完结（如 zzz） → 完结日 >= 本季度起始日 → 收；
     * - 上季度及更早完结的短番 → 完结日 < 本季度起始日 → 不收（否则一年前的番也会混进来）。
     */
    fun quarterStart(today: LocalDate): LocalDate {
        val startMonth = when (today.monthValue) {
            in 1..3 -> 1
            in 4..6 -> 4
            in 7..9 -> 7
            else -> 10
        }
        return LocalDate.of(today.year, startMonth, 1)
    }

    /**
     * 判断作品是否属于「**本季**」：本季度内还在播，或在本季度内完结。
     *
     * 注意：/calendar 明确返回的条目**不需要**过这一关（日历本身就是权威），
     * 本函数只用于「按开播日 + 集数推算」的兜底路径。
     *
     * 判定放宽的来龙去脉见 [quarterStart]。
     */
    fun isProbablyAiring(
        subject: SubjectEntity,
        today: LocalDate,
        windowDays: Long = DEFAULT_WINDOW_DAYS,
    ): Boolean {
        val airDate = TimeUtils.parseDate(subject.airDate) ?: return false
        if (airDate.isAfter(today)) return false
        if (AiringStatus.isMovieOrOva(subject)) return false
        val total = subject.totalEpisodes
        return if (total != null && total > 0) {
            val end = AiringStatus.getEstimatedEndDate(airDate, total)
            // 仍在播（end 在未来）或本季度内刚完结，都算「本季」
            end != null && !end.isBefore(quarterStart(today))
        } else {
            // 没有总集数的长期连载：只要开播日在窗口内就保留
            !airDate.isBefore(today.minusDays(windowDays))
        }
    }


    /** /calendar 的分组结果 → 当季条目（权威「正在放送」，不做在播过滤）。 */
    fun fromCalendar(
        calendar: List<CalendarDaySchedule>,
        today: LocalDate = LocalDate.now(),
    ): List<SeasonalItem> = calendar.flatMap { day ->
        day.subjects.mapNotNull { subject ->
            if (subject.subjectId <= 0L) return@mapNotNull null
            SeasonalItem(
                subject = subject,
                weekday = day.dayOfWeek,
                confirmed = true,
            )
        }
    }.distinctBy { it.subject.subjectId }

    /** 检索结果 → 当季条目（推算路径：先过 [isProbablyAiring]）。 */
    fun fromSearch(
        subjects: List<SubjectEntity>,
        today: LocalDate = LocalDate.now(),
        windowDays: Long = DEFAULT_WINDOW_DAYS,
    ): List<SeasonalItem> = subjects
        .filter { it.subjectId > 0L }
        .filter { isProbablyAiring(it, today, windowDays) }
        .map { subject ->
            SeasonalItem(
                subject = subject,
                weekday = WEEKDAYS.getOrNull((subject.airWeekday ?: 0) - 1),
                confirmed = false,
            )
        }
        .distinctBy { it.subject.subjectId }

    /**
     * 合并两条来源。
     *
     * **同一 id 以日历为准**（它同时给出了放送星期，且是权威的「正在放送」）。
     */
    fun merge(calendarItems: List<SeasonalItem>, fallbackItems: List<SeasonalItem>): List<SeasonalItem> {
        val merged = LinkedHashMap<Long, SeasonalItem>()
        calendarItems.forEach { merged[it.subject.subjectId] = it }
        fallbackItems.forEach { merged.putIfAbsent(it.subject.subjectId, it) }
        return merged.values.toList()
    }

    /**
     * 展示字段补全（纯函数）：按 id 把 [donors] 里**非空**的字段补进每条候选的空缺处。
     *
     * ## 为什么需要它（用户报告：「发现页部分动画类型卡片不展示简介」）
     *
     * 实测 Bangumi `GET /calendar` 返回的每个条目 `summary` 都是**空串**
     * （2026-10 抽样 70 条，70 条全空），而日历是「正在放送」的权威来源、
     * 且 [merge] 规定同一 id 以日历为准 —— 于是**只在日历里出现的长连载**
     * （航海王、名侦探柯南）卡片永远没有简介：它们在 400 天开播窗口之外
     * （开播于 1999 年），放宽窗口的检索那条路也补不到。
     *
     * 这里不改候选池、不改类型过滤、不改排序：只把同一 id 已有条目的空字段补上，
     * 让卡片拿到简介 / 集数 / 评分。放送星期与 [SeasonalItem.confirmed] 仍以日历为准。
     *
     * @param items 候选（合并后的池子或 [select] 选出的展示条目）
     * @param donors subjectId → 字段更全的条目（检索结果；可以缺、可以整个为空）
     */
    fun fillBlankFields(
        items: List<SeasonalItem>,
        donors: Map<Long, SubjectEntity>,
    ): List<SeasonalItem> {
        if (donors.isEmpty()) return items
        return items.map { item ->
            val donor = donors[item.subject.subjectId] ?: return@map item
            item.copy(subject = item.subject.fillBlankFieldsFrom(donor))
        }
    }

    /**
     * 是否值得为「补简介」多跑一次检索：只看**可能被展示**的条目
     * （类型过滤 + 排序 + 质量门槛 + 截断到 [targetSize] 之后）是否还有拿不到简介的。
     *
     * 看不见的候选不值得多一次请求；当季番普遍能由手上的兜底检索结果补上，
     * 只有开播多年的长连载才会让这里返回 true。
     */
    fun needsFieldDonors(
        items: List<SeasonalItem>,
        types: List<Int>,
        sort: SeasonalSort,
        targetSize: Int,
        donors: Map<Long, SubjectEntity>,
    ): Boolean {
        val shown = select(items, types, sort, targetSize).items
        return shown.any { item ->
            item.subject.summary.isNullOrBlank() &&
                donors[item.subject.subjectId]?.summary.isNullOrBlank() != false
        }
    }

    /** 把 [donor] 里非空的字段补进本体的空缺处；**已有的值绝不覆盖**。 */
    private fun SubjectEntity.fillBlankFieldsFrom(donor: SubjectEntity): SubjectEntity = copy(
        titleCN = titleCN.orIfBlank(donor.titleCN),
        summary = summary.orIfBlank(donor.summary),
        coverUrl = coverUrl.orIfBlank(donor.coverUrl),
        totalEpisodes = totalEpisodes ?: donor.totalEpisodes,
        platform = platform.orIfBlank(donor.platform),
        volumes = volumes ?: donor.volumes,
        airDate = airDate.orIfBlank(donor.airDate),
        ratingScore = ratingScore ?: donor.ratingScore,
        ratingTotal = ratingTotal ?: donor.ratingTotal,
        rank = rank ?: donor.rank,
        series = series ?: donor.series,
        tags = tags.ifEmpty { donor.tags },
    )

    private fun String?.orIfBlank(fallback: String?): String? =
        if (isNullOrBlank()) fallback?.takeIf { it.isNotBlank() } else this

    /**
     * 类型筛选（第 6 轮 §2.4）：按 Bangumi type 整数集合过滤。
     *
     * 类型来源是**顶部类型行**（[SeasonalTypes.of]），不再是榜单自己的 chips。
     * 空集合 = 什么都不留（调用方不该传空）。
     */
    fun filterByTypes(items: List<SeasonalItem>, types: List<Int>): List<SeasonalItem> {
        if (types.isEmpty()) return emptyList()
        val allowed = types.toSet()
        return items.filter { BrowseFilter.bangumiTypeOf(it.subject.type) in allowed }
    }

    /**
     * 当季热门的核心选取（第 6 轮 §2.3 的 A 方案：B 方案 next.bgm.tv 本轮不接）。
     *
     * 流水线：
     * 1. 类型过滤（顶部类型行）；
     * 2. **类内排序**：按用户选的 [sort]（默认热度 = 评分人数）排每一类，得到「类内人气序」；
     * 3. 交给 [DiscoveryFeed]：质量门槛（rating_total ≥ 100）+ 类内归一 + 多样性重排
     *    （窗口 10 内同类 ≤3、连续 ≤2、小类保底 3）→ 取前 [targetSize]（默认 30）。
     *
     * 「不足就是不足」：候选不够就返回实际条数，绝不填充（用户已否掉历史排名补位）。
     *
     * @param items 候选池（/calendar ∪ 放宽窗口检索的合并结果）。
     * @param types 要保留的 Bangumi type 整数（见 [SeasonalTypes]）。
     * @param sort 类内排序（热度/评分/排名/开播日/放送日）。
     */
    fun select(
        items: List<SeasonalItem>,
        types: List<Int>,
        sort: SeasonalSort,
        targetSize: Int = DiscoveryFeed.TARGET_SIZE,
        minRatingTotal: Int = DiscoveryFeed.MIN_RATING_TOTAL,
    ): SeasonalSelection {
        val typed = filterByTypes(items, types)
        val byId = typed.associateBy { it.subject.subjectId }
        // groupBy 返回 LinkedHashMap → 类型顺序 = 候选池里的首次出现顺序（确定性）
        val groups = typed
            .groupBy { it.subject.type }
            .map { (type, list) ->
                DiscoveryFeed.TypeCandidates(type, this.sort(list, sort).map { it.subject })
            }
        val result = DiscoveryFeed.build(
            groups = groups,
            params = DiscoveryFeed.Params(targetSize = targetSize, minRatingTotal = minRatingTotal),
        )
        return SeasonalSelection(
            items = result.items.mapNotNull { byId[it.subject.subjectId] },
            candidateCount = result.candidateCount,
            rejectedByQuality = result.rejectedByQuality,
            totalTyped = typed.size,
        )
    }

    /** 排序（不可比较的字段一律排到末尾，绝不因为缺字段而丢条目）。 */
    fun sort(items: List<SeasonalItem>, sort: SeasonalSort): List<SeasonalItem> = when (sort) {
        SeasonalSort.HEAT -> items.sortedWith(
            compareByDescending<SeasonalItem> { it.subject.ratingTotal ?: 0 }
                .thenByDescending { it.subject.ratingScore ?: 0f }
        )
        SeasonalSort.SCORE -> items.sortedWith(
            compareByDescending<SeasonalItem> { it.subject.ratingScore ?: 0f }
                .thenByDescending { it.subject.ratingTotal ?: 0 }
        )
        SeasonalSort.RANK -> items.sortedWith(
            compareBy<SeasonalItem> { it.subject.rank ?: Int.MAX_VALUE }
                .thenByDescending { it.subject.ratingScore ?: 0f }
        )
        SeasonalSort.AIR_DATE -> items.sortedWith(
            compareByDescending<SeasonalItem> { it.subject.airDate ?: "" }
        )
        SeasonalSort.WEEKDAY -> items.sortedWith(
            compareBy<SeasonalItem> { it.subject.airWeekday ?: Int.MAX_VALUE }
                .thenByDescending { it.subject.ratingTotal ?: 0 }
        )
    }

    /**
     * 按关键词过滤（**纯内存、零请求**）。
     *
     * ## 当前状态：暂无调用方（第 5 轮返工后保留的工具函数）
     *
     * 它曾服务「当季热门」榜头的一个列表内搜索框，用户明确要求删掉那个框
     * （「当季热门是显示热门作品的功能，不是播放列表检索」），因此 UI 已移除。
     * 函数本身是纯的、有单测覆盖，保留以备将来给「作品库 / 收藏」这类真正需要检索的
     * 场景复用；**不要在当季热门里再把它接回去**。
     *
     * ## 原本的理由
     *
     * 「正在放送」的候选有两百条左右（/calendar 全量 + 兜底检索）。
     * 按热度排序后，一部播到一半的特摄（评分人数几百）会被评分人数上万的当红番
     * 挤到很后面 —— 用户会以为「它不在当季热门里」。
     * 列表内检索是让任何一部在播作品都能被**立刻找到**的最低成本手段。
     */
    fun filterByQuery(items: List<SeasonalItem>, query: String): List<SeasonalItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { item ->
            val s = item.subject
            s.title.lowercase().contains(q) ||
                s.titleCN?.lowercase()?.contains(q) == true ||
                s.displayTitle.lowercase().contains(q)
        }
    }

    /** 兜底检索的日期窗口：[今天 - windowDays, 明天)。 */
    fun fallbackAirDateRange(
        today: LocalDate = LocalDate.now(),
        windowDays: Long = DEFAULT_WINDOW_DAYS,
    ): List<String> = listOf(
        ">=" + today.minusDays(windowDays).toString(),
        "<" + today.plusDays(1).toString(),
    )

    /** Bangumi air_weekday：1=周一 … 7=周日。 */
    private val WEEKDAYS = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
    )
}

/**
 * [SeasonalTrendingCalculator.select] 的结果（第 6 轮 §2.3）。
 *
 * @property items 类内人气序 + 质量门槛 + 多样性重排后的条目（≤ targetSize）。
 * @property candidateCount 「候选 N 部」里的 N：类型过滤 + 合并去重后、**质量门槛之前**的条数。
 * @property rejectedByQuality 被质量门槛（rating_total < 100）挡掉的条数。
 * @property totalTyped 类型过滤后的条数（与 candidateCount 相同，保留可读性）。
 */
data class SeasonalSelection(
    val items: List<SeasonalItem>,
    val candidateCount: Int,
    val rejectedByQuality: Int,
    val totalTyped: Int,
)
