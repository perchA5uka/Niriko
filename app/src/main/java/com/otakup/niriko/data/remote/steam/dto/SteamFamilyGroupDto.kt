package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * IFamilyGroupsService/GetFamilyGroup 响应
 * （api.steampowered.com，需用户 access token 而非 Web API key）。
 *
 * 返回用户所属 Steam 家庭组的 groupid；后续 GetSharedLibraryApps 用它拉取共享库。
 */
@Serializable
data class SteamFamilyGroupResponseDto(
    val response: SteamFamilyGroupInnerDto? = null,
)

@Serializable
data class SteamFamilyGroupInnerDto(
    /** 家庭组 ID（字符串，Steam 内部为 uint64）。 */
    @SerialName("family_groupid")
    val familyGroupId: String? = null,
)
