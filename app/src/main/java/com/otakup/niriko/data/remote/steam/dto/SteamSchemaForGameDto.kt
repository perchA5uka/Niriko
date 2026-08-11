package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ISteamUserStats/GetSchemaForGame 响应（成就定义，无用户维度）。
 */
@Serializable
data class SteamSchemaForGameResponseDto(
    val game: SteamGameSchemaDto? = null,
)

@Serializable
data class SteamGameSchemaDto(
    @SerialName("gameName")
    val gameName: String? = null,
    @SerialName("availableGameStats")
    val availableGameStats: SteamAvailableGameStatsDto? = null,
)

@Serializable
data class SteamAvailableGameStatsDto(
    val achievements: List<SteamAchievementDefinitionDto> = emptyList(),
)

@Serializable
data class SteamAchievementDefinitionDto(
    /** 成就 API 名（与 GetPlayerAchievements 的 apiname 对应）。 */
    val name: String? = null,
    @SerialName("displayName")
    val displayName: String? = null,
    val description: String? = null,
    /** 是否隐藏成就。 */
    val hidden: Boolean = false,
    /** 成就图标 URL（完整 CDN 地址）。 */
    val icon: String? = null,
    val icongray: String? = null,
)
