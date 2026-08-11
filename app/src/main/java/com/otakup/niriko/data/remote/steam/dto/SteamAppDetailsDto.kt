package com.otakup.niriko.data.remote.steam.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Steam 商店应用详情响应（store.steampowered.com/api/appdetails）。
 * 响应为 Map<appid, AppDetailsWrapper>，wrapper 内含 success 与 data。
 */
@Serializable
data class SteamAppDetailsWrapperDto(
    val success: Boolean = false,
    val data: SteamAppDetailsDto? = null,
)

@Serializable
data class SteamAppDetailsDto(
    /** 条目类型：game / dlc / demo / music / video 等。 */
    val type: String? = null,
    /** 商店标题（l=schinese 本地化）。 */
    val name: String? = null,
    @SerialName("steam_appid")
    val steamAppId: Int? = null,
    @SerialName("short_description")
    val shortDescription: String? = null,
    @SerialName("header_image")
    val headerImage: String? = null,
    @SerialName("is_free")
    val isFree: Boolean = false,
    @SerialName("price_overview")
    val priceOverview: SteamPriceOverviewDto? = null,
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val metacritic: SteamMetacriticDto? = null,
    @SerialName("release_date")
    val releaseDate: SteamReleaseDateDto? = null,
    val platforms: SteamPlatformsDto? = null,
    val genres: List<SteamGenreDto> = emptyList(),
    val screenshots: List<SteamScreenshotDto> = emptyList(),
)

@Serializable
data class SteamPriceOverviewDto(
    val currency: String? = null,
    /** 原价（分）。 */
    val initial: Int? = null,
    /** 现价（分）。 */
    val final: Int? = null,
    @SerialName("discount_percent")
    val discountPercent: Int? = null,
)

@Serializable
data class SteamMetacriticDto(
    /** 0-100。 */
    val score: Int? = null,
    val url: String? = null,
)

@Serializable
data class SteamReleaseDateDto(
    @SerialName("coming_soon")
    val comingSoon: Boolean? = null,
    /** 本地化发行日期字符串（如 "2023 年 8 月 20 日"）。 */
    val date: String? = null,
)

@Serializable
data class SteamGenreDto(
    val id: String? = null,
    /** 本地化类型描述（如 "动作"）。 */
    val description: String? = null,
)

@Serializable
data class SteamScreenshotDto(
    val id: Int? = null,
    @SerialName("path_thumbnail")
    val pathThumbnail: String? = null,
    /** 全尺寸截图 URL。 */
    @SerialName("path_full")
    val pathFull: String? = null,
)
