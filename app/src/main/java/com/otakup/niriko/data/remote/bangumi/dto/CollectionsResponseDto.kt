package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.Serializable

/**
 * 用户收藏分页响应（GET /v0/users/{username}/collections）。
 * 与 SearchResponseDto 同构：data + total + limit + offset。
 */
@Serializable
data class CollectionsResponseDto(
    val data: List<UserCollectionDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)