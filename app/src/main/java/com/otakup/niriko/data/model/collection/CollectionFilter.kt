package com.otakup.niriko.data.model.collection

import com.otakup.niriko.data.model.WatchStatus

data class CollectionFilter(
    val keyword: String = "",
    val status: WatchStatus? = null,
    val sort: SortOrder = SortOrder.UPDATE_TIME,
    val selectedTags: Set<String> = emptySet(),
)
