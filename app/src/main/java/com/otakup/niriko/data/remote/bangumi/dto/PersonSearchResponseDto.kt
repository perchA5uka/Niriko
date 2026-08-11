package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.Serializable

/** 人物搜索结果分页（来自 POST /v0/search/persons）。 */
@Serializable
data class PersonSearchResponseDto(
    val data: List<PersonDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)
