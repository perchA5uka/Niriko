package com.otakup.niriko.plugin

/**
 * 数据源能力声明。
 *
 * 每个插件通过此结构声明自己能做什么。
 * DataSourceChain 根据此声明自动决定如何组合调度，
 * 避免向无法处理某类请求的数据源发送无效请求。
 */
data class DataSourceCapabilities(
    // ===== 核心搜索 =====
    val supportsSearch: Boolean = true,
    val supportsSearchWithTotal: Boolean = true,

    // ===== 详情与扩展 =====
    val supportsDetail: Boolean = true,
    val supportsCharacters: Boolean = true,
    val supportsStaff: Boolean = true,
    val supportsEpisodes: Boolean = true,

    // ===== 日历与月度（大部分源不支持） =====
    val supportsCalendar: Boolean = false,
    val supportsSubjectsByMonth: Boolean = false,
    val supportsRatingDistribution: Boolean = false,
)
