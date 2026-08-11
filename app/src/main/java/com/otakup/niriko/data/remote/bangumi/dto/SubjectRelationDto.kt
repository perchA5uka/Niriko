package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 关联条目（前后传/版本/系列等），来自 GET /v0/subjects/{id}/subjects。 */
@Serializable
data class SubjectRelationDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    /** 关联类型：前传/续集/动画化/不同演绎/原作书籍/系列等。 */
    val relation: String? = null,
    val images: SubjectImagesDto? = null,
    val type: Int = 0,
)
