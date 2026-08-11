package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Bangumi API 的 infobox 条目。
 * 每个 item 包含一个 key 和 value（支持字符串、数组、嵌套对象）。
 */
@Serializable
data class SubInfoboxItem(
    val key: String = "",
    @Contextual
    val value: JsonElement? = null,
)
