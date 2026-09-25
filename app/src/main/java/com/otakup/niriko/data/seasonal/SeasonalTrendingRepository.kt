package com.otakup.niriko.data.seasonal

import android.util.Log
import com.otakup.niriko.data.discover.DiscoveryFeed
import com.otakup.niriko.data.remote.CalendarCache
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.data.repository.SubjectRepository
import java.time.LocalDate

private const val TAG = "SeasonalTrending"

/**
 * 「当季热门」数据编排（第 5 轮 D26；第 6 轮 §2.3 改成**人气选取**）。
 *
 * ## 语义
 *
 * 当季热门 = 「**正在放送**」里**当下最有人气**的那些，不是「本季度开播」，
 * 也不是「把所有正在放送的列出来」。
 *
 * ## 候选池（第 5 轮保留，未改）
 *
 * 1. **日历接口 /calendar**（权威「正在放送」，且附带放送星期）；
 * 2. **放宽窗口的检索 + 在播推算**（air_date ∈ [今天-400 天, 明天)，
 *    再用 [SeasonalTrendingCalculator.isProbablyAiring] 判断未完结）。
 *
 * 两条取并集、按 subjectId 去重（同 id 以日历为准）。这样即使日历只回动画，
 * 长连载的三次元也不会丢（《假面骑士zzz》就是靠这条救回来的）。
 *
 * ## 选取（第 6 轮 §2.3 A 方案）
 *
 * 候选池 → 类型过滤（顶部类型行）→ 类内人气序 → [DiscoveryFeed]
 * （质量门槛 rating_total ≥ 100 + 多样性重排）→ 取前 [DiscoveryFeed.TARGET_SIZE]（30）。
 *
 * 改造前是「全量 300 条 + 只在排序 chips 上体现人气」—— 用户看到的是一张放送清单。
 *
 * ## 不做写库
 *
 * 这里**刻意不调用** subjectRepository.upsertAll：/calendar 的条目不含本地补充列
 * （biliScore / pinyinKey / tags 等），upsert 会把它们抹成 null
 * （本项目在 getDetail 上已经踩过同类问题）。日历数据只用于展示。
 */
class SeasonalTrendingRepository(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val subjectRepository: SubjectRepository,
    private val calendarCache: CalendarCache = CalendarCache(),
    /** 每个类型在兜底检索里取多少条（类内候选）。 */
    private val perTypeLimit: Int = PER_TYPE_LIMIT,
) {

    /**
     * 加载当季热门。
     *
     * @param types 要保留的 Bangumi type 整数（来自顶部类型行，见 [SeasonalTypes.of]）。
     * @param sort 类内排序方式（默认热度 = 评分人数）。
     * @param targetSize 最终展示条数上限（默认 30）。**不足就是不足，不做任何填充**。
     * @param nsfw 是否包含 R18（null = 不传，交给数据源默认）。
     */
    suspend fun load(
        types: List<Int> = SeasonalTypes.ALL,
        sort: SeasonalSort = SeasonalSort.HEAT,
        targetSize: Int = DiscoveryFeed.TARGET_SIZE,
        nsfw: Boolean? = null,
        today: LocalDate = LocalDate.now(),
    ): SeasonalTrendingResult {
        // —— ① 权威：/calendar ——
        val calendarAttempt = runCatching {
            calendarCache.load { remoteDataSource.getCalendar() }
        }.onFailure { Log.w(TAG, "calendar unavailable", it) }
        val snapshot = calendarAttempt.getOrNull()
        val calendarFailed = calendarAttempt.isFailure

        val calendarItems = snapshot?.entries
            ?.let { SeasonalTrendingCalculator.fromCalendar(it, today) }
            .orEmpty()

        // —— ② 兜底：放宽窗口的检索 + 在播推算 ——
        val confirmedIds = calendarItems.mapTo(HashSet()) { it.subject.subjectId }
        val fallback = loadFallback(types, nsfw, today)
        val fallbackItems = fallback.items
            .filter { it.subjectId !in confirmedIds }
            .let { SeasonalTrendingCalculator.fromSearch(it, today) }

        // —— ③ 合并 → 类型过滤 → 人气选取（门槛 + 多样性）→ 截断 ——
        val merged = SeasonalTrendingCalculator.merge(calendarItems, fallbackItems)
        val selection = SeasonalTrendingCalculator.select(
            items = merged,
            types = types,
            sort = sort,
            targetSize = targetSize,
        )

        if (selection.items.isEmpty()) {
            Log.w(
                TAG,
                "empty seasonal: calendar=" + (snapshot?.entries?.size ?: 0) +
                    " fallback=" + fallbackItems.size +
                    " typed=" + selection.totalTyped +
                    " rejectedByQuality=" + selection.rejectedByQuality,
            )
        }

        // 第 6 轮 R7：两条来源都失败 = 网络失败（上层必须写 state.error）。
        // 只要有一条成功（含过期日历缓存）就算拿到了数据，不报错。
        val loadFailed = calendarFailed && fallback.allFailed && types.isNotEmpty()

        return SeasonalTrendingResult(
            items = selection.items,
            all = merged,
            totalCandidates = selection.candidateCount,
            calendarAvailable = snapshot != null,
            stale = snapshot?.stale == true,
            loadFailed = loadFailed,
        )
    }

    /**
     * 兜底检索：按类型逐个查，合并去重。
     *
     * 窗口是「今天 - 400 天 ~ 明天」而不是「本季度」—— 这正是长连载能被捞回来的原因。
     * 排序用 heat（Bangumi 侧的热度），这样即使命中很多，先截断也留下了热门的那些。
     */
    private suspend fun loadFallback(
        types: List<Int>,
        nsfw: Boolean?,
        today: LocalDate,
    ): FallbackResult {
        val airDate = SeasonalTrendingCalculator.fallbackAirDateRange(today)
        val merged = LinkedHashMap<Long, com.otakup.niriko.data.local.entity.SubjectEntity>()
        var failedTypes = 0
        types.forEach { bangumiType ->
            val attempt = runCatching {
                subjectRepository.search(
                    keyword = "",
                    type = bangumiType,
                    airDate = airDate,
                    nsfw = nsfw,
                    sort = "heat",
                    limit = perTypeLimit,
                )
            }.onFailure { Log.w(TAG, "fallback search failed type=" + bangumiType, it) }
            if (attempt.isFailure) failedTypes++
            attempt.getOrDefault(emptyList()).forEach { merged.putIfAbsent(it.subjectId, it) }
        }
        return FallbackResult(
            items = merged.values.toList(),
            allFailed = types.isNotEmpty() && failedTypes == types.size,
        )
    }

    /** 兜底检索的结果：条目 + 是否**每个类型都失败**（第 6 轮 R7 用来判定网络失败）。 */
    private data class FallbackResult(
        val items: List<com.otakup.niriko.data.local.entity.SubjectEntity>,
        val allFailed: Boolean,
    )

    companion object {
        /**
         * 类内候选的抓取量（每个类型）。
         *
         * 现在是**类内候选**而不是展示量：展示量由 [DiscoveryFeed.TARGET_SIZE] 决定（30）。
         */
        const val PER_TYPE_LIMIT = 100
    }
}
