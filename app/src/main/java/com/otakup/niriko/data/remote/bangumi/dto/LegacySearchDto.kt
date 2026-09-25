package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bangumi **旧版**搜索响应（GET /search/subject/{keywords}）。
 *
 * 为什么需要它：v0 的 `POST /v0/search/subjects` 在 `nsfw=true` 时**必须带 access token**，
 * 否则请求会被拒。而旧版接口免 token、且**会返回 NSFW 条目** —— 这是 NSFW 内容在未登录
 * 状态下仍可阅读的唯一可用通道。
 *
 * 代价：旧版接口只支持关键词 + 类型，没有 tag / air_date / rank 区间筛选，
 * 因此只在「关键词非空 且 nsfw=true」时作为兜底使用。
 */
@Serializable
data class LegacySearchResponseDto(
    val results: Int = 0,
    val list: List<LegacySubjectDto> = emptyList(),
)

@Serializable
data class LegacySubjectDto(
    val id: Long = 0,
    val type: Int = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    val summary: String? = null,
    @SerialName("air_date")
    val airDate: String? = null,
    @SerialName("air_weekday")
    val airWeekday: Int? = null,
    val images: SubjectImagesDto? = null,
    val eps: Int? = null,
    @SerialName("eps_count")
    val epsCount: Int? = null,
    val rating: SubjectRatingDto? = null,
    /** 旧版把排名放在条目层级（v0 在 rating.rank）。 */
    val rank: Int? = null,
    val platform: String? = null,
    val volumes: Int? = null,
)
