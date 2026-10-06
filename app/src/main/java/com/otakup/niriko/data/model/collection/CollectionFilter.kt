package com.otakup.niriko.data.model.collection

import com.otakup.niriko.data.model.WatchStatus

data class CollectionFilter(
    val keyword: String = "",
    val status: WatchStatus? = null,
    val sort: SortOrder = SortOrder.UPDATE_TIME,
    val selectedTags: Set<String> = emptySet(),
    /**
     * 当前查看的作品库分区（F09）。null = 全部作品（不带分区过滤）。
     *
     * 分区是**筛选维度**而不是「另一份收藏」：作品仍然来自同一张 collection 表，
     * 分区只决定「哪些 subjectId 参与本次列表」。因此删分区永远不会影响收藏本身。
     */
    val folderId: Long? = null,
)
