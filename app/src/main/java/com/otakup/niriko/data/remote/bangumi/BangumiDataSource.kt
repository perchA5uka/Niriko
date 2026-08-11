package com.otakup.niriko.data.remote.bangumi

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.remote.CalendarDaySchedule
import com.otakup.niriko.data.remote.CharacterDetailInfo
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.PersonDetailInfo
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.data.remote.PersonTopWork
import com.otakup.niriko.data.remote.SubjectRelationInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.bangumi.dto.SearchFilterDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchRequestDto
import com.otakup.niriko.data.remote.bangumi.dto.toEntity
import com.otakup.niriko.data.remote.mapper.SubjectMapper
import com.otakup.niriko.util.resolveCoverUrl
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.DayOfWeek

class BangumiDataSource(
    private val apiService: BangumiApiService,
    private val mapper: SubjectMapper = SubjectMapper(),
) : SubjectRemoteDataSource {

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
        val request = SearchRequestDto(
            keyword = keyword,
            sort = sort ?: "rank",
            filter = SearchFilterDto(
                type = if (type != null) listOf(type) else null,
                tag = tags,
                airDate = airDate,
                rank = rank,
                nsfw = nsfw,
            ).takeIf { it.type != null || it.tag != null || it.airDate != null || it.rank != null || it.nsfw != null },
        )
        val response = apiService.searchSubjects(request, limit = limit, offset = offset)
        return mapper.fromSearchResponse(response)
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
        val request = SearchRequestDto(
            keyword = keyword,
            sort = sort ?: "rank",
            filter = SearchFilterDto(
                type = if (type != null) listOf(type) else null,
                tag = tags,
                airDate = airDate,
                rank = rank,
                nsfw = nsfw,
            ).takeIf { it.type != null || it.tag != null || it.airDate != null || it.rank != null || it.nsfw != null },
        )
        val response = apiService.searchSubjects(request, limit = limit, offset = offset)
        return mapper.fromSearchResponse(response) to response.total
    }

    override suspend fun getDetail(subjectId: Long): SubjectEntity {
        val response = apiService.getSubjectDetail(subjectId = subjectId)
        return mapper.fromDetailResponse(response)
    }

    override suspend fun getInfoBox(subjectId: Long): List<InfoBoxEntry> {
        return try {
            val response = apiService.getSubjectDetail(subjectId = subjectId)
            response.infobox?.mapNotNull { item ->
                val text = infoBoxValueToText(item.value) ?: return@mapNotNull null
                if (item.key.isBlank() || text.isBlank()) null
                else InfoBoxEntry(key = item.key, value = text)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 把 infobox 的 JsonElement 值转成可读文本（支持字符串/数组/嵌套对象）。 */
    private fun infoBoxValueToText(element: kotlinx.serialization.json.JsonElement?): String? {
        return when (element) {
            is kotlinx.serialization.json.JsonPrimitive -> element.content
            is kotlinx.serialization.json.JsonArray -> element.joinToString("、") { infoBoxValueToText(it) ?: "" }
            is kotlinx.serialization.json.JsonObject -> element.entries.joinToString("、") { (k, v) -> "$k: ${infoBoxValueToText(v) ?: ""}" }
            else -> null
        }
    }

    override suspend fun getCalendar(): List<CalendarDaySchedule> {
        val response = apiService.getCalendar()
        return response.map { day ->
            CalendarDaySchedule(
                dayOfWeek = mapBangumiWeekday(day.weekday.id),
                subjects = day.items.map { dto -> dto.toEntity() },
            )
        }
    }

    override suspend fun getSubjectsByMonth(
        type: Int,
        year: Int,
        month: Int,
    ): List<SubjectEntity> {
        val response = apiService.getSubjectsByMonth(
            type = type,
            sort = "date",
            year = year,
            month = month,
            limit = 100,
        )
        return mapper.fromSearchResponse(response)
    }

    override suspend fun getSubjectsInDateRange(
        type: Int,
        startDate: String,
        endDate: String,
    ): List<SubjectEntity> {
        val request = SearchRequestDto(
            keyword = "",
            sort = "date",
            filter = SearchFilterDto(
                type = listOf(type),
                airDate = listOf(">=$startDate", "<$endDate"),
            ),
        )
        val response = apiService.searchSubjectsByDateRange(request, limit = 100)
        return mapper.fromSearchResponse(response)
    }

    override suspend fun getRankingByType(type: Int, offset: Int, limit: Int): List<SubjectEntity> {
        val response = apiService.getRankingByType(type = type, sort = "rank", limit = limit, offset = offset)
        return mapper.fromSearchResponse(response)
    }

    override suspend fun getCharacters(subjectId: Long): List<CharacterInfo> {
        val response = apiService.getCharacters(subjectId)
        return mapper.mapCharacters(response)
    }

    override suspend fun getStaff(subjectId: Long): List<StaffInfo> {
        val response = apiService.getPersons(subjectId)
        return mapper.mapStaff(response)
    }

    override suspend fun getEpisodes(subjectId: Long): List<EpisodeInfo> {
        val response = apiService.getEpisodes(subjectId)
        return mapper.mapEpisodes(response.data)
    }

    override suspend fun getRatingDistribution(subjectId: Long): Map<Int, Int> {
        val response = apiService.getSubjectDetail(subjectId)
        return response.rating?.count?.mapKeys { it.key.toIntOrNull() ?: 0 }
            ?.filterKeys { it in 1..10 }
            ?: emptyMap()
    }

    // ==================== 详情扩展（角色/人物/关联） ====================

    override suspend fun getCharacterDetail(characterId: Long): CharacterDetailInfo {
        val dto = apiService.getCharacterDetail(characterId)
        return CharacterDetailInfo(
            id = dto.id,
            name = dto.name,
            nameCn = dto.nameCn,
            summary = dto.summary,
            // 详情页头像用 grid 正方形，避免长立绘裁到身体中段
            imageUrl = dto.images?.grid ?: dto.images?.small,
            relation = dto.relation,
        )
    }

    override suspend fun getCharacterSubjects(characterId: Long): List<PersonSubjectInfo> {
        val dtos = apiService.getCharacterSubjects(characterId)
        return dtos.map { it.toPersonSubjectInfo() }
    }

    override suspend fun getPersonDetail(personId: Long): PersonDetailInfo {
        val dto = apiService.getPersonDetail(personId)
        return PersonDetailInfo(
            id = dto.id,
            name = dto.name,
            nameCn = dto.nameCn,
            summary = dto.summary,
            // 详情页头像用 grid 正方形，避免长立绘裁到身体中段
            imageUrl = dto.images?.grid ?: dto.images?.small,
            career = dto.career,
        )
    }

    override suspend fun getPersonSubjects(personId: Long): List<PersonSubjectInfo> {
        val dtos = apiService.getPersonSubjects(personId)
        return dtos.map { it.toPersonSubjectInfo() }
    }

    override suspend fun getPersonCharacters(personId: Long): List<CharacterInfo> {
        val dtos = apiService.getPersonCharacters(personId)
        return mapper.mapCharacters(dtos)
    }

    override suspend fun getSubjectRelations(subjectId: Long): List<SubjectRelationInfo> {
        val dtos = apiService.getSubjectRelations(subjectId)
        return dtos.map {
            SubjectRelationInfo(
                subjectId = it.id,
                title = it.name,
                titleCN = it.nameCn,
                type = it.type,
                relation = it.relation,
                imageUrl = resolveCoverUrl(it.images),
            )
        }
    }

    override suspend fun searchPersons(keyword: String): List<PersonDetailInfo> {
        val response = apiService.searchPersons(
            SearchRequestDto(keyword = keyword, sort = "match"),
            limit = 20,
        )
        // 防请求风暴：detail 并发 ≤2、调用级去重、总请求上限（Bangumi 未登录限速 ~30 req/min）
        val detailCache = mutableMapOf<Long, Int>() // subjectId → ratingTotal
        val detailSemaphore = Semaphore(2)
        var detailQueries = 0
        val maxDetailQueries = 30
        return response.data.map { dto ->
            val topWorks = try {
                // 候选：动画+书籍 + 核心身份，取前 8 个（控制查询数）
                val candidates = apiService.getPersonSubjects(dto.id)
                    .filter { it.type == 2 || it.type == 1 } // 动画 + 书籍优先
                    .filter { isCoreStaff(it.staff) }        // 核心身份过滤
                    .take(8)
                // 查收藏数（rating.total）排序取 top 3 —— 去重 + 限流 + 上限，命中缓存跳过请求
                candidates
                    .map { work ->
                        work to detailCache.getOrPut(work.id) {
                            detailQueries++
                            if (detailQueries > maxDetailQueries) {
                                0
                            } else {
                                detailSemaphore.withPermit {
                                    runCatching { apiService.getSubjectDetail(work.id).rating?.total ?: 0 }
                                        .getOrDefault(0)
                                }
                            }
                        }
                    }
                    .sortedByDescending { (_, total) -> total }
                    .take(3)
                    .map { (work, _) ->
                        PersonTopWork(
                            subjectId = work.id,
                            title = work.nameCn ?: work.name,
                            imageUrl = work.image,
                        )
                    }
            } catch (_: Exception) {
                emptyList()
            }
            PersonDetailInfo(
                id = dto.id,
                name = dto.name,
                nameCn = dto.nameCn,
                summary = dto.summary,
                imageUrl = dto.images?.grid ?: dto.images?.small,
                career = dto.career,
                topWorks = topWorks,
            )
        }
    }

}

/** 核心身份过滤：作者/导演/原作/脚本/角色设计/音乐/配音等为"代表作"身份，艺术家/协力等剔除。 */
private fun isCoreStaff(staff: String?): Boolean {
    if (staff.isNullOrBlank()) return true // 无身份信息时保留
    val coreKeywords = listOf(
        "作者", "原作", "导演", "监督", "系列构成", "脚本", "编剧",
        "角色设计", "作画", "音乐", "作曲", "配音", "声优", "主演", "演出", "分镜", "企画", "制作",
    )
    return coreKeywords.any { staff.contains(it) }
}

/** PersonSubjectDto → PersonSubjectInfo 转换。 */
private fun com.otakup.niriko.data.remote.bangumi.dto.PersonSubjectDto.toPersonSubjectInfo() =
    PersonSubjectInfo(
        subjectId = id,
        title = name,
        titleCN = nameCn,
        type = type,
        staff = staff,
        eps = eps,
        imageUrl = image,
    )

/** Bangumi weekday id → java.time.DayOfWeek。Bangumi: 0=Sunday, 1=Monday … 6=Saturday。 */
internal fun mapBangumiWeekday(id: Int): DayOfWeek =
    if (id == 0) DayOfWeek.SUNDAY
    else if (id in 1..6) DayOfWeek.of(id)
    else DayOfWeek.SUNDAY  // fallback
