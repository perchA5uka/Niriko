package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 人物/角色参与的作品条目（来自 GET /v0/persons/{id}/subjects 或 /v0/characters/{id}/subjects）。
 * - persons 接口：`staff` 为参与身份（如 导演/原作/音乐），`eps` 为参与的章节/曲目。
 * - characters 接口：`staff` 为角色名（如 旁白/主角），无 `eps`、`image` 用 `image` 字段。
 */
@Serializable
data class PersonSubjectDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("name_cn")
    val nameCn: String? = null,
    /** 参与身份（persons）或角色名（characters）。 */
    val staff: String? = null,
    /** 参与章节/曲目（persons 接口）。 */
    val eps: String? = null,
    val image: String? = null,
    val type: Int = 0,
)
