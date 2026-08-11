package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * IFamilyGroupsService/GetSharedLibraryApps 响应
 * （api.steampowered.com，需用户 access token）。
 *
 * 返回家庭组内可访问的应用列表。include_own=true 时才包含"借入"（他人拥有、
 * 共享给本用户）的游戏；否则只返回本人拥有的。调用方需与 GetOwnedGames 结果
 * 取差集，识别出真正的家庭库游戏。
 */
@Serializable
data class SteamSharedLibraryAppsResponseDto(
    val response: SteamSharedLibraryInnerDto? = null,
)

@Serializable
data class SteamSharedLibraryInnerDto(
    val apps: List<SteamSharedAppDto> = emptyList(),
)

@Serializable
data class SteamSharedAppDto(
    val appid: Int = 0,
    /** include_appinfo 语义下为游戏名（可能缺省）。 */
    val name: String? = null,
    /** 封面图标 URL（CDN 前缀需自行拼接）。 */
    @SerialName("img_icon_url")
    val imgIconUrl: String? = null,
    @SerialName("img_logo_url")
    val imgLogoUrl: String? = null,
)
