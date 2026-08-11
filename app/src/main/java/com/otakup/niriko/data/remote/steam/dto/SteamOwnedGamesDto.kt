package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GetOwnedGames 响应（api.steampowered.com/IPlayerService/GetOwnedGames/v1）。
 * 只声明所需字段，其余忽略（Json ignoreUnknownKeys）。
 */
@Serializable
data class SteamOwnedGamesResponseDto(
    val response: SteamOwnedGamesInnerDto? = null,
)

@Serializable
data class SteamOwnedGamesInnerDto(
    /** 游戏总数。 */
    @SerialName("game_count")
    val gameCount: Int? = null,
    val games: List<SteamOwnedGameDto> = emptyList(),
)

@Serializable
data class SteamOwnedGameDto(
    val appid: Int = 0,
    /** include_appinfo=true 时返回名称。 */
    val name: String? = null,
    /** 封面图标 URL（含 CDN 前缀，需自行拼接完整地址）。 */
    @SerialName("img_icon_url")
    val imgIconUrl: String? = null,
    @SerialName("img_logo_url")
    val imgLogoUrl: String? = null,
    /** 总游玩时长（分钟）。 */
    @SerialName("playtime_forever")
    val playtimeForever: Int = 0,
    /** 近两周游玩时长（分钟）。 */
    @SerialName("playtime_2weeks")
    val playtime2Weeks: Int? = null,
    /** 是否免费。 */
    @SerialName("has_community_visible_stats")
    val hasCommunityVisibleStats: Boolean? = null,
)
