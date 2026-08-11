package com.otakup.niriko.data.repository

import android.util.Log
import com.otakup.niriko.data.local.dao.VndbDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.VndbBindingEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.steam.SteamTitleMatcher
import com.otakup.niriko.data.remote.vndb.VndbApiClient
import com.otakup.niriko.data.remote.vndb.VndbApiService
import com.otakup.niriko.data.remote.vndb.dto.VndbQueryRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add

private const val TAG = "VndbRepo"

/**
 * VNDB 补充数据仓储（纯信息源，不做用户库导入）。
 *
 * 职责：
 * - 匹配：Bangumi GAME 条目 → VNDB 条目（标题搜索 + 置信度判定），绑定落库
 * - 读取：按 subjectId 返回 VNDB 绑定（详情页 VNDB 区块用）
 * - 搜索：VNDB 标题搜索（VNDB 游戏条目作为独立作品展示时用）
 *
 * 全部方法异常保护：VNDB 是补充数据源，任何失败静默返回，不影响主流程。
 */
class VndbRepository(
    private val vndbDao: VndbDao,
    private val apiService: VndbApiService = VndbApiClient.apiService,
) {

    /** 查询字段（搜索也带全字段，保证卡片信息完整）。 */
    private val detailFields = listOf(
        "title", "alttitle", "titles.lang", "titles.title", "titles.latin",
        "description", "developers.name",
        "tags.name", "released", "rating", "votecount",
        "image.url", "length_minutes", "platforms", "olang",
    ).joinToString(", ")

    /**
     * 对 GAME 条目执行 VNDB 匹配并落库（标题置信度 ≥ [SteamTitleMatcher.MIN_CONFIDENCE] 才绑定）。
     * 与 Steam 绑定互通：同一 bangumi 词条可同时绑 Steam + VNDB（各自独立表）。
     */
    suspend fun matchAndBind(subjects: List<SubjectEntity>): List<VndbBindingEntity> {
        val games = subjects.filter { it.type == SubjectType.GAME }
        if (games.isEmpty()) return emptyList()

        val alreadyBound = vndbDao.getBindingsBySubjectIds(games.map { it.subjectId })
            .map { it.subjectId }
            .toSet()
        val toMatch = games.filter { it.subjectId !in alreadyBound }
        if (toMatch.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val results = withContext(Dispatchers.IO) {
            toMatch.chunked(5).flatMap { batch ->
                coroutineScope {
                    batch.map { subject ->
                        async {
                            try {
                                val title = subject.titleCN?.takeIf { it.isNotBlank() } ?: subject.title
                                val candidates = searchVndb(title)
                                val best = candidates
                                    .mapNotNull { vn ->
                                        val score = SteamTitleMatcher.confidence(title, vn.title)
                                        if (score >= SteamTitleMatcher.MIN_CONFIDENCE) vn to score else null
                                    }
                                    .maxByOrNull { it.second }
                                    ?.first ?: return@async null
                                val binding = VndbBindingEntity(
                                    subjectId = subject.subjectId,
                                    vndbId = best.id,
                                    matchMethod = "AUTO",
                                    confidence = (candidates.firstOrNull { it.id == best.id }
                                        ?.let { vn ->
                                            SteamTitleMatcher.confidence(title, vn.title)
                                        } ?: 0f),
                                    createTime = now,
                                )
                                vndbDao.upsertBinding(binding)
                                binding
                            } catch (e: Exception) {
                                Log.w(TAG, "match failed for ${subject.subjectId}", e)
                                null
                            }
                        }
                    }.awaitAll()
                }
            }
        }
        return results.filterNotNull()
    }

    /** 按标题搜索 VNDB（filters=["search","=",标题] + sort=searchrank）。失败返回空。 */
    private suspend fun searchVndb(title: String): List<com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto> {
        if (title.isBlank()) return emptyList()
        return runCatching {
            apiService.query(
                VndbQueryRequest(
                    filters = buildJsonArray { add("search"); add("="); add(title) },
                    fields = detailFields,
                    sort = "searchrank",
                    results = 10,
                )
            ).results
        }.getOrDefault(emptyList())
    }

    /** 读取某条目的 VNDB 绑定（详情页 VNDB 区块用）。未绑定返回 null。 */
    suspend fun getBinding(subjectId: Long): VndbBindingEntity? = withContext(Dispatchers.IO) {
        runCatching { vndbDao.getBindingBySubjectId(subjectId) }.getOrNull()
    }

    /** 按 VNDB id 拉取详情（含简介/标签/时长等，详情页区块用）。失败返回 null。 */
    suspend fun getDetail(vndbId: String): com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto? {
        if (vndbId.isBlank()) return null
        return runCatching {
            apiService.query(
                VndbQueryRequest(
                    filters = buildJsonArray { add("id"); add("="); add(vndbId) },
                    fields = detailFields,
                    results = 1,
                )
            ).results.firstOrNull()
        }.getOrDefault(null)
    }

    /** 解除绑定（错绑数据手动解绑）。异常保护。 */
    suspend fun unbind(subjectId: Long) {
        withContext(Dispatchers.IO) {
            runCatching { vndbDao.deleteBindingBySubjectId(subjectId) }
        }
    }
}
