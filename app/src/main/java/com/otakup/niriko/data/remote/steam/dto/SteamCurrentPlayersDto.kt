package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 当前游玩人数响应（api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1）。
 */
@Serializable
data class SteamCurrentPlayersResponseDto(
    val response: SteamCurrentPlayersInnerDto? = null,
)

@Serializable
data class SteamCurrentPlayersInnerDto(
    @SerialName("player_count")
    val playerCount: Int? = null,
    /** 1=成功，其他为失败。 */
    val result: Int? = null,
)
