package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 用户收藏条目（GET /v0/users/{username}/collections 的 data 元素）。
 * 结构对齐 Bangumi v0 API（参考 Bangumi-master types/response.ts）：
 * `{ subject_id, subject_type, type, rate, comment, tags, ep_status, updated_at, subject }`。
 */
@Serializable
data class UserCollectionDto(
    /** 条目 ID（= Bangumi Subject ID）。 */
    @SerialName("subject_id")
    val subjectId: Long = 0,
    /** 条目类型（1=book 2=anime 3=music 4=game 5=real）。 */
    @SerialName("subject_type")
    val subjectType: Int = 0,
    /** 用户收藏状态（Bangumi 官方 CollectionType：1=想看 2=看过 3=在看 4=搁置 5=抛弃）。 */
    val type: Int = 0,
    /** 用户评分（0-10，0 或缺失表示未评分）。 */
    val rate: Int = 0,
    val comment: String? = null,
    val tags: List<String> = emptyList(),
    /** 看到的话数（ep_status）。 */
    @SerialName("ep_status")
    val epStatus: Int = 0,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    /** 内嵌条目元数据（可直接作 SubjectEntity 种子）。 */
    val subject: SubjectDto? = null,
) {
    val hasRating: Boolean get() = rate > 0
}