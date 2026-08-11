package com.otakup.niriko.data.calculator

import com.otakup.niriko.data.model.search.SearchSortMode
import java.util.Calendar

/**
 * 趋势/搜索相关纯计算逻辑。
 *
 * 所有方法都是纯函数——不持有状态，可独立单元测试。
 */
object TrendingCalculator {

    /** 根据当前季度计算日期范围（季初月 -1，季末月 +0），如夏季 → [">=2026-06-01", "<2026-10-01"]。 */
    fun computeSeasonDateRange(): List<String> {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val (startMonth, endMonth) = when (month) {
            1, 2, 3 -> 1 to 4
            4, 5, 6 -> 4 to 7
            7, 8, 9 -> 7 to 10
            else -> 10 to 13
        }
        val startYear = if (startMonth == 1) year else year
        val endYear = if (endMonth > 12) year + 1 else year
        val endM = if (endMonth > 12) endMonth - 12 else endMonth
        return listOf(
            ">=${startYear}-${"%02d".format(startMonth)}-01",
            "<${endYear}-${"%02d".format(endM)}-01",
        )
    }

    /** 从 filterSelections 提取 tag 列表用于 API 搜索。 */
    fun buildFilterTags(
        filterSelections: Map<String, List<String>>,
        dimensions: List<com.otakup.niriko.data.filter.FilterDimension>,
    ): List<String> {
        return filterSelections.flatMap { (dimKey, labels) ->
            val dim = dimensions.find { it.key == dimKey } ?: return@flatMap emptyList()
            labels.mapNotNull { label ->
                dim.options.find { it.label == label }?.apiQueryValue
            }
        }
    }

    /** 判断是否处于无搜索关键字时的活跃筛选状态。 */
    fun hasActiveFilters(
        filterSelections: Map<String, List<String>>,
        nsfwEnabled: Boolean,
        sortMode: SearchSortMode,
    ): Boolean {
        return filterSelections.isNotEmpty() || nsfwEnabled || sortMode != SearchSortMode.HEAT
    }
}
