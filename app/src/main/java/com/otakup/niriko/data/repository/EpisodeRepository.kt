package com.otakup.niriko.data.repository

import android.util.Log
import com.otakup.niriko.data.local.dao.EpisodeDao
import com.otakup.niriko.data.local.entity.EpisodeEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import com.otakup.niriko.data.refresh.FreshnessDecider
import com.otakup.niriko.data.refresh.RefreshDecision
import com.otakup.niriko.data.refresh.RefreshResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EpisodeRepo"

/**
 * 剧集/章节仓储：缓存优先，**真的会过期**后再拉 `/v0/episodes`，落库后返回 [EpisodeInfo]。
 * 统计页「已播到第几集」与每集热力图共用该数据。
 *
 * ## 改造说明
 *
 * 改造前的判断是 `if (cached.isNotEmpty() && !forceRefresh)` —— 注释写着「缺/过期再拉」，
 * 但**没有任何过期逻辑**：剧集一旦落库就永远不再更新，新播出的集数不会出现，
 * 而 `forceRefresh` 参数在整个仓库里没有任何调用方传过 `true`。
 *
 * 现在按 [RefreshResource.EPISODES] 的 TTL（6 小时）判定：
 * 命中软 TTL 直接读缓存，否则重新拉取（本仓储的唯一调用方是统计页的后台预取，
 * 已经限了并发与超时，因此「可复用但建议重建」这一档直接按重建处理）。
 *
 * 逐行时间戳 `EpisodeEntity.lastSyncTime` 就是新鲜度锚点——不需要额外的 key→时间 映射表。
 */
class EpisodeRepository(
    private val episodeDao: EpisodeDao,
    private val remoteDataSource: SubjectRemoteDataSource,
    /** 可注入时钟（单测用）。 */
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun getEpisodes(subjectId: Long, forceRefresh: Boolean = false): List<EpisodeInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cached = episodeDao.getBySubject(subjectId)
                if (cached.isNotEmpty() && !forceRefresh) {
                    val lastSync = cached.maxOfOrNull { it.lastSyncTime } ?: 0L
                    val decision = FreshnessDecider.decideByTimestamp(
                        policy = RefreshResource.EPISODES,
                        lastSuccessAt = lastSync,
                        now = clock(),
                    )
                    if (decision == RefreshDecision.FRESH) {
                        return@runCatching cached.map { it.toEpisodeInfo() }
                    }
                }
                fetchAndStore(subjectId)
            }.getOrElse { e ->
                Log.w(TAG, "getEpisodes failed subjectId=$subjectId", e)
                emptyList()
            }
        }

    /** 后台预取（与 [getEpisodes] 同一策略）。 */
    suspend fun prefetch(subjectId: Long): List<EpisodeInfo> = getEpisodes(subjectId)

    /**
     * 清理长期未刷新的剧集缓存（[EpisodeDao.deleteOlderThan] 此前是死代码）。
     * 只在实际拉取时顺带执行，不额外增加数据库负载。
     */
    suspend fun pruneStale(cutoffMs: Long = STALE_AFTER_MS) {
        runCatching { episodeDao.deleteOlderThan(clock() - cutoffMs) }
            .onFailure { Log.w(TAG, "pruneStale failed", it) }
    }

    private suspend fun fetchAndStore(subjectId: Long): List<EpisodeInfo> {
        val info = remoteDataSource.getEpisodes(subjectId)
        val now = clock()
        // 剧照来自 TMDb（另一条链路）。Bangumi 刷新会 delete + insert 整表，
        // 因此先把已有剧照取出来回填，避免每次刷新都把剧照清空。
        val existingStills = runCatching {
            episodeDao.getBySubject(subjectId).associate { it.epId to it.stillUrl }
        }.getOrDefault(emptyMap())
        val entities = info.map { it.toEpisodeEntity(subjectId, now, existingStills[it.id]) }
        episodeDao.deleteBySubject(subjectId)
        // 空结果说明该条目没有剧集数据 —— 不能写空，否则每次进来都会重新请求。
        // 修复 BUG-3：原来两个 `entities.isNotEmpty()` 判断重复，且 pruneStale 也被同一个
        // 条件挡住，导致清理逻辑几乎不执行、episodes 表随时间无限增长。
        if (entities.isNotEmpty()) {
            episodeDao.insertAll(entities)
            pruneStale()
        }
        return entities.map { it.toEpisodeInfo() }
    }

    private fun EpisodeInfo.toEpisodeEntity(subjectId: Long, now: Long, stillUrl: String?): EpisodeEntity =
        EpisodeEntity(
            epId = id,
            subjectId = subjectId,
            sort = sort,
            ep = ep,
            name = name,
            nameCn = nameCn,
            // 修复：改造前 desc / disc 被硬编码成 null / 0，导致单集简介缺失、音乐碟片分组恒为单碟
            desc = desc,
            airdate = airdate,
            duration = duration,
            durationSeconds = durationSeconds,
            type = type,
            disc = disc,
            status = status,
            comment = comment,
            stillUrl = stillUrl,
            lastSyncTime = now,
        )

    private fun EpisodeEntity.toEpisodeInfo(): EpisodeInfo =
        EpisodeInfo(
            id = epId,
            name = name,
            nameCn = nameCn,
            desc = desc,
            ep = ep,
            sort = sort,
            airdate = airdate,
            duration = duration,
            status = status,
            comment = comment,
            disc = disc,
            durationSeconds = durationSeconds,
            type = type,
            stillUrl = stillUrl,
        )

    private companion object {
        /** 多久没刷新的剧集行可以被清掉（30 天；远大于 6 小时 TTL）。 */
        const val STALE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
