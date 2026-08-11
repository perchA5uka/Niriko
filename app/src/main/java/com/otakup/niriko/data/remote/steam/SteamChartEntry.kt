package com.otakup.niriko.data.remote.steam

/**
 * 活跃玩家排行条目（GetMostPlayedGames 的 appid → 排名映射结果）。
 * 详情页 Steam 区块「当前活跃排名」展示用。
 */
data class SteamChartEntry(
    /** 当前排名（1 = 最活跃）。 */
    val rank: Int = 0,
    /** 上周排名（-1 = 新上榜）。 */
    val lastWeekRank: Int = 0,
    /** 峰值在线人数。 */
    val peakInGame: Int = 0,
)
