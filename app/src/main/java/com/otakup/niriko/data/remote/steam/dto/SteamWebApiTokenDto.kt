package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * store.steampowered.com/pointssummary/ajaxgetasyncconfig 响应（登录态下）。
 *
 * 该接口在用户已登录（Cookie 会话有效）时返回 `webapi_token`——Steam 的
 * 用户级 access token（非 Web API key），用于 IFamilyGroupsService 等
 * 家庭库接口。token 约 1-2 天过期，需在登录会话有效期内重新获取。
 *
 * 只声明所需字段，其余忽略（Json ignoreUnknownKeys）。
 */
@Serializable
data class SteamWebApiTokenDto(
    @SerialName("webapi_token")
    val webApiToken: String? = null,
)
