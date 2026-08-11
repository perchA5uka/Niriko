package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    val type: Int = 0,
    val summary: String? = null,
    val images: SubjectImagesDto? = null,
    @SerialName("total_episodes")
    val totalEpisodes: Int? = null,
    val platform: String? = null,
    val volumes: Int? = null,
    val eps: Int? = null,
    val rating: SubjectRatingDto? = null,
    val date: String? = null,
    /** /calendar 旧接口字段名，与 [date] 同义，双字段 fallback。 */
    @SerialName("air_date")
    val airDateField: String? = null,
    @SerialName("air_weekday")
    val airWeekday: Int? = null,
    val series: Boolean? = null,
    val tags: List<SubjectTagDto>? = null,
)