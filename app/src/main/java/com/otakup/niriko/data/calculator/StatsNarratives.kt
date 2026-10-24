package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.model.stats.MonthlyStats

/** 两个月的数量对比结果；[CountComparison.Insufficient] 表示样本太小，调用方应「不显示」而不是显示"持平"。 */
sealed interface CountComparison {
    data class Up(val delta: Int) : CountComparison
    data class Down(val delta: Int) : CountComparison
    data object Flat : CountComparison
    data object Insufficient : CountComparison
}

/**
 * 统计口径的"一句话"文案。
 *
 * 为什么先算 [CountComparison] 再拼句子：数据不足时必须有"什么都不显示"这条路，
 * 否则会出现"比上月多 0 部"这种既没信息又让人觉得统计坏了的句子。
 * 也不给百分比：基数常常是个位数，"+71%"会把 7 部说成 12 部的量级。
 */
object StatsNarratives {

    /** 两侧都少于这个数就不比较（个位数月度波动没有解读价值）。 */
    const val MIN_SAMPLE = 3

    fun compareCount(current: Int?, previous: Int?, minSample: Int = MIN_SAMPLE): CountComparison {
        if (current == null || previous == null) return CountComparison.Insufficient
        if (current < minSample && previous < minSample) return CountComparison.Insufficient
        val delta = current - previous
        return when {
            delta > 0 -> CountComparison.Up(delta)
            delta < 0 -> CountComparison.Down(-delta)
            else -> CountComparison.Flat
        }
    }

    /** 末尾连续下降的次数（10→8→5 记 2 次）。 */
    fun decliningStreak(trend: List<MonthlyStats>): Int {
        if (trend.size < 2) return 0
        var streak = 0
        for (index in trend.lastIndex downTo 1) {
            if (trend[index].count < trend[index - 1].count) streak++ else break
        }
        return streak
    }

    /**
     * 月度趋势标题旁的一句话；返回 null 表示这一格应该完全不渲染。
     *
     * 连续下降达到 3 次时优先说"连续 N 个月下降"——它比"比上月少 2 部"更有信息量。
     */
    fun monthlyNarrative(trend: List<MonthlyStats>, minSample: Int = MIN_SAMPLE): String? {
        if (trend.size < 2) return null
        val streak = decliningStreak(trend)
        if (streak >= 3) return "连续 " + streak + " 个月下降"
        val current = trend.last().count
        val previous = trend[trend.size - 2].count
        return when (val comparison = compareCount(current, previous, minSample)) {
            is CountComparison.Up -> "比上月多 " + comparison.delta + " 部"
            is CountComparison.Down -> "比上月少 " + comparison.delta + " 部"
            CountComparison.Flat -> "与上月持平"
            CountComparison.Insufficient -> null
        }
    }
}
