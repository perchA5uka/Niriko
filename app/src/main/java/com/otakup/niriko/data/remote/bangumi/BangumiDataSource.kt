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
import com.otakup.niriko.data.remote.bangumi.dto.mapSubjectType
import com.otakup.niriko.data.remote.bangumi.dto.SearchFilterDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchRequestDto
import com.otakup.niriko.data.remote.bangumi.dto.toEntity
import com.otakup.niriko.data.remote.mapper.SubjectMapper
import com.otakup.niriko.util.resolveCoverUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.DayOfWeek
import java.util.concurrent.ConcurrentHashMap

class BangumiDataSource(
    private val apiService: BangumiApiService,
    private val mapper: SubjectMapper = SubjectMapper(),
) : SubjectRemoteDataSource {

    /**
     * 旧版兜底的负缓存（复审修复）：同一关键词确认「旧版也是空」后短时间内不再重复兜底。
     *
     * 正式搜索按 5 个类型各发一次 POST；「确实没有这个作品」若每次都兜底一遍旧版，
     * 就是 5 次多余请求。
     */
    private val legacyEmptyCache = LegacyEmptyCache()

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
        // 修复 R5：nsfw=true 在 v0 上必须带 token（未登录会被拒）。
        // 关键词非空时优先走免 token 的旧版接口，保证 NSFW 内容在未登录状态下仍可检索。
        if (nsfw == true && keyword.isNotBlank()) {
            legacySearch(keyword, type, limit, offset)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
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
        // 复审修复：runCatching 会把 CancellationException 一起吞掉 —— 取消必须原样抛出
        var postFailure: Exception? = null
        var postResults: List<SubjectEntity>? = null
        try {
            postResults = mapper.fromSearchResponse(apiService.searchSubjects(request, limit = limit, offset = offset))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            postFailure = e
        }
        // 第 6 轮 F5b：POST **真失败**时无条件兜底；POST 返回空时只有「请求不带任何筛选语义」
        // 才兜底（旧版没有 tag/air_date/rank 能力，带筛选时兜底等于悄悄丢掉筛选条件）
        val useLegacy = shouldUseLegacySearch(
            keyword = keyword,
            postFailed = postFailure != null,
            postResultEmpty = postResults.isNullOrEmpty(),
            hasFilters = hasFilterSemantics(tags, airDate, rank, nsfw),
        )
        if (useLegacy) {
            legacySearchOnce(keyword, type, limit, offset)?.let { return it }
        }
        // POST 成功 → 返回它（可能为空 = 确实没有结果）；POST 失败 → 抛出（失败要说话）
        postFailure?.let { throw it }
        return postResults.orEmpty()
    }

    /**
     * 旧版搜索兜底（免 token、含 NSFW）。失败返回 null（不抛）。
     * 旧版没有 tag / air_date / rank 区间能力，因此结果数可能少于 v0。
     */
    private suspend fun legacySearch(
        keyword: String,
        type: Int?,
        limit: Int?,
        offset: Int?,
    ): List<SubjectEntity>? = try {
        val response = apiService.legacySearchSubjects(
            keywords = keyword,
            type = type,
            maxResults = limit ?: 25,
            start = offset ?: 0,
        )
        response.list.map { dto ->
            SubjectEntity(
                subjectId = dto.id,
                title = dto.name,
                titleCN = dto.nameCn,
                type = mapSubjectType(dto.type),
                summary = dto.summary,
                coverUrl = com.otakup.niriko.util.resolveCoverUrl(dto.images),
                totalEpisodes = (dto.epsCount ?: dto.eps)?.takeIf { it > 0 },
                platform = dto.platform,
                volumes = dto.volumes?.takeIf { it > 0 },
                airDate = dto.airDate,
                airWeekday = dto.airWeekday,
                ratingScore = dto.rating?.score?.toFloat(),
                ratingTotal = dto.rating?.total?.takeIf { it > 0 },
                rank = (dto.rating?.rank ?: dto.rank)?.takeIf { it > 0 },
                sourceId = "bangumi",
                lastSyncTime = System.currentTimeMillis(),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 旧版兜底失败：返回 null（不抛），由调用方决定是「确实没有结果」还是把 POST 的失败抛出去
        null
    }

    /**
     * 旧版兜底 + 负缓存：同一「关键词 + 类型」已经确认「旧版也是空」时，短时间内不再重复请求。
     *
     * round 3 复审：key 必须带 type（见 [LegacyEmptyCache]），否则 5 个类型的并行兜底会互相掐掉。
     */
    private suspend fun legacySearchOnce(
        keyword: String,
        type: Int?,
        limit: Int?,
        offset: Int?,
    ): List<SubjectEntity>? {
        if (legacyEmptyCache.shouldSkip(keyword, type)) return null
        val result = legacySearch(keyword, type, limit, offset) ?: return null
        if (result.isEmpty()) legacyEmptyCache.markEmpty(keyword, type)
        return result
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
        if (nsfw == true && keyword.isNotBlank()) {
            legacySearch(keyword, type, limit, offset)?.takeIf { it.isNotEmpty() }?.let {
                return it to it.size
            }
        }
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
        // 复审修复：runCatching 会吞掉 CancellationException —— 取消必须原样抛出
        var postFailure: Exception? = null
        var postResults: Pair<List<SubjectEntity>, Int>? = null
        try {
            val response = apiService.searchSubjects(request, limit = limit, offset = offset)
            postResults = mapper.fromSearchResponse(response) to response.total
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            postFailure = e
        }
        val useLegacy = shouldUseLegacySearch(
            keyword = keyword,
            postFailed = postFailure != null,
            postResultEmpty = postResults == null || postResults.first.isEmpty(),
            hasFilters = hasFilterSemantics(tags, airDate, rank, nsfw),
        )
        if (useLegacy) {
            legacySearchOnce(keyword, type, limit, offset)?.let { return it to it.size }
        }
        postFailure?.let { throw it }
        return postResults ?: (emptyList<SubjectEntity>() to 0)
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

    /**
     * 生日拼接：按可用精度降级（只有年份时输出 "1962"，有月无日时输出 "1962-06"）。
     * Bangumi 的人物/角色生日经常缺日，硬拼成 "-00" 是错的。
     */
    private fun formatBirthday(year: Int?, month: Int?, day: Int?): String? {
        if (year == null && month == null && day == null) return null
        val sb = StringBuilder()
        if (year != null) sb.append(year)
        if (month != null) {
            if (sb.isNotEmpty()) sb.append("-")
            sb.append("%02d".format(month))
        }
        if (day != null && month != null) {
            sb.append("-").append("%02d".format(day))
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    /** Bangumi 血型用数字表示：1=A / 2=B / 3=O / 4=AB。 */
    private fun formatBloodType(code: Int?): String? = when (code) {
        1 -> "A"
        2 -> "B"
        3 -> "O"
        4 -> "AB"
        else -> null
    }

    /** infobox → 可读条目（与条目详情页共用同一套 value 序列化规则）。 */
    private fun List<com.otakup.niriko.data.remote.bangumi.dto.SubInfoboxItem>?.toInfoBoxEntries(): List<InfoBoxEntry> =
        this?.mapNotNull { item ->
            val text = infoBoxValueToText(item.value) ?: return@mapNotNull null
            if (item.key.isBlank() || text.isBlank()) null else InfoBoxEntry(item.key, text)
        } ?: emptyList()

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
            // 阶段 6：这些字段一直在 DTO 里，之前映射时被丢掉了
            gender = dto.gender,
            birthday = formatBirthday(dto.birthYear, dto.birthMonth, dto.birthDay),
            bloodType = formatBloodType(dto.bloodType),
            collects = dto.stat?.collects,
            comments = dto.stat?.comments,
            infoBox = dto.infobox.toInfoBoxEntries(),
        )
    }

    override suspend fun getCharacterSubjects(characterId: Long): List<PersonSubjectInfo> {
        val dtos = apiService.getCharacterSubjects(characterId)
        return dtos.map { it.toPersonSubjectInfo() }
    }

    override suspend fun getPersonDetail(personId: Long): PersonDetailInfo {
        val dto = apiService.getPersonDetail(personId)
        // 职位统计在 ViewModel 侧基于参与作品聚合（见 PersonJobAnalyzer），此处不重复请求
        return PersonDetailInfo(
            id = dto.id,
            name = dto.name,
            nameCn = dto.nameCn,
            summary = dto.summary,
            // 详情页头像用 grid 正方形，避免长立绘裁到身体中段
            imageUrl = dto.images?.grid ?: dto.images?.small,
            career = dto.career,
            // 阶段 6：这些字段一直在 DTO 里，之前映射时被丢掉了
            gender = dto.gender,
            birthday = formatBirthday(dto.birthYear, dto.birthMonth, dto.birthDay),
            bloodType = formatBloodType(dto.bloodType),
            collects = dto.stat?.collects,
            comments = dto.stat?.comments,
            infoBox = dto.infobox.toInfoBoxEntries(),
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

/**
 * 旧版 GET 搜索（GET /search/subject/关键词）的兜底触发条件（第 6 轮 F5b + 复审收窄，纯函数）。
 *
 * 规则（三段式）：
 * 1. 何时兜底 —— keyword 非空，且满足下列任一条：
 *    a) POST 真失败（抛异常）：可能是另一条网络通路，值得一试；
 *    b) POST 成功但返回空，且请求**不带任何筛选语义**（[hasFilterSemantics] 为 false）：
 *       对应「反代吞掉 POST body」这类原始场景 —— 请求发出去了、服务端当成了无条件的空检索。
 * 2. 何时不兜底 ——
 *    a) keyword 为空：旧版接口必须带关键词，browse 类查询（type / rank / air_date 过滤）
 *       在它那里没有等价能力；
 *    b) POST 成功但返回空，且请求带 tag / air_date / rank / nsfw：旧版无法表达这些条件，
 *       用旧版结果顶替等于**悄悄丢掉用户的筛选**（比空结果更误导），
 *       且会让「每个筛选组合都多打一次旧版」把请求数翻倍；
 *    c) POST 成功且有结果：没有兜底的必要。
 * 3. 为什么这样切 —— 兜底的目的是绕开「POST 通路坏掉」，不是把「按条件搜索没有结果」
 *    改写成「按标题搜索」（换一个查询语义）。前者是通路问题，后者是数据问题，不能混。
 *
 * 调用点：search / searchWithTotal 两处，均先算 [hasFilterSemantics] 再判定。
 *
 * @param postFailed POST 是否抛异常（调用方用 try/catch 捕获，取消不在此列）
 * @param postResultEmpty POST 是否成功但结果为空
 * @param hasFilters 请求是否带旧版无法表达的筛选语义，见 [hasFilterSemantics]
 */
internal fun shouldUseLegacySearch(
    keyword: String,
    postFailed: Boolean,
    postResultEmpty: Boolean,
    hasFilters: Boolean = false,
): Boolean = keyword.isNotBlank() && (postFailed || (postResultEmpty && !hasFilters))

/** 请求是否带有「旧版接口无法表达」的筛选语义（tag / 开播日 / 排名 / NSFW）。 */
internal fun hasFilterSemantics(
    tags: List<String>?,
    airDate: List<String>?,
    rank: List<String>?,
    nsfw: Boolean?,
): Boolean = !tags.isNullOrEmpty() || !airDate.isNullOrEmpty() || !rank.isNullOrEmpty() || nsfw == true

/**
 * 旧版兜底的负缓存（复审修复 round 2，round 3 升级 key）。
 *
 * 为什么需要：正式搜索按 5 个类型各发一次 POST；「确实没有这个作品」时若每个空结果
 * 都各兜底一次旧版，就会多打好几次请求。这里把「旧版也是空」记下来，短窗口内不再重试。
 * 只缓存「空」这个结论 —— 命中过的非空结果不会进缓存。
 *
 * 为什么 key 必须带 type（round 3 复审裁决）：legacy 接口本身就是按 (keyword, type) 查询的，
 * 而 5 个类型是**并行**发起的。若 key 只有关键词，就会出现「谁先返回空，谁就掐掉其它类型的
 * 兜底」—— 同一关键词的结果随时序变化（不确定性），60s 内重复搜索甚至可能直接变成「未找到」。
 * 用 keyword + type 后：同一关键词的每个类型各自最多兜底一次（不误伤），
 * 同一关键词 + 同一类型的重复搜索在 [ttlMs] 内仍被抑制；代价是「全空关键词」首轮最多 5 次
 * legacy 请求 —— 这是为「结果确定性」付的账，已由 round 3 复审确认接受。
 *
 * - key = 「关键词原文|type」（type 为 null 记作 all；不做 normalize）
 * - TTL = [ttlMs]，默认 60_000ms（构造可注入，便于单测直接验证过期行为）
 * - 时钟 = [clock]，默认 System.currentTimeMillis，可注入以便单测控制过期
 *
 * 线程安全：5 个类型并行访问同一实例，底层用 [ConcurrentHashMap]；
 * 并发下最坏只是多打一次旧版请求，不会破坏结构、不会产生跨类型误伤。
 */
internal class LegacyEmptyCache(
    private val ttlMs: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val emptyAt = ConcurrentHashMap<String, Long>()

    /** 该「关键词 + 类型」是否在窗口内已被确认「旧版为空」。 */
    fun shouldSkip(keyword: String, type: Int?): Boolean {
        val key = cacheKey(keyword, type)
        val at = emptyAt[key] ?: return false
        if (clock() - at >= ttlMs) {
            emptyAt.remove(key)
            return false
        }
        return true
    }

    /** 记录「旧版对该关键词 + 类型返回空」。 */
    fun markEmpty(keyword: String, type: Int?) {
        emptyAt[cacheKey(keyword, type)] = clock()
    }

    /** 缓存 key：关键词原文 + 类型（null → all）。 */
    private fun cacheKey(keyword: String, type: Int?): String =
        keyword + "|" + (type?.toString() ?: "all")
}

/** Bangumi weekday id → java.time.DayOfWeek。Bangumi: 0=Sunday, 1=Monday … 6=Saturday。 */
internal fun mapBangumiWeekday(id: Int): DayOfWeek =
    if (id == 0) DayOfWeek.SUNDAY
    else if (id in 1..6) DayOfWeek.of(id)
    else DayOfWeek.SUNDAY  // fallback
