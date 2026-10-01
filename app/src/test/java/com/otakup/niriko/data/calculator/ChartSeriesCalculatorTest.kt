package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.model.stats.MonthlyStats
import com.otakup.niriko.data.model.stats.RatingComparison
import com.otakup.niriko.data.model.stats.RatingDistribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [ChartSeriesCalculator] 的 JVM 单测：图表渲染不测，这里覆盖全部可测纯逻辑。 */
class ChartSeriesCalculatorTest {

    @Test
    fun `累计值按顺序逐个累加`() {
        assertEquals(listOf(5, 8, 16, 22), ChartSeriesCalculator.cumulativeCounts(listOf(5, 3, 8, 6)))
    }

    @Test
    fun `累计值空输入返回空`() {
        assertEquals(emptyList<Int>(), ChartSeriesCalculator.cumulativeCounts(emptyList()))
    }

    @Test
    fun `累计值允许出现零与负增长`() {
        assertEquals(listOf(0, 0, 4), ChartSeriesCalculator.cumulativeCounts(listOf(0, 0, 4)))
    }

    @Test
    fun `月度趋势累计值只取count`() {
        val trend = listOf(
            MonthlyStats(label = "2024-01", year = 2024, month = 1, count = 5),
            MonthlyStats(label = "2024-02", year = 2024, month = 2, count = 3),
            MonthlyStats(label = "2024-10", year = 2024, month = 10, count = 6),
        )
        assertEquals(listOf(5, 8, 14), ChartSeriesCalculator.monthlyCumulative(trend))
    }

    @Test
    fun `评分差为个人减Bangumi`() {
        val row = RatingComparison(subjectId = 1, title = "孤独摇滚！", myRating = 9.5f, bangumiRating = 8.4f)
        assertEquals(1.1f, ChartSeriesCalculator.ratingDifference(row)!!, 0.0001f)
    }

    @Test
    fun `评分差缺失任一侧返回null`() {
        assertNull(ChartSeriesCalculator.ratingDifference(
            RatingComparison(subjectId = 1, title = "A", myRating = 9f, bangumiRating = null)))
        assertNull(ChartSeriesCalculator.ratingDifference(
            RatingComparison(subjectId = 2, title = "B", myRating = null, bangumiRating = 8f)))
    }

    @Test
    fun `评分差批量结果保留null占位`() {
        val rows = listOf(
            RatingComparison(1, "A", 9f, 8f),
            RatingComparison(2, "B", null, 8f),
            RatingComparison(3, "C", 7f, 8.5f),
        )
        val diffs = ChartSeriesCalculator.ratingDifferences(rows)
        assertEquals(3, diffs.size)
        assertEquals(1f, diffs[0]!!, 0.0001f)
        assertNull(diffs[1])
        assertEquals(-1.5f, diffs[2]!!, 0.0001f)
    }

    @Test
    fun `分桶起点解析`() {
        assertEquals(0, ChartSeriesCalculator.bucketStart("0-1"))
        assertEquals(9, ChartSeriesCalculator.bucketStart("9-10"))
        assertEquals(7, ChartSeriesCalculator.bucketStart(" 7-8 "))
        assertEquals(Int.MAX_VALUE, ChartSeriesCalculator.bucketStart("未知"))
    }

    @Test
    fun `分桶排序按数值而非字典序`() {
        val buckets = listOf(
            RatingDistribution("9-10", 6),
            RatingDistribution("10-11", 1),
            RatingDistribution("0-1", 2),
        )
        assertEquals(
            listOf("0-1", "9-10", "10-11"),
            ChartSeriesCalculator.sortBuckets(buckets).map { it.range },
        )
    }

    @Test
    fun `分桶合并补齐缺失并排序`() {
        val mine = listOf(
            RatingDistribution("9-10", 20),
            RatingDistribution("0-1", 1),
        )
        val bangumi = listOf(
            RatingDistribution("8-9", 30),
            RatingDistribution("9-10", 28),
        )
        val merged = ChartSeriesCalculator.mergeRatingBuckets(mine, bangumi)
        assertEquals(listOf("0-1", "8-9", "9-10"), merged.map { it.range })
        assertEquals(1, merged[0].myCount)
        assertEquals(0, merged[0].bangumiCount)
        assertEquals(0, merged[1].myCount)
        assertEquals(30, merged[1].bangumiCount)
        assertEquals(20, merged[2].myCount)
        assertEquals(28, merged[2].bangumiCount)
    }

    @Test
    fun `分桶合并同一标签重复累加`() {
        val merged = ChartSeriesCalculator.mergeRatingBuckets(
            mine = listOf(RatingDistribution("9-10", 2), RatingDistribution("9-10", 3)),
            bangumi = emptyList(),
        )
        assertEquals(1, merged.size)
        assertEquals(5, merged[0].myCount)
        assertEquals(0, merged[0].bangumiCount)
    }

    @Test
    fun `分桶合并空输入返回空`() {
        assertEquals(
            emptyList<ChartSeriesCalculator.RatingBucket>(),
            ChartSeriesCalculator.mergeRatingBuckets(emptyList(), emptyList()),
        )
    }
}
