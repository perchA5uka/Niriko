package com.otakup.niriko.data.calculator

import kotlin.math.abs
import kotlin.math.sqrt

/** 走势方向。 */
enum class TrendDirection { RISING, FALLING, FLAT }

/**
 * 评分走势分析（**纯函数，可单测**，无 Android 依赖）。
 *
 * 供详情页「评分走势」区块使用：一条曲线 + 一句结论
 * （平均 / 最高 / 最低 / 趋势斜率 / 波动 / 移动平均）。
 */
object EpisodeRatingAnalyzer {

    /** 曲线上的一个点。 */
    data class RatingPoint(
        val epId: Long,
        /** 集号（展示与 X 轴用）。 */
        val ep: Double,
        val label: String,
        val score: Float,
        val votes: Int? = null,
        val sourceId: String = "tmdb",
    )

    data class TrendStats(
        val count: Int,
        val average: Float,
        val max: RatingPoint?,
        val min: RatingPoint?,
        /** 最小二乘斜率（每集变化的分值）。 */
        val slope: Float,
        /** 评分标准差（波动性）。 */
        val volatility: Float,
        val direction: TrendDirection,
        /** 与输入等长的移动平均（窗口不足时用可用点平均）。 */
        val movingAverage: List<Float>,
        /** 高于均值的点（「高光回」）。 */
        val aboveAverage: List<RatingPoint>,
        /** 低于均值的点（「崩坏回」）。 */
        val belowAverage: List<RatingPoint>,
    ) {
        val isEmpty: Boolean get() = count == 0
    }

    /** 斜率绝对值小于该值视为「平稳」。 */
    const val FLAT_SLOPE_THRESHOLD = 0.02f

    fun analyze(points: List<RatingPoint>, window: Int = 3): TrendStats {
        val sorted = points.sortedBy { it.ep }
        if (sorted.isEmpty()) {
            return TrendStats(
                count = 0, average = 0f, max = null, min = null, slope = 0f,
                volatility = 0f, direction = TrendDirection.FLAT,
                movingAverage = emptyList(), aboveAverage = emptyList(), belowAverage = emptyList(),
            )
        }

        val scores = sorted.map { it.score }
        val average = scores.average().toFloat()
        val slope = slopeOf(scores)
        val volatility = standardDeviation(scores, average)
        val windowSafe = window.coerceAtLeast(1)

        return TrendStats(
            count = sorted.size,
            average = average,
            max = sorted.maxByOrNull { it.score },
            min = sorted.minByOrNull { it.score },
            slope = slope,
            volatility = volatility,
            direction = directionOf(slope),
            movingAverage = movingAverage(scores, windowSafe),
            aboveAverage = sorted.filter { it.score > average },
            belowAverage = sorted.filter { it.score < average },
        )
    }

    /** 最小二乘斜率：以「序号」为 X（避免集号有跳号时斜率被拉伸）。 */
    fun slopeOf(scores: List<Float>): Float {
        val n = scores.size
        if (n < 2) return 0f
        val meanX = (n - 1) / 2.0
        val meanY = scores.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val dx = i - meanX
            num += dx * (scores[i] - meanY)
            den += dx * dx
        }
        if (den == 0.0) return 0f
        return (num / den).toFloat()
    }

    fun directionOf(slope: Float): TrendDirection = when {
        slope > FLAT_SLOPE_THRESHOLD -> TrendDirection.RISING
        slope < -FLAT_SLOPE_THRESHOLD -> TrendDirection.FALLING
        else -> TrendDirection.FLAT
    }

    fun standardDeviation(scores: List<Float>, average: Float = scores.average().toFloat()): Float {
        if (scores.isEmpty()) return 0f
        var acc = 0.0
        for (s in scores) {
            val d = (s - average).toDouble()
            acc += d * d
        }
        return sqrt(acc / scores.size).toFloat()
    }

    /** 居中移动平均；边界处用可用点求平均。 */
    fun movingAverage(scores: List<Float>, window: Int): List<Float> {
        if (scores.isEmpty()) return emptyList()
        val w = window.coerceAtLeast(1)
        val half = w / 2
        return scores.indices.map { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(scores.lastIndex)
            var sum = 0f
            for (j in from..to) sum += scores[j]
            sum / (to - from + 1)
        }
    }

    /**
     * 低票数点过滤：新番刚播时每集票数极少，曲线噪声大。
     * 返回 (保留的点, 被过滤的点数)。
     */
    fun filterLowVotes(points: List<RatingPoint>, minVotes: Int): Pair<List<RatingPoint>, Int> {
        if (minVotes <= 0) return points to 0
        val kept = points.filter { (it.votes ?: 0) >= minVotes }
        return kept to (points.size - kept.size)
    }

    /** 「高光回」/「崩坏回」：与均值的偏离超过阈值（默认 0.5 分）的点。 */
    fun highlights(points: List<RatingPoint>, threshold: Float = 0.5f): Pair<List<RatingPoint>, List<RatingPoint>> {
        val stats = analyze(points)
        if (stats.isEmpty) return emptyList<RatingPoint>() to emptyList()
        val high = points.filter { it.score - stats.average >= threshold }.sortedByDescending { it.score }
        val low = points.filter { stats.average - it.score >= threshold }.sortedBy { it.score }
        return high to low
    }

    /** 直观的波动描述（对齐 Bangumi-master 的「异口同声…厨黑大战」风格，但用于集间波动）。 */
    fun volatilityLabel(volatility: Float): String = when {
        volatility <= 0f -> "—"
        volatility < 0.3f -> "非常稳定"
        volatility < 0.6f -> "较为稳定"
        volatility < 1.0f -> "有起伏"
        volatility < 1.5f -> "起伏明显"
        else -> "剧烈波动"
    }

    /** 距离平均线的最大偏离（画图自适应 Y 轴范围用）。 */
    fun maxDeviation(points: List<RatingPoint>): Float {
        if (points.isEmpty()) return 0f
        val avg = points.map { it.score }.average().toFloat()
        return points.maxOf { abs(it.score - avg) }
    }
}
