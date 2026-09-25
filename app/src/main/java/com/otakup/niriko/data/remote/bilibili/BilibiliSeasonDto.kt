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
    /**
     * 分集列表（阶段 D）。
     *
     * 此前这个接口**只解析了 rating**，把整段 episodes[] 丢掉了——
     * 而每集的 cover 正是动画剧照最正当、最省事的来源（免 key、已有 UA/Referer）。
     */
    val episodes: List<BilibiliEpisodeDto> = emptyList(),
    /** 番剧总封面。 */
    val cover: String? = null,
)

/** 分集信息（pgc/view/web/season 的 result.episodes[]）。 */
@Serializable
data class BilibiliEpisodeDto(
    val id: Long = 0,
    @SerialName("ep_id")
    val epId: Long = 0,
    /** 分集序号（第几话）。 */
    val title: String? = null,
    /** 分集副标题。 */
    @SerialName("long_title")
    val longTitle: String? = null,
    /** 分集封面（剧照）。 */
    val cover: String? = null,
    /** 发布时间戳（秒）。 */
    @SerialName("pub_time")
    val pubTime: Long? = null,
    /** 时长（秒）。 */
    val duration: Long? = null,
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