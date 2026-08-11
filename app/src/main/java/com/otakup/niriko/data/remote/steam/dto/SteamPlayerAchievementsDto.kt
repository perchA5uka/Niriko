package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ISteamUserStats/GetPlayerAchievements 响应。
 * 单用户单游戏成就解锁状态；要求 profile Game details 公开，否则为空/错误。
 */
@Serializable
data class SteamPlayerAchievementsResponseDto(
    val playerstats: SteamPlayerStatsDto? = null,
)

@Serializable
data class SteamPlayerStatsDto(
    val steamID: String? = null,
    @SerialName("gameName")
    val gameName: String? = null,
    /** 是否成功（false = 隐私限制/无该游戏）。 */
    val success: Boolean = false,
    val achievements: List<SteamAchievementProgressDto> = emptyList(),
)

@Serializable
data class SteamAchievementProgressDto(
    /** 成就 API 名（与 GetSchemaForGame 的 name 对应）。 */
    val apiname: String? = null,
    /** 是否已解锁。 */
    val achieved: Boolean = false,
    /** 解锁时间戳（秒），未解锁为 0。 */
    val unlocktime: Long = 0,
)
