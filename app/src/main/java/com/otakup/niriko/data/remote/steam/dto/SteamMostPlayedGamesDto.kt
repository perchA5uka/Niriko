package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ISteamChartsService/GetMostPlayedGames 响应（实测确认结构）。
 *
 * ```json
 * {"response":{"rollup_date":1778457600,"ranks":[
 *   {"rank":1,"appid":730,"last_week_rank":1,"peak_in_game":1275982}, ...
 * ]}}
 * ```
 * 注意：实测响应无 concurrent_in_game 字段，只有 peak_in_game（峰值在线）。
 */
@Serializable
data class SteamMostPlayedGamesResponseDto(
    val response: SteamMostPlayedGamesInnerDto? = null,
)

@Serializable
data class SteamMostPlayedGamesInnerDto(
    /** 排行统计日期（unix 秒）。 */
    @SerialName("rollup_date")
    val rollupDate: Long = 0,
    val ranks: List<SteamChartRankDto> = emptyList(),
)

@Serializable
data class SteamChartRankDto(
    /** 当前排名（1 = 最活跃）。 */
    val rank: Int = 0,
    val appid: Int = 0,
    /** 上周排名（-1 = 新上榜）。 */
    @SerialName("last_week_rank")
    val lastWeekRank: Int = 0,
    /** 峰值在线人数。 */
    @SerialName("peak_in_game")
    val peakInGame: Int = 0,
)
