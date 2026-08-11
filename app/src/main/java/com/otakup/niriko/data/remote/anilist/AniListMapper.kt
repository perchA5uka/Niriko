package com.otakup.niriko.data.remote.anilist

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.model.SubjectType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AniList JSON 响应 → 领域模型 映射器。
 * 所有解析逻辑集中于此，不依赖外部 DTO 类，直接操作 JsonElement。
 */
object AniListMapper {

    /** 映射单个 Media 节点 → SubjectEntity。 */
    fun mediaToSubject(media: JsonObject, now: Long = System.currentTimeMillis()): SubjectEntity {
        val id = media["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        val title = media["title"]?.jsonObject
        val typeStr = media["type"]?.jsonPrimitive?.content ?: "ANIME"
        val score = media["averageScore"]?.jsonPrimitive?.content?.toIntOrNull()
        val startDate = media["startDate"]?.jsonObject
        val cover = media["coverImage"]?.jsonObject

        return SubjectEntity(
            subjectId = id,
            title = title?.get("romaji")?.jsonPrimitive?.content
                ?: title?.get("english")?.jsonPrimitive?.content
                ?: title?.get("native")?.jsonPrimitive?.content
                ?: "Unknown",
            titleCN = title?.get("native")?.jsonPrimitive?.content
                ?: title?.get("english")?.jsonPrimitive?.content,
            type = mapMediaType(typeStr),
            summary = stripHtml(media["description"]?.jsonPrimitive?.content),
            coverUrl = cover?.get("large")?.jsonPrimitive?.content
                ?: cover?.get("extraLarge")?.jsonPrimitive?.content,
            totalEpisodes = media["episodes"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: media["chapters"]?.jsonPrimitive?.content?.toIntOrNull(),
            platform = media["format"]?.jsonPrimitive?.content,
            volumes = media["volumes"]?.jsonPrimitive?.content?.toIntOrNull(),
            airDate = formatDate(startDate),
            ratingScore = score?.let { it / 10f },
            ratingTotal = score?.let { it },
            tags = parseStringList(media["genres"]) +
                    (media["tags"]?.jsonArray?.mapNotNull { tag ->
                        (tag as? JsonObject)?.get("name")?.jsonPrimitive?.content
                    } ?: emptyList()),
            lastSyncTime = now,
        )
    }

    /** 映射角色列表。 */
    fun mapCharacters(media: JsonObject): List<CharacterInfo> {
        val edges = media["characters"]?.jsonObject?.get("edges")?.jsonArray ?: return emptyList()
        return edges.mapNotNull { edge ->
            val obj = edge.jsonObject
            val node = obj["node"]?.jsonObject ?: return@mapNotNull null
            val id = node["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val name = node["name"]?.jsonObject
            val image = node["image"]?.jsonObject
            val voiceActors = obj["voiceActors"]?.jsonArray

            CharacterInfo(
                id = id,
                name = name?.get("full")?.jsonPrimitive?.content ?: "Unknown",
                nameCn = name?.get("native")?.jsonPrimitive?.content,
                roleName = obj["role"]?.jsonPrimitive?.content,
                imageUrl = image?.get("large")?.jsonPrimitive?.content,
                actors = voiceActors?.mapNotNull { va ->
                    val vaObj = va.jsonObject
                    StaffInfo(
                        id = vaObj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        name = vaObj["name"]?.jsonObject?.get("full")?.jsonPrimitive?.content ?: "Unknown",
                        nameCn = vaObj["name"]?.jsonObject?.get("native")?.jsonPrimitive?.content,
                        roleName = vaObj["language"]?.jsonPrimitive?.content,
                        imageUrl = vaObj["image"]?.jsonObject?.get("large")?.jsonPrimitive?.content,
                    )
                } ?: emptyList(),
            )
        }
    }

    /** 映射制作人员列表。 */
    fun mapStaff(media: JsonObject): List<StaffInfo> {
        val edges = media["staff"]?.jsonObject?.get("edges")?.jsonArray ?: return emptyList()
        return edges.mapNotNull { edge ->
            val obj = edge.jsonObject
            val node = obj["node"]?.jsonObject ?: return@mapNotNull null
            StaffInfo(
                id = node["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                name = node["name"]?.jsonObject?.get("full")?.jsonPrimitive?.content ?: "Unknown",
                nameCn = node["name"]?.jsonObject?.get("native")?.jsonPrimitive?.content,
                roleName = obj["role"]?.jsonPrimitive?.content,
                imageUrl = node["image"]?.jsonObject?.get("large")?.jsonPrimitive?.content,
            )
        }
    }

    /** 映射作品类型。 */
    private fun mapMediaType(type: String): SubjectType = when (type) {
        "ANIME" -> SubjectType.ANIME
        "MANGA" -> SubjectType.MANGA
        else -> SubjectType.OTHER
    }

    /** 从 FuzzyDate JSON 对象格式化为 "YYYY-MM-DD"。 */
    private fun formatDate(date: JsonObject?): String? {
        if (date == null) return null
        val year = date["year"]?.jsonPrimitive?.content
        val month = date["month"]?.jsonPrimitive?.content?.padStart(2, '0')
        val day = date["day"]?.jsonPrimitive?.content?.padStart(2, '0')
        return if (year != null) "${year}-${month ?: "01"}-${day ?: "01"}" else null
    }

    /** 去除 HTML 标签。 */
    private fun stripHtml(html: String?): String? {
        if (html == null) return null
        return html.replace(Regex("<[^>]*>"), "").trim()
    }

    /** 解析 JSON 字符串数组。 */
    private fun parseStringList(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content }
    }
}
