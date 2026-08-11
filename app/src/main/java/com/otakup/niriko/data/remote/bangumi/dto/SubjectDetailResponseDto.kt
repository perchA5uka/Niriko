package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectDetailResponseDto(
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
    @SerialName("air_weekday")
    val airWeekday: Int? = null,
    val series: Boolean? = null,
    val tags: List<SubjectTagDto>? = null,
    /** 条目信息框（艺术家/发行商/发售日期等键值对，音乐类型尤其丰富）。 */
    val infobox: List<SubInfoboxItem>? = null,
)