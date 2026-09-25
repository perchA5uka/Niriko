package com.otakup.niriko.data.model.search

/**
 * 发现页顶栏的视图模式（第 6 轮 §5/§6 收敛为 3 个入口）。
 *
 * 第 4 轮曾在这里加过两个「功能入口」：[FIND]（找条目）与 [MONTHLY]（评分月刊）。
 * 第 6 轮两项都被删除：
 * - 「找条目」的能力并进了「历史排名」的筛选器（[com.otakup.niriko.data.discover.BrowseFilter]）；
 * - 「评分月刊」整块移除（含本地快照表，DB v28 → v29）。
 *
 * 于是这里只剩三个**趋势列表**模式，全部由 [com.otakup.niriko.ui.search.TrendingSection] 渲染。
 */
enum class TrendingMode(val label: String) {
    SEASONAL("当季热门"),
    ALL_TIME("历史排名"),
    /** 本地 Steam 条目（含 Bangumi 无词条的独占占位条目）。 */
    STEAM("Steam");

    companion object {
        /** 顶栏显示的模式（= 全部模式，第 6 轮起不再分组）。 */
        val TREND_MODES: List<TrendingMode> = listOf(SEASONAL, ALL_TIME, STEAM)
    }
}
