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
    /** 血型（Bangumi 用数字表示，1=A/2=B/3=O/4=AB）。 */
    @SerialName("blood_type")
    val bloodType: Int? = null,
    /** 详细资料表（身高/体重/出身地/引用来源/官方网站/Twitter 等）。 */
    val infobox: List<SubInfoboxItem>? = null,
    val stat: SubjectStatDto? = null,
)

/** 人物/角色统计（收藏数、评论数）。 */
@Serializable
data class SubjectStatDto(
    val comments: Int? = null,
    val collects: Int? = null,
)
