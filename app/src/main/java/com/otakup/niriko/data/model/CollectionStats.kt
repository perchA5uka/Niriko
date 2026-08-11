package com.otakup.niriko.data.model

import com.otakup.niriko.data.model.WatchStatus

/**
 * 收藏库统计数据（列表数据内存计算）。
 */
data class CollectionStats(
    val totalCount: Int = 0,
    val statusCounts: Map<WatchStatus, Int> = emptyMap(),
    val averageRating: Double = 0.0,
    val totalWatchedEpisodes: Int = 0,
    val completionRate: Float = 0f,
)
