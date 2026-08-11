package com.otakup.niriko.data.remote.bilibili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * B 站番剧详情响应(pgc/view/web/season)。
 * 只声明所需字段,其余忽略(Json ignoreUnknownKeys)。
 */
@Serializable
data class BilibiliSeasonResponseDto(
    val code: Int = -1,
    val message: String? = null,
    val result: BilibiliSeasonResultDto? = null,
)

@Serializable
data class BilibiliSeasonResultDto(
    val title: String? = null,
    val seasonId: Long? = null,
    @SerialName("season_id")
    val seasonIdSnake: Long? = null,
    val mediaId: Long? = null,
    @SerialName("media_id")
    val mediaIdSnake: Long? = null,
    val rating: BilibiliRatingDto? = null,
    val rights: BilibiliRightsDto? = null,
)

@Serializable
data class BilibiliRatingDto(
    val score: Double = 0.0,
    val count: Int = 0,
)

@Serializable
data class BilibiliRightsDto(
    /** 区域限制: 0=不限, 328=仅港澳台(area_limit 数值来自实测)。 */
    @SerialName("area_limit")
    val areaLimit: Int = 0,
    /** 实测返回 0/1 整数(非布尔)。 */
    val banAreaShow: Int = 0,
    @SerialName("ban_area_show")
    val banAreaShowSnake: Int = 0,
)

/**
 * 兼容字段解析辅助: 实测 B 站同时返回 `seasonId`(驼峰) 与 `season_id`(snake)。
 * 该 DTO 保持宽松, 由 Client 统一读取任一非空值。
 */
@Suppress("unused")
internal fun BilibiliSeasonResultDto.resolveSeasonId(): Long? =
    listOfNotNull(seasonId, seasonIdSnake, mediaId, mediaIdSnake)
        .firstOrNull { it > 0L }

@Suppress("unused")
internal val BilibiliSeasonResultDto.areaLimited: Boolean
    get() = rights?.areaLimit != 0 || rights?.banAreaShow != 0 || rights?.banAreaShowSnake != 0