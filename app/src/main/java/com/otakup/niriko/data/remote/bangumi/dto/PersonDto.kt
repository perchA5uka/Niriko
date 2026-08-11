package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 人物（声优/导演/制作人员）详情，来自 GET /v0/persons/{id}。 */
@Serializable
data class PersonDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    val summary: String? = null,
    val images: SubjectImagesDto? = null,
    /** 职业列表，如 ["artist", "director", "seiyu"]。 */
    val career: List<String> = emptyList(),
    val gender: String? = null,
    @SerialName("birth_year")
    val birthYear: Int? = null,
    @SerialName("birth_mon")
    val birthMonth: Int? = null,
    @SerialName("birth_day")
    val birthDay: Int? = null,
    val stat: SubjectStatDto? = null,
)

/** 人物/角色统计（收藏数、评论数）。 */
@Serializable
data class SubjectStatDto(
    val comments: Int? = null,
    val collects: Int? = null,
)
