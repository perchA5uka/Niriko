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
    /**
     * 评分区间，**字符串形式的比较表达式**，如 `[">=8"]`、`[">=7", "<9"]`。
     *
     * Bangumi v0 把区间类的 filter 统一设计成「字符串数组 + 运算符前缀」，
     * 所以即使语义是数字也必须传字符串（传 number 会被拒）。
     */
    val rating: List<String>? = null,
    /**
     * 评分人数区间，格式与 [rating] 相同（如 [">=100"]）。
     *
     * 第 6 轮新增：官方 schema 里 filter 确实有 rating_count
     * （https://bangumi.github.io/api/dist.json），它是「质量门槛」在服务端的表达 ——
     * 规格 §3.3 的 v1 规则版就用它挡掉「10 分 3 人」的作品。
     */
    @SerialName("rating_count")
    val ratingCount: List<String>? = null,
    val rank: List<String>? = null,
    val nsfw: Boolean? = null,
)
