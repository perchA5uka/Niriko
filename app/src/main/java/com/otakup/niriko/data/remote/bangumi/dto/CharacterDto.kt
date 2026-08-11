package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 角色信息（来自 GET /v0/subjects/{id}/characters）。 */
@Serializable
data class CharacterDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    @SerialName("role_name")
    val roleName: String? = null, // 女主角、配角等
    /** 角色类型（主角/配角/客串等，来自角色的 relation 字段）。 */
    val relation: String? = null,
    val summary: String? = null,
    val images: SubjectImagesDto? = null,
    val actors: List<StaffDto> = emptyList(),
)

/** 声优/制作人员信息。角色接口中 actors 字段和 persons 接口复用此结构。 */
@Serializable
data class StaffDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    val images: SubjectImagesDto? = null,
    /** persons 接口专用：角色类型（原作、监督、音乐等）。characters 接口不含此字段。 */
    @SerialName("role_name")
    val roleName: String? = null,
)
