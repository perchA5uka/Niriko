package com.otakup.niriko.data.remote.anilist

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.CharacterDetailInfo
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.PersonDetailInfo
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.data.remote.SubjectRelationInfo
import com.otakup.niriko.data.remote.CalendarDaySchedule
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * AniList 数据源 — 实现 SubjectRemoteDataSource。
 *
 * 使用 GraphQL API (https://graphql.anilist.co) 获取动漫元数据。
 * 在中国大陆可直接访问（无需 VPN）。
 * 日历/评分分布/按月浏览 等能力受限，已在 capabilities 中声明。
 */
class AniListDataSource(
    private val client: AniListClient,
) : SubjectRemoteDataSource {

    // ==================== 搜索 ====================

    override suspend fun search(
        keyword: String,
        type: Int?,
        tags: List<String>?,
        airDate: List<String>?,
        rank: List<String>?,
        nsfw: Boolean?,
        sort: String?,
        limit: Int?,
        offset: Int?,
    ): List<SubjectEntity> {
        val anilistType = mapBangumiType(type) ?: return emptyList()
        val vars = mutableMapOf<String, JsonElement>()
        vars["search"] = JsonPrimitive(keyword)
        vars["type"] = JsonPrimitive(anilistType)
        val perPage = (limit ?: 50).coerceAtLeast(1)
        vars["perPage"] = JsonPrimitive(perPage)
        val page = ((offset ?: 0) / perPage) + 1
        vars["page"] = JsonPrimitive(page)

        return try {
            val pageObj = client.query(AniListQueries.SEARCH, vars, listOf("data", "Page"))
            val mediaArray = pageObj.jsonObject["media"]?.jsonArray ?: return emptyList()
            val now = System.currentTimeMillis()
            mediaArray.map { AniListMapper.mediaToSubject(it.jsonObject, now) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchWithTotal(
        keyword: String,
        type: Int?,
        tags: List<String>?,
        airDate: List<String>?,
        rank: List<String>?,
        nsfw: Boolean?,
        sort: String?,
        limit: Int?,
        offset: Int?,
    ): Pair<List<SubjectEntity>, Int> {
        val anilistType = mapBangumiType(type) ?: return emptyList<SubjectEntity>() to 0
        val vars = mutableMapOf<String, JsonElement>()
        vars["search"] = JsonPrimitive(keyword)
        vars["type"] = JsonPrimitive(anilistType)
        vars["perPage"] = JsonPrimitive(limit ?: 50)

        return try {
            val pageObj = client.query(AniListQueries.SEARCH, vars, listOf("data", "Page"))
            val pageInfo = pageObj.jsonObject["pageInfo"]?.jsonObject
            val total = pageInfo?.get("total")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val mediaArray = pageObj.jsonObject["media"]?.jsonArray ?: return emptyList<SubjectEntity>() to 0
            val now = System.currentTimeMillis()
            val results = mediaArray.map { AniListMapper.mediaToSubject(it.jsonObject, now) }
            results to total
        } catch (e: Exception) {
            emptyList<SubjectEntity>() to 0
        }
    }

    // ==================== 详情 ====================

    override suspend fun getDetail(subjectId: Long): SubjectEntity {
        val vars = mapOf("id" to JsonPrimitive(subjectId.toInt()))
        val media = client.query(AniListQueries.DETAIL, vars, listOf("data", "Media")).jsonObject
        return AniListMapper.mediaToSubject(media)
    }

    override suspend fun getInfoBox(subjectId: Long): List<InfoBoxEntry> = emptyList()

    // ==================== 扩展数据 ====================

    override suspend fun getCharacters(subjectId: Long): List<CharacterInfo> {
        return try {
            val vars = mapOf("id" to JsonPrimitive(subjectId.toInt()))
            val media = client.query(AniListQueries.CHARACTERS, vars, listOf("data", "Media")).jsonObject
            AniListMapper.mapCharacters(media)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getStaff(subjectId: Long): List<StaffInfo> {
        return try {
            val vars = mapOf("id" to JsonPrimitive(subjectId.toInt()))
            val media = client.query(AniListQueries.STAFF, vars, listOf("data", "Media")).jsonObject
            AniListMapper.mapStaff(media)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getEpisodes(subjectId: Long): List<EpisodeInfo> {
        // AniList 没有分集列表 API（只有总集数），返回空
        return emptyList()
    }

    // ==================== 不支持的接口 ====================

    override suspend fun getCalendar(): List<CalendarDaySchedule> {
        // AniList 不支持每周放送日历
        return emptyList()
    }

    override suspend fun getRatingDistribution(subjectId: Long): Map<Int, Int> {
        // AniList 不提供评分分布
        return emptyMap()
    }

    override suspend fun getSubjectsByMonth(type: Int, year: Int, month: Int): List<SubjectEntity> {
        // AniList 不支持按月浏览
        return emptyList()
    }

    override suspend fun getSubjectsInDateRange(type: Int, startDate: String, endDate: String): List<SubjectEntity> = emptyList()

    override suspend fun getRankingByType(type: Int, offset: Int, limit: Int): List<SubjectEntity> = emptyList()

    // ==================== 详情扩展（AniList 不支持，返回空） ====================

    override suspend fun getCharacterDetail(characterId: Long): CharacterDetailInfo {
        throw UnsupportedOperationException("AniList 不支持角色详情")
    }

    override suspend fun getCharacterSubjects(characterId: Long): List<PersonSubjectInfo> = emptyList()

    override suspend fun getPersonDetail(personId: Long): PersonDetailInfo {
        throw UnsupportedOperationException("AniList 不支持人物详情")
    }

    override suspend fun getPersonSubjects(personId: Long): List<PersonSubjectInfo> = emptyList()

    override suspend fun getPersonCharacters(personId: Long): List<CharacterInfo> = emptyList()

    override suspend fun getSubjectRelations(subjectId: Long): List<SubjectRelationInfo> = emptyList()

    override suspend fun searchPersons(keyword: String): List<PersonDetailInfo> = emptyList()

    // ==================== 辅助 ====================

    /** Bangumi type 整数 → AniList MediaType 字符串。不支持的返回 null。 */
    private fun mapBangumiType(bangumiType: Int?): String? {
        if (bangumiType == null) return "ANIME" // 默认只搜动画
        return when (bangumiType) {
            2 -> "ANIME"
            1 -> "MANGA"
            else -> null // GAME/MUSIC/REAL 等 AniList 不支持
        }
    }
}
