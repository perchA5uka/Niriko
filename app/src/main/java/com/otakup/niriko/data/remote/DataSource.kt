package com.otakup.niriko.data.remote

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.util.TitleResolver
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
    // ===== 阶段 6：基本信息 / 声优 / 统计 =====
    val gender: String? = null,
    val birthday: String? = null,
    val bloodType: String? = null,
    val collects: Int? = null,
    val comments: Int? = null,
    val infoBox: List<InfoBoxEntry> = emptyList(),
    /** 声优列表（角色详情页此前不展示，数据其实已有）。 */
    val actors: List<StaffInfo> = emptyList(),
) {
    val moreInfoUrl: String get() = "https://bgm.tv/character/$id"
}

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
    // ===== 阶段 6：基本信息 / 统计 / 职位 =====
    /** 性别（male / female）。 */
    val gender: String? = null,
    /** 生日（如 "1962-06-29"，只到年/月时按可用精度拼接）。 */
    val birthday: String? = null,
    /** 血型（A / B / O / AB）。 */
    val bloodType: String? = null,
    /** 收藏人数（stat.collects）。 */
    val collects: Int? = null,
    /** 评论数（stat.comments）。 */
    val comments: Int? = null,
    /** 详细资料表（身高/体重/出身地/引用来源/官方网站/Twitter 等）。 */
    val infoBox: List<InfoBoxEntry> = emptyList(),
    /** 参与人数统计：职位 → 作品数（本地聚合，见 PersonJobAnalyzer）。 */
    val jobStats: List<PersonJobStat> = emptyList(),
) {
    /** 更多资料外链。 */
    val moreInfoUrl: String get() = "https://bgm.tv/person/$id"
}

/** 职位统计（人物页「参与职位」区块）。 */
data class PersonJobStat(
    val job: String,
    val count: Int,
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

/** 人物/角色参与作品展示名降级（中文名空串时回退原名）。 */
val PersonSubjectInfo.displayTitle: String
    get() = TitleResolver.resolve(titleCN, title).primary.ifBlank { "未命名作品" }

/** 关联条目展示名降级（中文名空串时回退原名）。 */
val SubjectRelationInfo.displayTitle: String
    get() = TitleResolver.resolve(titleCN, title).primary.ifBlank { "未命名作品" }

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

/**
 * 单个数据源的失败记录（第 6 轮 F5）。
 *
 * 之前链条只把失败写进日志，上层拿到的是「空列表」——于是「网络失败」与
 * 「确实没有结果」在 UI 上完全无法区分。这里把失败本身变成可传递的数据。
 */
data class PluginFailure(
    val pluginId: String,
    val reason: String,
)

/**
 * 全部数据源均失败（第 6 轮 F5）。
 *
 * searchWithTotal 只在「所有被调用过的插件都抛错」时抛出本异常；
 * 只要有一个插件正常返回（哪怕是空列表），就说明是「确实没有结果」，不抛。
 * 上层据此显示「网络失败 + 重试」，而不是「未找到相关作品」。
 */
class AllPluginsFailedException(
    val failures: List<PluginFailure>,
) : Exception(
    "所有数据源均失败：" + failures.joinToString("; ") { failure ->
        failure.pluginId + "=" + failure.reason
    }
)
