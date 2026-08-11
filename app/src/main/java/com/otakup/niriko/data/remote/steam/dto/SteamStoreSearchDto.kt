package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Steam 商店搜索响应（store.steampowered.com/api/storesearch）。
 * 只声明所需字段，其余忽略（Json ignoreUnknownKeys）。
 */
@Serializable
data class SteamStoreSearchResponseDto(
    val total: Int = 0,
    val items: List<SteamStoreSearchItemDto> = emptyList(),
)

@Serializable
data class SteamStoreSearchItemDto(
    /** app / bundle / sub。 */
    val type: String? = null,
    val name: String = "",
    /** appid。 */
    val id: Int = 0,
    val price: SteamPriceDto? = null,
    @SerialName("tiny_image")
    val tinyImage: String? = null,
    val platforms: SteamPlatformsDto? = null,
    /** Metascore（字符串形式，如 "90" 或空串）。 */
    val metascore: String? = null,
)

@Serializable
data class SteamPriceDto(
    val currency: String? = null,
    /** 原价（分）。 */
    val initial: Int? = null,
    /** 现价（分）。 */
    val final: Int? = null,
    @SerialName("discount_percent")
    val discountPercent: Int? = null,
)

@Serializable
data class SteamPlatformsDto(
    val windows: Boolean = false,
    val mac: Boolean = false,
    val linux: Boolean = false,
)
