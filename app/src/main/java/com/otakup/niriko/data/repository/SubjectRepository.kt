package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.remote.PersonDetailInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val TAG = "SubjectRepo"

/**
 * 主题（作品元数据）仓库。
 *
 * 缓存策略 — 简单可靠：
 * - 发现页（keyword=""）：始终返回本地缓存，不发起远程请求
 * - 搜索页（keyword!=""）：始终尝试远程 API，失败时回退本地缓存
 * - 详情页：始终尝试远程 API，优先展示缓存（若存在）
 * - 所有远程失败都静默回退缓存，不抛异常不崩溃
 */
class SubjectRepository(
    private val subjectDao: SubjectDao,
    private val remoteDataSource: SubjectRemoteDataSource,
) {

    fun observeAll(): Flow<List<SubjectEntity>> =
        subjectDao.observeAll()

    fun observeById(id: Long): Flow<SubjectEntity?> =
        subjectDao.observeById(id)

    suspend fun getById(id: Long): SubjectEntity? =
        subjectDao.getById(id)

    /**
     * 搜索作品 — 始终尝试远程 API，失败回退本地缓存。
     * 不检查网络状态，全靠 try-catch 兜底。
     * 无关键字时也发远程请求（Bangumi API 在 filter 参数下可处理空关键字）。
     */
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
        forceRefresh: Boolean = false,
    ): List<SubjectEntity> {
        // 始终尝试远程
        return try {
            // 落库由 DataSourceChain 内部批量完成（单事务），此处不再重复写入
            remoteDataSource.search(keyword, type, tags, airDate, rank, nsfw, sort, limit, offset)
        } catch (e: Exception) {
            Log.e(TAG, "search failed: keyword=$keyword", e)
            // 远程失败 → 回退本地缓存
            if (keyword.isNotBlank()) {
                val fallback = subjectDao.searchByKeyword(keyword).first()
                if (fallback.isNotEmpty()) return fallback
            }
            subjectDao.observeAll().first()
        }
    }

    /**
     * 获取详情 — 始终尝试远程，回退缓存。
     * 写库时合并本地已存的 Bilibili 补充字段（远程详情不含 bili 列，直接覆盖会抹掉 B 站评分）。
     */
    suspend fun getDetail(
        subjectId: Long,
        forceRefresh: Boolean = false,
    ): SubjectEntity? {
        // 尝试远程（不检查网络状态，失败即回退）
        return try {
            val remote = remoteDataSource.getDetail(subjectId)
            val now = System.currentTimeMillis()
            val cached = subjectDao.getById(subjectId)
            val merged = mergeBiliSupplementary(remote, cached, now)
            subjectDao.upsert(merged)
            merged
        } catch (e: Exception) {
            Log.e(TAG, "getDetail failed: subjectId=$subjectId", e)
            subjectDao.getById(subjectId) // 回退缓存
        }
    }

    suspend fun insert(subject: SubjectEntity) {
        subjectDao.insert(subject.copy(lastSyncTime = System.currentTimeMillis()))
    }

    /** 更新或插入(update-first)。用于后台异步写回补充数据(如 Bilibili 评分)。 */
    suspend fun upsert(subject: SubjectEntity) {
        subjectDao.upsert(subject.copy(lastSyncTime = System.currentTimeMillis()))
    }

    /** 查询 airDate 在指定范围内的作品（本地回退放送日历用）。 */
    suspend fun getSubjectsInAirDateRange(startDate: String, endDate: String): List<SubjectEntity> =
        subjectDao.getSubjectsInAirDateRange(startDate, endDate)

    companion object {
        /**
         * 合并远程详情与本地缓存的 Bilibili 补充字段。
         * 远程详情(DTO)不含 bili 三列,均为 null;若本地已有 B 站评分,直接覆盖会抹掉。
         * 规则:远端字段非空用远端,否则用本地旧值。纯函数便于单测。
         */
        internal fun mergeBiliSupplementary(
            remote: SubjectEntity,
            cached: SubjectEntity?,
            now: Long = System.currentTimeMillis(),
        ): SubjectEntity {
            if (cached == null) return remote.copy(lastSyncTime = now)
            return remote.copy(
                biliScore = remote.biliScore ?: cached.biliScore,
                biliRatingTotal = remote.biliRatingTotal ?: cached.biliRatingTotal,
                biliSeasonId = remote.biliSeasonId ?: cached.biliSeasonId,
                lastSyncTime = now,
            )
        }
    }

    /** 查询所有有 airDate 的作品。 */
    suspend fun getAllWithAirDate(): List<SubjectEntity> =
        subjectDao.getAllWithAirDate()

    /**
     * 搜索并返回带 total 的结果（用于搜索页）。
     * 始终请求远程，失败回退本地。
     */
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
    ): Pair<List<SubjectEntity>, Int> {
        return try {
            // 落库由 DataSourceChain 内部批量完成（单事务），此处不再重复写入
            remoteDataSource.searchWithTotal(
                keyword, type, tags, airDate, rank, nsfw, sort, limit, offset,
            )
        } catch (e: Exception) {
            Log.e(TAG, "searchWithTotal failed: keyword=$keyword", e)
            // 回退本地
            if (keyword.isNotBlank()) {
                val fallback = subjectDao.searchByKeyword(keyword).first()
                if (fallback.isNotEmpty()) return fallback to fallback.size
            }
            emptyList<SubjectEntity>() to 0
        }
    }

    /** 按标题前缀搜索本地作品（用于自动补全）。 */
    suspend fun searchByPrefix(keyword: String): List<SubjectEntity> =
        subjectDao.searchByKeywordPrefix(keyword)

    /** 人物搜索（声优/导演等），返回含代表作品的人物列表。 */
    suspend fun searchPersons(keyword: String): List<PersonDetailInfo> {
        return try {
            remoteDataSource.searchPersons(keyword)
        } catch (e: Exception) {
            Log.e(TAG, "searchPersons failed: keyword=$keyword", e)
            emptyList()
        }
    }

    /** 获取类型排名榜（GET，绕开 POST body 被吞）。30s 内存缓存加速重复浏览。 */
    private val rankingCache = mutableMapOf<String, Pair<List<SubjectEntity>, Long>>()

    suspend fun getRanking(type: Int, offset: Int = 0, limit: Int = 20): List<SubjectEntity> {
        val key = "$type-$offset"
        val cached = rankingCache[key]
        if (cached != null && System.currentTimeMillis() - cached.second < 30_000) {
            return cached.first
        }
        return try {
            // 落库由 DataSourceChain 内部批量完成（单事务），此处不再重复写入
            val remote = remoteDataSource.getRankingByType(type, offset, limit)
            val now = System.currentTimeMillis()
            rankingCache[key] = remote to now
            remote
        } catch (e: Exception) {
            Log.e(TAG, "getRanking failed: type=$type", e)
            emptyList()
        }
    }

    /** 清除排名缓存（下拉刷新/切换类型时调用，强制重新拉取）。 */
    fun clearRankingCache() {
        rankingCache.clear()
    }
}
