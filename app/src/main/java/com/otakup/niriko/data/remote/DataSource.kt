package com.otakup.niriko.data.remote

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import java.time.DayOfWeek

/**
 * 可扩展的远程作品数据源接口。
 * 不同元数据服务（Bangumi、MyAnimeList 等）可分别实现此接口。
 */
interface SubjectRemoteDataSource {

    suspend fun search(
        keyword: String,
        type: Int? = null,
        tags: List<String>? = null,
        airDate: List<String>? = null,
        rank: List<String>? = null,
        nsfw: Boolean? = null,
        sort: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<SubjectEntity>

    suspend fun getDetail(subjectId: Long): SubjectEntity

    /** 获取条目 infobox（艺术家/发行商/发售日期等键值对）。 */
    suspend fun getInfoBox(subjectId: Long): List<InfoBoxEntry>

    /** 获取角色列表。 */
    suspend fun getCharacters(subjectId: Long): List<CharacterInfo>

    /** 获取制作人员列表。 */
    suspend fun getStaff(subjectId: Long): List<StaffInfo>

    /** 获取剧集列表。 */
    suspend fun getEpisodes(subjectId: Long): List<EpisodeInfo>

    /** 搜索并返回总结果数（用于客户端缓存）。 */
    suspend fun searchWithTotal(
        keyword: String,
        type: Int? = null,
        tags: List<String>? = null,
        airDate: List<String>? = null,
        rank: List<String>? = null,
        nsfw: Boolean? = null,
        sort: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): Pair<List<SubjectEntity>, Int>

    /** 获取当季放送日历，按星期分组。 */
    suspend fun getCalendar(): List<CalendarDaySchedule>

    /** 获取评分分布（key: 分数 1-10, value: 投票数）。 */
    suspend fun getRatingDistribution(subjectId: Long): Map<Int, Int>

    /**
     * 按月浏览条目。用于非当季月份的数据加载。
     * @param type 条目类型（2=ANIME）
     * @param year 年份
     * @param month 月份
     */
    suspend fun getSubjectsByMonth(
        type: Int,
        year: Int,
        month: Int,
    ): List<SubjectEntity>

    /** 按开播日期范围查询条目（跨月延续用）。开播日在 [startDate, endDate) 区间。 */
    suspend fun getSubjectsInDateRange(
        type: Int,
        startDate: String,
        endDate: String,
    ): List<SubjectEntity>

    /** 获取类型排名榜（GET /v0/subjects?sort=rank，绕开 POST 在代理下 body 被吞）。按 rank 升序。 */
    suspend fun getRankingByType(type: Int, offset: Int = 0, limit: Int = 20): List<SubjectEntity>

    // ==================== 详情扩展（角色/人物/关联） ====================

    /** 获取角色详情。 */
    suspend fun getCharacterDetail(characterId: Long): CharacterDetailInfo

    /** 获取角色出演作品列表。 */
    suspend fun getCharacterSubjects(characterId: Long): List<PersonSubjectInfo>

    /** 获取人物（声优/导演等）详情。 */
    suspend fun getPersonDetail(personId: Long): PersonDetailInfo

    /** 获取人物参与的作品列表。 */
    suspend fun getPersonSubjects(personId: Long): List<PersonSubjectInfo>

    /** 获取人物演绎的角色列表（声优最近角色）。 */
    suspend fun getPersonCharacters(personId: Long): List<CharacterInfo>

    /** 获取条目关联（前后传/版本/系列等）。 */
    suspend fun getSubjectRelations(subjectId: Long): List<SubjectRelationInfo>

    /** 人物搜索（声优/导演/作者等）。返回含代表作品的人物列表。 */
    suspend fun searchPersons(keyword: String): List<PersonDetailInfo>
}

/** 人物代表作品（卡片小图用）。 */
data class PersonTopWork(
    val subjectId: Long,
    val title: String,
    val imageUrl: String?,
)

/** 角色详情信息（UI 模型）。 */
data class CharacterDetailInfo(
    val id: Long,
    val name: String,
    val nameCn: String?,
    val summary: String?,
    val imageUrl: String?,
    val relation: String?,
)

/** 人物（声优/导演等）详情信息。 */
data class PersonDetailInfo(
    val id: Long,
    val name: String,
    val nameCn: String?,
    val summary: String?,
    val imageUrl: String?,
    /** 职业列表，如 ["artist", "director", "seiyu"]。 */
    val career: List<String>,
    /** 代表作品（卡片小图用，类型优先 + 核心身份过滤后的前 3 个）。 */
    val topWorks: List<PersonTopWork> = emptyList(),
)

/** 人物/角色参与的作品条目（staff 为参与身份或角色名）。 */
data class PersonSubjectInfo(
    val subjectId: Long,
    val title: String,
    val titleCN: String?,
    val type: Int,
    val staff: String?,
    /** 参与章节/曲目（persons 接口）。 */
    val eps: String? = null,
    val imageUrl: String?,
)

/** 关联条目（前后传/版本/系列等）。 */
data class SubjectRelationInfo(
    val subjectId: Long,
    val title: String,
    val titleCN: String?,
    val type: Int,
    val relation: String?,
    val imageUrl: String?,
)

/** 放送日历条目：某星期几的放送作品列表。 */
data class CalendarDaySchedule(
    val dayOfWeek: DayOfWeek,
    val subjects: List<SubjectEntity>,
)

/** 条目 infobox 条目（如 艺术家/发行商/发售日期等）。value 已序列化为可读文本。 */
data class InfoBoxEntry(
    val key: String,
    val value: String,
)
