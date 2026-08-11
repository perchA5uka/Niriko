package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubjectImagesDto(
    val large: String? = null,
    val medium: String? = null,
    val small: String? = null,
    /** 服务端裁好的正方形小图（75x75），适合头像。 */
    val grid: String? = null,
)