package com.otakup.niriko.data.model.search

enum class TrendingMode(val label: String) {
    SEASONAL("当季热门"),
    ALL_TIME("历史排名"),
    /** 本地 Steam 条目（含 Bangumi 无词条的独占占位条目）。 */
    STEAM("Steam"),
}
