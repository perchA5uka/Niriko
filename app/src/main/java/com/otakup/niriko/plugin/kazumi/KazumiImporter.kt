package com.otakup.niriko.plugin.kazumi

import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导入结果汇总。 */
data class KazumiImportResult(
    val total: Int = 0,
    val imported: Int = 0,
    val skippedNonAnime: Int = 0,
    val skippedExisting: Int = 0,
    val failed: Int = 0,
    val statusDistribution: Map<WatchStatus, Int> = emptyMap(),
)

/**
 * Kazumi → Niriko 收藏导入器。
 *
 * - 仅导入 ANIME（KazumiCollectEntry.type == 2），其余计入 skippedNonAnime；
 * - SubjectEntity 幂等写入（sourceId="kazumi"），CollectionEntity 默认跳过已存在，
 *   覆写模式（overwrite）下 REPLACE；
 * - 批量写入（subjectDao.upsertAll / collectionDao.insertAll），全程 Dispatchers.IO。
 */
class KazumiImporter(
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao,
) {
    /** Kazumi CollectType → Niriko WatchStatus。 */
    private val statusMap = mapOf(
        1 to WatchStatus.WATCHING,    // 在看
        2 to WatchStatus.PLAN_TO_WATCH, // 想看
        3 to WatchStatus.ON_HOLD,     // 搁置
        4 to WatchStatus.COMPLETED,   // 看过
        5 to WatchStatus.DROPPED,     // 抛弃
    )

    /** 导入 [entries]（已由 KazumiHiveReader 解析），返回汇总。 */
    suspend fun import(
        entries: List<KazumiCollectEntry>,
        overwrite: Boolean = false,
    ): KazumiImportResult = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext KazumiImportResult()

        val animeEntries = entries.filter { it.type == 2 } // kazumi type 2 = ANIME
        val skippedNonAnime = entries.size - animeEntries.size

        // 1) subjects：幂等 upsert（update-first；已存在则保留原数据，仅补 sourceId 标记）
        val subjects = animeEntries.map { it.toSubjectEntity() }
        subjectDao.upsertAll(subjects)

        // 2) collections：默认跳过已存在，overwrite 时 REPLACE
        var imported = 0
        var skippedExisting = 0
        var failed = 0
        val statusCounts = mutableMapOf<WatchStatus, Int>()
        for (entry in animeEntries) {
            try {
                val existing = collectionDao.getBySubjectId(entry.bangumiId)
                if (existing != null && !overwrite) {
                    skippedExisting++
                    continue
                }
                val status = statusMap[entry.collectType] ?: WatchStatus.PLAN_TO_WATCH
                statusCounts[status] = statusCounts[status]?.plus(1) ?: 1
                collectionDao.insertAll(
                    listOf(
                        CollectionEntity(
                            subjectId = entry.bangumiId,
                            status = status,
                            watchedEpisodes = null,
                            rating = null,
                            startDate = null,
                            finishDate = null,
                            personalTags = emptyList(),
                            personalImpression = null,
                            remark = null,
                            watchedTrackIds = emptyList(),
                            createTime = entry.collectTimeMillis,
                            updateTime = entry.collectTimeMillis,
                        ),
                    ),
                )
                imported++
            } catch (_: Exception) {
                failed++
            }
        }

        KazumiImportResult(
            total = animeEntries.size,
            imported = imported,
            skippedNonAnime = skippedNonAnime,
            skippedExisting = skippedExisting,
            failed = failed,
            statusDistribution = statusCounts,
        )
    }

    /** KazumiCollectEntry → SubjectEntity（仅 ANIME，封面 fallback 链）。 */
    private fun KazumiCollectEntry.toSubjectEntity(): SubjectEntity {
        val cover = listOfNotNull(
            images["large"],
            images["medium"],
            images["small"],
            images["grid"],
        ).firstOrNull { it.isNotBlank() }
        return SubjectEntity(
            subjectId = bangumiId,
            title = title.ifBlank { nameCn.ifBlank { "未知作品" } },
            titleCN = nameCn.ifBlank { title },
            type = SubjectType.ANIME,
            summary = summary.takeIf { it.isNotBlank() },
            coverUrl = cover,
            totalEpisodes = null,
            platform = null,
            volumes = null,
            airDate = airDate.takeIf { it.isNotBlank() },
            airWeekday = airWeekday.takeIf { it > 0 },
            ratingScore = ratingScore.takeIf { it > 0 }?.toFloat(),
            ratingTotal = votes.takeIf { it > 0 },
            rank = rank.takeIf { it > 0 },
            series = null,
            tags = tags,
            lastSyncTime = System.currentTimeMillis(),
            sourceId = "kazumi",
        )
    }
}