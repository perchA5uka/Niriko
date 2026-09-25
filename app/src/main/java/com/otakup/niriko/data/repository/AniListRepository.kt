package com.otakup.niriko.data.repository

import android.util.Log
import com.otakup.niriko.data.local.dao.AniListDao
import com.otakup.niriko.data.local.entity.AniListBindingEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.AniListRichDetail
import com.otakup.niriko.data.remote.anilist.AniListGameDataSource
import com.otakup.niriko.data.remote.game.GameItem
import com.otakup.niriko.data.remote.game.GameItemDetail
import com.otakup.niriko.data.remote.steam.SteamTitleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AniListRepo"

/**
 * AniList 补充数据仓储（纯信息源，不做用户库导入）。
 *
 * 与 [com.otakup.niriko.data.repository.VndbRepository] 同构，但**不限制匹配类型**：
 * 动画/漫画/游戏等任何 bangumi 词条都可绑定 AniList 条目（AniList 覆盖 ANIME+MANGA，
 * 无 GAME 限制），补充英文名/原名/评分/状态/集数/标签/简介等信息。
 *
 * 职责（阶段 G 绑定保守化后）：
 * - 读取：按 subjectId 返回 AniList 绑定（详情页 AniList 区块用）
 * - 搜索：AniList 标题搜索（候选列表，供用户手动确认绑定）
 * - 手动绑定 / 解绑：详情页候选一键绑定、错绑数据解绑
 *
 * 注意：**不再做标题自动匹配写库**。原 matchAndBind 的「标题置信度 ≥ 阈值即写入绑定表」
 * 已取消（降低错绑）。标题搜索仅产出候选（[searchCandidates]），绑定只经由用户手动确认
 * （[bindManually]）。若未来存在"已知 anilistId 映射"，可在此补一条仅 ID 通道的自动绑定。
 *
 * 全部方法异常保护：AniList 是补充数据源，任何失败静默返回，不影响主流程。
 */
class AniListRepository(
    private val anilistDao: AniListDao,
    /** 复用 AniListGameDataSource 的 GraphQL 搜索/详情（sourceGameId = "media-{id}"）。 */
    private val gameSource: AniListGameDataSource = AniListGameDataSource(),
) {

    /** 读取某条目的 AniList 绑定（详情页 AniList 区块用）。未绑定返回 null。 */
    suspend fun getBinding(subjectId: Long): AniListBindingEntity? = withContext(Dispatchers.IO) {
        runCatching { anilistDao.getBindingBySubjectId(subjectId) }.getOrNull()
    }

    /** 按 AniList id 拉取详情（含简介/评分/标签/状态等，详情页区块用）。失败返回 null。 */
    suspend fun getDetail(anilistId: Long): GameItemDetail? {
        val result = try {
            gameSource.getDetail("media-$anilistId")
        } catch (e: Exception) {
            Log.w(TAG, "getDetail failed anilistId=$anilistId", e)
            null
        }
        if (result == null) Log.w(TAG, "getDetail returned null anilistId=$anilistId")
        return result
    }

    /** 按 AniList id 拉取独有富信息（热度/趋势/排名/下一集等）。失败返回 null。 */
    suspend fun getRichDetail(anilistId: Long): AniListRichDetail? {
        val result = try {
            gameSource.getRichDetail(anilistId)
        } catch (e: Exception) {
            Log.w(TAG, "getRichDetail failed anilistId=$anilistId", e)
            null
        }
        if (result == null) Log.w(TAG, "getRichDetail returned null anilistId=$anilistId")
        return result
    }

    /** 按任意关键词搜索 AniList（详情页“搜索更多”用）。失败返回空。 */
    suspend fun searchMore(query: String, limit: Int = 10): List<GameItem> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { gameSource.search(query, limit = limit) }.getOrDefault(emptyList())
        }
    }

    /**
     * 保守自动匹配：多标题搜 AniList，本地用 [SteamTitleMatcher] 评分。
     * 仅当 精确相等 或 置信度 ≥ 0.95 才写库；其余返回 false（仍展示候选）。
     */
    suspend fun autoBindMatch(subject: SubjectEntity): Boolean {
        val titles = listOfNotNull(subject.title, subject.titleCN)
            .filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty()) {
            Log.w(TAG, "autoBindMatch subjectId=${subject.subjectId} titles empty")
            return false
        }
        val results = try {
            withContext(Dispatchers.IO) { gameSource.search(titles.first(), limit = 10) }
        } catch (e: Exception) {
            Log.w(TAG, "autoBindMatch search failed subjectId=${subject.subjectId} query=${titles.first()}", e)
            emptyList()
        }
        if (results.isEmpty()) {
            Log.w(TAG, "autoBindMatch no results subjectId=${subject.subjectId}")
            return false
        }
        val best = results.map { item ->
            val candidateTitles = listOfNotNull(item.title, item.aliases) + item.aliases?.split(" / ").orEmpty()
            item to SteamTitleMatcher.bestConfidence(titles, candidateTitles)
        }.maxByOrNull { it.second }
        val (item, score) = best ?: return false
        val exact = titles.any { t ->
            SteamTitleMatcher.normalizeTitle(t) == SteamTitleMatcher.normalizeTitle(item.title)
        }
        val id = item.sourceGameId.removePrefix("media-").toLongOrNull() ?: return false
        if (exact || score >= 0.95f) {
            Log.w(TAG, "autoBindMatch binding subjectId=${subject.subjectId} anilistId=$id score=$score exact=$exact")
            return bindManually(subject.subjectId, id)
        }
        Log.w(TAG, "autoBindMatch no high-confidence match subjectId=${subject.subjectId} best=$score")
        return false
    }

    /** 候选搜索（详情页无绑定展示用）：用 Bangumi title/titleCN 搜 AniList，返回 Top N 候选。 */
    suspend fun searchCandidates(
        subject: SubjectEntity,
        limit: Int = 5,
    ): List<GameItem> {
        val titles = listOfNotNull(subject.title, subject.titleCN)
            .filter { it.isNotBlank() }.distinct()
        if (titles.isEmpty()) return emptyList()
        val merged = mutableListOf<GameItem>()
        for (t in titles) {
            try {
                gameSource.search(t, limit = limit).forEach { item ->
                    if (merged.none { it.sourceGameId == item.sourceGameId }) merged.add(item)
                }
            } catch (e: Exception) {
                Log.w(TAG, "searchCandidates failed subjectId=${subject.subjectId} query=$t", e)
            }
        }
        Log.w(TAG, "searchCandidates subjectId=${subject.subjectId} titles=$titles results=${merged.size}")
        return merged.take(limit)
    }

    /** 解除绑定（错绑数据手动解绑）。异常保护。 */
    suspend fun unbind(subjectId: Long) {
        withContext(Dispatchers.IO) {
            runCatching { anilistDao.deleteBindingBySubjectId(subjectId) }
        }
    }

    /** 手动绑定（详情页候选一键绑定用）。失败返回 false。 */
    suspend fun bindManually(subjectId: Long, anilistId: Long): Boolean {
        if (anilistId <= 0) return false
        return try {
            withContext(Dispatchers.IO) {
                anilistDao.upsertBinding(
                    AniListBindingEntity(
                        subjectId = subjectId,
                        anilistId = anilistId,
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
