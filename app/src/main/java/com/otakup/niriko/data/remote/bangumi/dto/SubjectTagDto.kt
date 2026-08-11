package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubjectTagDto(
    val name: String = "",
    val count: Int = 0,
)