package com.otakup.niriko.data.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeRatingAnalyzerTest {

    private fun points(vararg scores: Float) = scores.mapIndexed { index, score ->
        EpisodeRatingAnalyzer.RatingPoint(
            epId = index + 1L,
            ep = (index + 1).toDouble(),
            label = "EP${index + 1}",
            score = score,
            votes = 100,
        )
    }

    @Test
    fun `空数据返回空统计`() {
        val stats = EpisodeRatingAnalyzer.analyze(emptyList())
        assertEquals(0, stats.count)
        assertTrue(stats.isEmpty)
        assertEquals(TrendDirection.FLAT, stats.direction)
    }

    @Test
    fun `均值极值与数量正确`() {
        val stats = EpisodeRatingAnalyzer.analyze(points(6f, 8f, 10f))
        assertEquals(3, stats.count)
        assertEquals(8f, stats.average, 0.001f)
        assertEquals(10f, stats.max!!.score, 0.001f)
        assertEquals(6f, stats.min!!.score, 0.001f)
    }

    @Test
    fun `上升序列斜率为正且判为上升`() {
        val stats = EpisodeRatingAnalyzer.analyze(points(6f, 7f, 8f, 9f))
        assertTrue(stats.slope > 0f)
        assertEquals(TrendDirection.RISING, stats.direction)
    }

    @Test
    fun `下降序列斜率为负且判为下降`() {
        val stats = EpisodeRatingAnalyzer.analyze(points(9f, 8f, 7f, 6f))
        assertTrue(stats.slope < 0f)
        assertEquals(TrendDirection.FALLING, stats.direction)
    }

    @Test
    fun `完全平稳判为FLAT`() {
        val stats = EpisodeRatingAnalyzer.analyze(points(8f, 8f, 8f, 8f))
        assertEquals(0f, stats.slope, 0.0001f)
        assertEquals(TrendDirection.FLAT, stats.direction)
    }

    @Test
    fun `波动为标准差`() {
        // 6,6,10,10 → 均值 8，总体标准差 2
        val stats = EpisodeRatingAnalyzer.analyze(points(6f, 6f, 10f, 10f))
        assertEquals(2f, stats.volatility, 0.001f)
    }

    @Test
    fun `移动平均在边界使用可用点`() {
        val ma = EpisodeRatingAnalyzer.movingAverage(listOf(2f, 4f, 6f), 3)
        assertEquals(3f, ma[0], 0.001f)   // (2+4)/2
        assertEquals(4f, ma[1], 0.001f)   // (2+4+6)/3
        assertEquals(5f, ma[2], 0.001f)   // (4+6)/2
    }

    @Test
    fun `低票数过滤返回被过滤数量`() {
        val list = listOf(
            EpisodeRatingAnalyzer.RatingPoint(1, 1.0, "a", 7f, 5),
            EpisodeRatingAnalyzer.RatingPoint(2, 2.0, "b", 7f, 50),
            EpisodeRatingAnalyzer.RatingPoint(3, 3.0, "c", 7f, 500),
        )
        val (kept, removed) = EpisodeRatingAnalyzer.filterLowVotes(list, 50)
        assertEquals(2, kept.size)
        assertEquals(1, removed)
    }

    @Test
    fun `高光与崩坏回按偏离阈值筛出`() {
        val list = points(5f, 8f, 8f, 9f)
        val (high, low) = EpisodeRatingAnalyzer.highlights(list, threshold = 0.5f)
        assertTrue(high.any { it.score == 9f })
        assertTrue(low.any { it.score == 5f })
    }

    @Test
    fun `单集数据斜率为0不崩溃`() {
        val stats = EpisodeRatingAnalyzer.analyze(points(7f))
        assertEquals(0f, stats.slope, 0.0001f)
        assertEquals(1, stats.count)
    }
}
