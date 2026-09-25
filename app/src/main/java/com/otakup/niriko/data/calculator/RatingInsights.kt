package com.otakup.niriko.data.calculator

import kotlin.math.sqrt

/**
 * 评分洞察（**纯函数，可单测**）。
 *
 * 参考 Bangumi-master 的 `subject/component/rating/chart/utils.ts`：
 * 由评分分布（每档票数）算标准差 → 争议度标签「异口同声 … 厨黑大战」；
 * 以及本作在同类作品中的百分位。
 */
object RatingInsights {

    /**
     * 无数据哨兵值。
     * 必须与"所有票都在同一档"（标准差恰好为 0）区分开，否则空分布会显示成「异口同声」。
     */
    const val NO_DEVIATION = -1f

    /**
     * 评分分布标准差。
     *
     * @param distribution 分数(1-10) → 票数
     * @return 无数据时返回 [NO_DEVIATION]（-1）
     */
    fun deviation(distribution: Map<Int, Int>): Float {
        val total = distribution.values.sum()
        if (total <= 0) return NO_DEVIATION
        val mean = distribution.entries.sumOf { (score, count) -> score.toDouble() * count } / total
        var acc = 0.0
        distribution.forEach { (score, count) ->
            val d = score - mean
            acc += d * d * count
        }
        return sqrt(acc / total).toFloat()
    }

    /** 争议度标签（阈值对齐 Bangumi-master）。 */
    fun disputeLabel(deviation: Float): String = when {
        deviation < 0f -> "—"
        deviation == 0f -> "异口同声"
        deviation < 1.0f -> "异口同声"
        deviation < 1.15f -> "基本一致"
        deviation < 1.3f -> "略有分歧"
        deviation < 1.45f -> "莫衷一是"
        deviation < 1.6f -> "各执一词"
        deviation < 1.75f -> "你死我活"
        else -> "厨黑大战"
    }

    /**
     * 本地库内的同类百分位。
     *
     * 诚实的口径说明：这里用的是**用户自己收藏库**里同类型作品的分部，
     * 不是全体 Bangumi 用户（那需要一份需要人工维护的静态分布表）。
     * UI 必须标注「本地库内百分位」，不能暗示是全网排名。
     *
     * @return 0-100 的百分位（越高越好）；样本不足 5 个时返回 null。
     */
    fun localPercentile(score: Float?, sameTypeScores: List<Float>): Int? {
        if (score == null) return null
        val valid = sameTypeScores.filter { it > 0f }
        if (valid.size < MIN_SAMPLE) return null
        val below = valid.count { it < score }
        return (below.toFloat() / valid.size * 100f).toInt().coerceIn(0, 100)
    }

    /**
     * 把任意标尺的分数换算到 10 分制（用于跨源同屏对比）。
     * 与 [com.otakup.niriko.data.remote.rating.ExternalRating.toTenPoint] 同义，
     * 这里再放一份是为了让 calculator 层不依赖 remote 层（单测更轻）。
     */
    fun toTenPoint(nativeScore: Float?, scoreMax: Float): Float? {
        if (nativeScore == null || scoreMax <= 0f) return null
        return nativeScore / scoreMax * 10f
    }

    private const val MIN_SAMPLE = 5
}
