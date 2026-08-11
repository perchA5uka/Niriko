package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bangumi v0 搜索请求体。
 *
 * JSON 结构:
 * {
 *   "keyword": "...",
 *   "sort": "match",
 *   "filter": {
 *     "type": [2]
 *   }
 * }
 */
@Serializable
data class SearchRequestDto(
    val keyword: String,
    val sort: String = "rank",
    val filter: SearchFilterDto? = null,
)

@Serializable
data class SearchFilterDto(
    val type: List<Int>? = null,
    val tag: List<String>? = null,
    @SerialName("air_date")
    val airDate: List<String>? = null,
    val rank: List<String>? = null,
    val nsfw: Boolean? = null,
)
