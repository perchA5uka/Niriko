package com.otakup.niriko.data.remote.bangumi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

@Serializable
data class SubjectRatingDto(
    val score: Double = 0.0,
    @SerialName("count")
    val countElement: JsonElement? = null,
    val rank: Int = 0,
    val total: Int = 0,
) {
    /** 从 JSON Element 安全解析评分分布 Map。对象格式照常解析，非对象/非数字值降级为空 Map。 */
    val count: Map<String, Int>
        get() = parseCount(countElement)

    private companion object {
        fun parseCount(element: JsonElement?): Map<String, Int> {
            if (element == null || element is JsonNull || element is JsonArray) return emptyMap()
            if (element !is JsonObject) return emptyMap()

            return element.entries.mapNotNull { (key, value) ->
                val intValue = when (value) {
                    is JsonPrimitive -> value.intOrNull ?: value.content.toIntOrNull() ?: 0
                    else -> 0
                }
                key to intValue
            }.toMap()
        }
    }
}
