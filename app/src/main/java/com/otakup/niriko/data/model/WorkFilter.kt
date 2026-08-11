package com.otakup.niriko.data.model

/**
 * 作品列表查询条件。
 * 字段为 null / 空表示不作为过滤条件。
 */
data class WorkFilter(
    val keyword: String? = null,
    val type: WorkType? = null,
    val status: WatchStatus? = null,
    /** 需同时包含的标签；为空则不按标签过滤。 */
    val tags: List<String> = emptyList(),
)
