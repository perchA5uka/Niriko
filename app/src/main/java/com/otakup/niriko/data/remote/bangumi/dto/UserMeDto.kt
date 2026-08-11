package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 当前 Access Token 对应的用户信息（GET /v0/me）。
 * 仅保留同步所需字段，未知键忽略。
 */
@Serializable
data class UserMeDto(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: AvatarDto? = null,
    val sign: String? = null,
    /** 用户组（如 1=管理员…），不必细分，仅作展示。 */
    @SerialName("user_group")
    val userGroup: Int = 0,
)

@Serializable
data class AvatarDto(
    val large: String? = null,
    val medium: String? = null,
    val small: String? = null,
)