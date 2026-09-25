package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 剧集响应（来自 GET /v0/episodes）。 */
@Serializable
data class EpisodeResponseDto(
    val total: Int = 0,
    val data: List<EpisodeDto> = emptyList(),
)

/** 单集信息。 */
@Serializable
data class EpisodeDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    val desc: String? = null,
    val airdate: String? = null,
    /** 集号（用于展示）。 */
    val ep: Double = 0.0,
    /** 排序（用于排序）。 */
    val sort: Double = 0.0,
    /** 时长，如 "25:00"。 */
    val duration: String? = null,
    /** 放送状态：Air / Today / Tomorrow / NA。 */
    val status: String? = null,
    /** 本集讨论/回复数（热力图用）。 */
    val comment: Int = 0,
    /** 音乐曲目的碟片数（音乐类型）。 */
    val disc: Int = 0,
    /** 服务器解析的时长（秒），无法解析时为 0。 */
    @SerialName("duration_seconds")
    val durationSeconds: Int = 0,
    /** 剧集类型：0=本篇，1=SP，2=OP，3=ED（音乐类型曲目同样适用）。 */
    val type: Int = 0,
)
