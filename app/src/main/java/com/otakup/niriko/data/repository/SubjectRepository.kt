package com.otakup.niriko.data.repository

import com.otakup.niriko.data.local.SubjectWriteGateway
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.onair.OnAirRepository
import com.otakup.niriko.util.PinyinSearch
import com.otakup.niriko.data.remote.PersonDetailInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.plugin.DataSourceChain
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
    private val onAirRepository: OnAirRepository? = null,
    /** 写入网关（批量单事务 + 内容 diff）。未注入时退化为直写 DAO（单测/兼容）。 */
    private val writeGateway: SubjectWriteGateway? = null,
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
                // 阶段 D：拼音命中（子串，juren→进击的巨人）
                val pinyin = subjectDao.searchByPinyin(keyword)
                if (pinyin.isNotEmpty()) return pinyin
                // 关键词有值但无任何命中：返回空，避免倒出全库（修复历史 bug）
                return emptyList()
            }
            subjectDao.observeAll().first()
        }
    }

    /**
     * 获取详情 — 始终尝试远程，回退缓存。
     * 写库时合并本地已存的 Bilibili 补充字段（远程详情不含 bili 列，直接覆盖会抹掉 B 站评分）。
     */
    suspend fun getDetail(subjectId: Long): SubjectEntity? {
        // 尝试远程（不检查网络状态，失败即回退）
        return try {
            val remote = remoteDataSource.getDetail(subjectId)
            val now = System.currentTimeMillis()
            val cached = subjectDao.getById(subjectId)
            val merged = mergeBiliSupplementary(remote, cached, now)
            // 阶段 A：用静态 onair 数据补齐精确放送时刻；阶段 D：补齐拼音搜索键
            val enriched = finalizeSubject(merged)
            subjectDao.upsert(enriched)
            enriched
        } catch (e: Exception) {
            Log.e(TAG, "getDetail failed: subjectId=$subjectId", e)
            subjectDao.getById(subjectId) // 回退缓存
        }
    }

    suspend fun insert(subject: SubjectEntity) {
        val finalized = finalizeSubject(subject)
        writeGateway?.upsert(finalized)
            ?: subjectDao.insert(finalized.copy(lastSyncTime = System.currentTimeMillis()))
    }

    /** 更新或插入(update-first)。用于后台异步写回补充数据(如 Bilibili 评分)。 */
    suspend fun upsert(subject: SubjectEntity) {
        val finalized = finalizeSubject(subject)
        writeGateway?.upsert(finalized)
            ?: subjectDao.upsert(finalized.copy(lastSyncTime = System.currentTimeMillis()))
    }

    /**
     * 批量写入（**单事务 + 内容 diff**）。
     *
     * 放送日历 / 季节数据这类「一次几十到上百条」的刷新必须走这里：
     * 逐条 upsert 会产生同样多次 Room 失效通知，而作品库列表同时观察 subjects 表，
     * 于是每写一条就全量重算一次。
     *
     * @return 实际写入行数；未注入网关时退化为直接批量写并返回总数。
     */
    suspend fun upsertAll(subjects: List<SubjectEntity>): Int {
        if (subjects.isEmpty()) return 0
        val finalized = subjects.map { finalizeSubject(it) }
        val gateway = writeGateway
        if (gateway != null) return gateway.upsertAll(finalized).written
        val now = System.currentTimeMillis()
        subjectDao.upsertAll(finalized.map { it.copy(lastSyncTime = now) })
        return finalized.size
    }

    /** 落库前统一补充：onair 精确放送时刻 + 拼音搜索键。 */
    private fun finalizeSubject(s: SubjectEntity): SubjectEntity {
        val enriched = onAirRepository?.enrich(s) ?: s
        return if (enriched.pinyinKey == null) {
            enriched.copy(pinyinKey = PinyinSearch.pinyinKey(enriched.title, enriched.titleCN))
        } else enriched
    }

    /** 查询 airDate 在指定范围内的作品（本地回退放送日历用）。 */
    suspend fun getSubjectsInAirDateRange(startDate: String, endDate: String): List<SubjectEntity> =
        subjectDao.getSubjectsInAirDateRange(startDate, endDate)

    companion object {
        /** 榜单内存微缓存的存活时间（趋势区与触底分页会连打两次同一请求）。 */
        private const val RANKING_CACHE_TTL_MS = 30_000L

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
     *
     * 第 6 轮 F5：返回值从 Pair 升级为 [SearchOutcome] ——
     * 「远程失败」与「确实没有结果」必须能区分，否则 UI 只能把断网显示成「未找到相关作品」。
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
    ): SearchOutcome {
        return try {
            // 落库由 DataSourceChain 内部批量完成（单事务），此处不再重复写入
            val (results, total) = remoteDataSource.searchWithTotal(
                keyword, type, tags, airDate, rank, nsfw, sort, limit, offset,
            )
            SearchOutcome(results = results, total = total)
        } catch (e: CancellationException) {
            // 取消不是失败：原样抛出，不写任何结果（否则 collectLatest 的取消会被当成网络错误）
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "searchWithTotal failed: keyword=$keyword", e)
            // 回退本地：命中仍然返回，但必须标注为「离线结果」，不能假装是远程结果
            val fallback = if (keyword.isNotBlank()) {
                val byKeyword = subjectDao.searchByKeyword(keyword).first()
                if (byKeyword.isNotEmpty()) byKeyword
                // 阶段 D：拼音命中（子串，juren→进击的巨人）
                else subjectDao.searchByPinyin(keyword)
            } else {
                emptyList()
            }
            SearchOutcome(
                results = fallback,
                total = fallback.size,
                remoteFailed = true,
                offline = fallback.isNotEmpty(),
                failureReason = networkFailureMessage(e),
            )
        }
    }

    /** 按标题前缀搜索本地作品（用于自动补全）。 */
    suspend fun searchByPrefix(keyword: String): List<SubjectEntity> =
        subjectDao.searchByKeywordPrefix(keyword).ifEmpty { subjectDao.searchByPinyinPrefix(keyword) }

    /**
     * 搜索建议专用：主链（Bangumi/AniList）优先，无结果时走完整链（含游戏源旁路），
     * 与正式搜索能力对齐——正式搜索能出结果的关键词，建议也能出。
     */
    suspend fun searchForSuggestions(keyword: String, limit: Int = 5): List<SubjectEntity> {
        return try {
            val chain = remoteDataSource as? DataSourceChain
            if (chain != null) {
                chain.searchSuggestions(keyword, limit)
            } else {
                remoteDataSource.search(keyword = keyword, type = null, limit = limit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchForSuggestions failed: keyword=$keyword", e)
            emptyList()
        }
    }

    /** 人物搜索（声优/导演等），返回含代表作品的人物列表。 */
    suspend fun searchPersons(keyword: String): List<PersonDetailInfo> {
        return try {
            remoteDataSource.searchPersons(keyword)
        } catch (e: Exception) {
            Log.e(TAG, "searchPersons failed: keyword=$keyword", e)
            emptyList()
        }
    }

    /** 获取类型排名榜（GET，绕开 POST body 被吞）。30s 内存缓存加速重复浏览（趋势区 + 触底分页会连打两次）。 */
    private val rankingCache = mutableMapOf<String, Pair<List<SubjectEntity>, Long>>()

    /**
     * 获取类型排名榜。
     *
     * @param fingerprint 查询指纹（nsfw + 筛选 + sort，第 6 轮 R4）。改造前缓存 key 只有
     *        「type-offset」，切筛选/切 R18 会直接命中旧榜单。
     * @throws Exception 远程失败时抛出（第 6 轮 R1）。改造前 catch → emptyList()，
     *         上层拿到的是「空榜单」而不是「失败」，于是「刷新后一片空白」且没有任何提示。
     */
    suspend fun getRanking(
        type: Int,
        offset: Int = 0,
        limit: Int = 20,
        forceRefresh: Boolean = false,
        fingerprint: String = "",
    ): List<SubjectEntity> {
        val key = rankingCacheKey(type, offset, fingerprint)
        if (!forceRefresh) {
            val cached = rankingCache[key]
            if (cached != null && System.currentTimeMillis() - cached.second < RANKING_CACHE_TTL_MS) {
                return cached.first
            }
        }
        return try {
            // 落库由 DataSourceChain 内部批量完成（单事务），此处不再重复写入
            val remote = remoteDataSource.getRankingByType(type, offset, limit)
            val now = System.currentTimeMillis()
            rankingCache[key] = remote to now
            remote
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "getRanking failed: type=$type", e)
            // R1：不再静默吞异常，交给调用方决定 UI 文案（doRefreshTrending → state.error）
            throw e
        }
    }

    /**
     * 清除排名缓存（下拉刷新时调用，强制重新拉取）。
     *
     * 改造前这个方法**全仓没有任何调用方**：30s 缓存因此无法被用户操作击穿，
     * 下拉刷新后 30 秒内拿到的仍是旧榜。
     */
    fun clearRankingCache() {
        rankingCache.clear()
    }
}

/**
 * 一次搜索的完整结果（第 6 轮 F5）。
 *
 * - remoteFailed = false：正常返回（results 为空 = 确实没有结果）
 * - remoteFailed = true 且 offline = true：远程不可用，展示的是本地离线兜底结果
 * - remoteFailed = true 且 offline = false：网络失败（UI 必须显示「网络失败 + 重试」）
 */
data class SearchOutcome(
    val results: List<SubjectEntity>,
    val total: Int,
    val remoteFailed: Boolean = false,
    val offline: Boolean = false,
    val failureReason: String? = null,
)

/** 网络异常 → 用户可见文案（纯函数：repository / chain / viewmodel 三处共用同一套措辞）。 */
internal fun networkFailureMessage(error: Throwable): String = when (error) {
    is SocketTimeoutException -> "与信息源断开连接"
    is UnknownHostException -> "与网络断开连接"
    is ConnectException -> "与信息源断开连接"
    else -> "网络请求失败，请重试"
}

/** 榜单缓存 key（第 6 轮 R4）：type + offset + 查询指纹（nsfw/筛选/sort）。 */
internal fun rankingCacheKey(type: Int, offset: Int, fingerprint: String): String =
    if (fingerprint.isBlank()) "$type-$offset" else "$type-$offset-$fingerprint"
