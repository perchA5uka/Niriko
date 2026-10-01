package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.model.stats.MonthlyStats
import com.otakup.niriko.data.model.stats.RatingComparison
import com.otakup.niriko.data.model.stats.RatingDistribution

/**
 * 图表系列纯函数：为 Vico 图表（ui/stats/NirikoBarChart.kt 等）准备数据。
 *
 * 与 [StatsCalculator] 一样只做纯计算、不依赖 Android 框架，便于 JVM 单测覆盖；
 * 图表渲染本身不写单测（由截图基线覆盖），因此这里承担全部可测逻辑：
 * 月度累计值、评分对比差值、评分分桶合并与排序。
 */
object ChartSeriesCalculator {

    /** 同一坐标系下的一组评分分桶（个人 vs Bangumi）。 */
    data class RatingBucket(
        val range: String,
        val myCount: Int,
        val bangumiCount: Int,
    )

    /** 逐个累加，返回与输入等长的累计序列。 */
    fun cumulativeCounts(counts: List<Int>): List<Int> {
        val result = ArrayList<Int>(counts.size)
        var acc = 0
        counts.forEach { count ->
            acc += count
            result.add(acc)
        }
        return result
    }

    /** 月度趋势累计值（保留 MonthlyStats.label 截断语义，仅取 count）。 */
    fun monthlyCumulative(trend: List<MonthlyStats>): List<Int> =
        cumulativeCounts(trend.map { it.count })

    /** 评分差（个人 − Bangumi）；任一侧缺失返回 null。 */
    fun ratingDifference(row: RatingComparison): Float? =
        if (row.myRating == null || row.bangumiRating == null) {
            null
        } else {
            row.myRating - row.bangumiRating
        }

    /** 批量评分差，顺序与输入一致（null 保留占位）。 */
    fun ratingDifferences(rows: List<RatingComparison>): List<Float?> =
        rows.map { ratingDifference(it) }

    /**
     * 解析分桶区间起点，用于稳定排序（"9-10" → 9）。
     * 无法解析的区间排在最后，保证未知标签不会插到数字区间中间。
     */
    fun bucketStart(range: String): Int =
        range.substringBefore('-').trim().toIntOrNull() ?: Int.MAX_VALUE

    /** 按分桶区间起点排序（同起点按标签字典序）。 */
    fun sortBuckets(buckets: List<RatingDistribution>): List<RatingDistribution> =
        buckets.sortedWith(compareBy({ bucketStart(it.range) }, { it.range }))

    /**
     * 合并个人 / Bangumi 两组评分分布，输出同一坐标系下的分组柱数据：
     * 取两侧区间并集、按 [bucketStart] 排序、缺失桶补 0，同一标签重复出现时累加。
     */
    fun mergeRatingBuckets(
        mine: List<RatingDistribution>,
        bangumi: List<RatingDistribution>,
    ): List<RatingBucket> {
        val myCounts = LinkedHashMap<String, Int>()
        mine.forEach { myCounts[it.range] = (myCounts[it.range] ?: 0) + it.count }
        val bangumiCounts = LinkedHashMap<String, Int>()
        bangumi.forEach { bangumiCounts[it.range] = (bangumiCounts[it.range] ?: 0) + it.count }
        val ranges = LinkedHashSet<String>().apply {
            addAll(myCounts.keys)
            addAll(bangumiCounts.keys)
        }
        return ranges
            .sortedWith(compareBy({ bucketStart(it) }, { it }))
            .map { RatingBucket(it, myCounts[it] ?: 0, bangumiCounts[it] ?: 0) }
    }
}
