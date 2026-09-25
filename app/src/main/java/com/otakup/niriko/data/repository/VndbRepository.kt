package com.otakup.niriko.data.repository

import android.util.Log
import com.otakup.niriko.data.local.dao.VndbDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.VndbBindingEntity
import com.otakup.niriko.data.remote.steam.SteamTitleMatcher
import com.otakup.niriko.data.remote.vndb.VndbApiClient
import com.otakup.niriko.data.remote.vndb.VndbApiService
import com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest
import com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

private const val TAG = "VndbRepo"

/**
 * VNDB 补充数据仓储（纯信息源，不做用户库导入）。
 *
 * 职责（阶段 G 绑定保守化后）：
 * - 读取：按 subjectId 返回 VNDB 绑定（详情页 VNDB 区块用）
 * - 搜索：VNDB 标题搜索（候选列表，供用户手动确认绑定）
 * - 手动绑定 / 解绑：详情页候选一键绑定、错绑数据解绑
 *
 * 注意：**不再做标题自动匹配写库**。原 matchAndBind 的「标题置信度 ≥ 阈值即写入绑定表」
 * 已取消（降低错绑）。标题搜索仅产出候选（[searchCandidates]），绑定只经由用户手动确认
 * （[bindManually]）。若未来存在"已知 vndbId 映射"，可在此补一条仅 ID 通道的自动绑定。
 *
 * 全部方法异常保护：VNDB 是补充数据源，任何失败静默返回，不影响主流程。
 */
class VndbRepository(
    private val vndbDao: VndbDao,
    private val apiService: VndbApiService = VndbApiClient.apiService,
) {

    /** 候选/搜索用的稳定字段（保证能搜到结果）。 */
    private val searchFields = listOf(
        "title", "alttitle", "titles.lang", "titles.title", "titles.latin",
        "description", "developers.name",
        "tags.name", "released", "rating", "votecount", "popularity",
        "image.url", "length_minutes", "platforms", "olang",
    ).joinToString(", ")

    /**
     * 详情扩展字段。
     *
     * 第 4 轮 E 补齐了 VNDB **独有**的差异化数据（Bangumi 通常没有或更差）：
     * popularity、relations、languages、带权重的 tags、各平台发售日（见 [getPlatformReleases]）。
     */
    private val detailFields = listOf(
        "title", "alttitle", "titles.lang", "titles.title", "titles.latin",
        "description", "developers.name", "developers.original",
        "tags.name", "tags.category", "tags.rating", "tags.spoiler",
        "released", "rating", "votecount", "popularity",
        "image.url", "length", "length_minutes", "platforms", "olang", "languages",
        "screenshots.id", "screenshots.url", "screenshots.thumbnail", "screenshots.dims",
        "relations.id", "relations.relation", "relations.relation_official",
    ).joinToString(", ")

    /** 按标题搜索 VNDB（filters=["search","=",标题] + sort=searchrank）。失败返回空。 */
    private suspend fun searchVndb(title: String): List<VndbVisualNovelDto> {
        if (title.isBlank()) return emptyList()
        return try {
            val resp = apiService.query(
                VndbQueryRequest(
                    filters = buildJsonArray { add("search"); add("="); add(title) },
                    fields = searchFields,
                    sort = "searchrank",
                    results = 10,
                )
            )
            Log.w(TAG, "searchVndb query=$title results=${resp.results.size}")
            resp.results
        } catch (e: Exception) {
            Log.w(TAG, "searchVndb failed query=$title", e)
            emptyList()
        }
    }

    /**
     * 候选搜索（详情页无绑定展示用）：用 Bangumi title/titleCN 搜 VNDB，返回 Top N 候选。
     * 失败返回空（补充数据源，不影响主流程）。
     */
    suspend fun searchCandidates(
        subject: SubjectEntity,
        limit: Int = 5,
    ): List<VndbVisualNovelDto> {
        val titles = listOfNotNull(subject.title, subject.titleCN)
            .filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty()) return emptyList()
        // 按标题搜索，全部标题都搜完后合并去重（避免第一个标题填满 5 条顶掉正确候选），再取前 limit。
        val merged = mutableListOf<VndbVisualNovelDto>()
        for (t in titles) {
            searchVndb(t).forEach { vn ->
                if (merged.none { it.id == vn.id }) merged.add(vn)
            }
        }
        Log.w(TAG, "searchCandidates subjectId=${subject.subjectId} titles=$titles results=${merged.size}")
        return merged.take(limit)
    }

    /** 按任意关键词搜索 VNDB（详情页“搜索更多”用）。失败返回空。 */
    suspend fun searchMore(query: String, limit: Int = 10): List<VndbVisualNovelDto> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { searchVndb(query).take(limit) }.getOrDefault(emptyList())
        }
    }

    /**
     * 保守自动匹配：多标题搜 VNDB，本地用 [SteamTitleMatcher] 评分。
     * 仅当 精确相等 或 置信度 ≥ 0.95 才写库；其余返回 false（仍展示候选）。
     */
    suspend fun autoBindMatch(subject: SubjectEntity): Boolean {
        val titles = listOfNotNull(subject.title, subject.titleCN)
            .filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty()) {
            Log.w(TAG, "autoBindMatch subjectId=${subject.subjectId} titles empty")
            return false
        }
        val results = withContext(Dispatchers.IO) {
            titles.flatMap { searchVndb(it) }.distinctBy { it.id }
        }
        if (results.isEmpty()) {
            Log.w(TAG, "autoBindMatch no results subjectId=${subject.subjectId}")
            return false
        }
        val best = results.map { vn ->
            val candidateTitles = listOfNotNull(vn.title, vn.ctitle, vn.alttitle) +
                vn.titles.filter { it.lang.startsWith("zh") }.map { it.title }
            vn to SteamTitleMatcher.bestConfidence(titles, candidateTitles)
        }.maxByOrNull { it.second }
        val (vn, score) = best ?: return false
        val exact = titles.any { t ->
            SteamTitleMatcher.normalizeTitle(t) == SteamTitleMatcher.normalizeTitle(vn.title) ||
                (vn.ctitle?.let { SteamTitleMatcher.normalizeTitle(t) == SteamTitleMatcher.normalizeTitle(it) } == true)
        }
        if (exact || score >= 0.95f) {
            Log.w(TAG, "autoBindMatch binding subjectId=${subject.subjectId} vndbId=${vn.id} score=$score exact=$exact")
            return bindManually(subject.subjectId, vn.id)
        }
        Log.w(TAG, "autoBindMatch no high-confidence match subjectId=${subject.subjectId} best=$score")
        return false
    }

    /** 读取某条目的 VNDB 绑定（详情页 VNDB 区块用）。未绑定返回 null。 */
    suspend fun getBinding(subjectId: Long): VndbBindingEntity? = withContext(Dispatchers.IO) {
        runCatching { vndbDao.getBindingBySubjectId(subjectId) }.getOrNull()
    }

    /** 按 VNDB id 拉取详情（含简介/标签/时长等，详情页区块用）。失败返回 null。 */
    suspend fun getDetail(vndbId: String): VndbVisualNovelDto? {
        if (vndbId.isBlank()) {
            Log.w(TAG, "getDetail blank vndbId")
            return null
        }
        val extended = try {
            apiService.query(
                VndbQueryRequest(
                    filters = buildJsonArray { add("id"); add("="); add(vndbId) },
                    fields = detailFields,
                    results = 1,
                )
            ).results.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "getDetail extended fields failed vndbId=$vndbId", e)
            null
        }
        if (extended != null) {
            Log.w(TAG, "getDetail vndbId=$vndbId title=${extended.title} screenshots=${extended.screenshots.size} languages=${extended.languages.size}")
            return extended
        }
        // 扩展字段可能不被该版本 API 识别 → 用最小字段重试，保证已绑定区块仍可显示。
        val fallback = try {
            apiService.query(
                VndbQueryRequest(
                    filters = buildJsonArray { add("id"); add("="); add(vndbId) },
                    fields = searchFields,
                    results = 1,
                )
            ).results.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "getDetail fallback failed vndbId=$vndbId", e)
            null
        }
        if (fallback == null) Log.w(TAG, "getDetail returned null vndbId=$vndbId")
        return fallback
    }

    /** 解除绑定（错绑数据手动解绑）。异常保护。 */
    suspend fun unbind(subjectId: Long) {
        withContext(Dispatchers.IO) {
            runCatching { vndbDao.deleteBindingBySubjectId(subjectId) }
        }
    }

    /**
     * 补齐关联作品的标题（第 4 轮 E）。
     *
     * VNDB 的 `relations` 只给 id 与关系类型，标题要二次查询。为了省额度：
     * - 一次查询用 `["id","in",[ids]]` 批量取回；
     * - 最多取前 [limit] 条（关系多的作品可能十几个，全部显示反而没重点）。
     *
     * 用户场景：某作品匹配不上，但它的**同世界观作**匹配得上——有了标题，
     * 用户就能顺着关系链找到条目 id 再手动绑定。
     */
    suspend fun resolveRelationTitles(
        relations: List<com.otakup.niriko.data.remote.vndb.dto.VndbRelationDto>,
        limit: Int = 12,
    ): List<RelationWithTitle> = withContext(Dispatchers.IO) {
        val targets = relations
            .filter { it.id.isNotBlank() }
            // 先处理「官方 + 强关系」，这些才是用户真正关心的
            .sortedByDescending { if (it.relationOfficial) 1 else 0 }
            .distinctBy { it.id }
            .take(limit)
        if (targets.isEmpty()) return@withContext emptyList()

        val titles = runCatching {
            apiService.query(
                VndbQueryRequest(
                    filters = kotlinx.serialization.json.buildJsonArray {
                        add("id"); add("in")
                        add(kotlinx.serialization.json.buildJsonArray {
                            targets.forEach { add(it.id) }
                        })
                    },
                    fields = "id, title, alttitle, titles.lang, titles.title, released, rating, image.url",
                    results = targets.size.coerceAtLeast(1),
                )
            ).results.associateBy { it.id }
        }.getOrDefault(emptyMap())

        targets.map { relation ->
            val vn = titles[relation.id]
            RelationWithTitle(
                vndbId = relation.id,
                relation = relation.relation,
                relationLabel = relationLabel(relation.relation),
                title = vn?.title?.takeIf { it.isNotBlank() } ?: relation.title ?: relation.id,
                // 中文名优先（与详情页其它区块口径一致）
                ctitle = vn?.titles?.firstOrNull { it.lang.startsWith("zh") }?.title,
                released = vn?.released,
                rating = vn?.rating,
                coverUrl = vn?.image?.url,
                official = relation.relationOfficial,
            )
        }
    }

    /** 关联作品（带已解析的标题）。 */
    data class RelationWithTitle(
        val vndbId: String,
        val relation: String,
        val relationLabel: String,
        val title: String,
        val ctitle: String?,
        val released: String?,
        val rating: Int?,
        val coverUrl: String?,
        val official: Boolean,
    ) {
        val displayTitle: String get() = ctitle?.takeIf { it.isNotBlank() } ?: title
    }

    /**
     * VNDB 关系类型 → 中文标签。
     *
     * VNDB 的官方类型码（api-kana 的 vn.relation 枚举）：
     * seq=续作, preq=前作, set=同设定, alt=另一个版本, char=共用角色,
     * side=外传, par=同系列, ser=同系列（更宽）, fan=同人, orig=原作。
     */
    private fun relationLabel(code: String): String = when (code.lowercase()) {
        "seq" -> "续作"
        "preq" -> "前作"
        "set" -> "同世界观"
        "alt" -> "另一版本"
        "char" -> "共用角色"
        "side" -> "外传"
        "par" -> "同系列"
        "ser" -> "同系列"
        "fan" -> "同人"
        "orig" -> "原作"
        else -> code
    }

    /** 手动绑定（详情页候选一键绑定用）。失败返回 false。 */
    suspend fun bindManually(subjectId: Long, vndbId: String): Boolean {
        if (vndbId.isBlank()) return false
        return try {
            withContext(Dispatchers.IO) {
                vndbDao.upsertBinding(
                    VndbBindingEntity(
                        subjectId = subjectId,
                        vndbId = vndbId,
                        matchMethod = "MANUAL",
                        confidence = 1f,
                        createTime = System.currentTimeMillis(),
                    )
                )
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "bindManually failed", e)
            false
        }
    }
}
