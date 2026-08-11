package com.otakup.niriko.data.model.search

/** 搜索排序模式。v0 API 官方支持 match/heat/rank/score（Kazumi 默认 heat）。 */
enum class SearchSortMode(val label: String, val apiValue: String) {
    MATCH("匹配度", "match"),
    HEAT("热度", "heat"),
    RANK("排名", "rank"),
    SCORE("评分", "score"),
}
