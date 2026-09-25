package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 角色详情，来自 GET /v0/characters/{id}。 */
@Serializable
data class CharacterDetailDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    val summary: String? = null,
    val images: SubjectImagesDto? = null,
    /** 角色类型：主角/配角/客串等。 */
    val relation: String? = null,
    val gender: String? = null,
    @SerialName("birth_year")
    val birthYear: Int? = null,
    @SerialName("birth_mon")
    val birthMonth: Int? = null,
    @SerialName("birth_day")
    val birthDay: Int? = null,
    @SerialName("blood_type")
    val bloodType: Int? = null,
    /** 详细资料表（别名/身高/体重/出身地等）。 */
    val infobox: List<SubInfoboxItem>? = null,
    val stat: SubjectStatDto? = null,
)
