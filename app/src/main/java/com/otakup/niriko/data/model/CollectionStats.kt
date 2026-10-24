package com.otakup.niriko.data.model

import com.otakup.niriko.data.model.WatchStatus

/**
 * 收藏库统计数据（列表数据内存计算）。
 */
data class CollectionStats(
    val totalCount: Int = 0,
    val statusCounts: Map<WatchStatus, Int> = emptyMap(),
    val averageRating: Double = 0.0,
    /** 视频集数（ANIME + REAL）；游戏分钟与音乐曲目不再混入这里。 */
    val totalWatchedEpisodes: Int = 0,
    /** 游戏游玩分钟数（GAME 的 watchedEpisodes 真实单位）。 */
    val gameMinutes: Int = 0,
    /** 阅读单位（书籍/漫画卷数，缺失时退回集数）。 */
    val readUnits: Int = 0,
    val completionRate: Float = 0f,
)
