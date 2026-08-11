package com.otakup.niriko.plugin.bilibili

import com.otakup.niriko.data.local.dao.BilibiliSyncItemDao
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 导入结果汇总。 */
data class BilibiliImportResult(
    val total: Int = 0,
    val matched: Int = 0,
    val imported: Int = 0,
    val skippedNotMatched: Int = 0,
    val skippedExisting: Int = 0,
    val failed: Int = 0,
)

/**
 * 哔哩哔哩 → Niriko 收藏导入器。
 *
 * - 全部原始快照先写 `bilibili_sync_items`（imported=false），与收藏主表解耦；
 * - 仅处理勾选项（[BilibiliSyncPreview.selected]）：
 *   - 未匹配（bgmSubjectId == null）→ skippedNotMatched；
 *   - subjects 缺条目 → 先写占位 SubjectEntity（sourceId="bilibili"，详情页打开时会被
 *     DataSourceChain 用 Bangumi 真实元数据刷新）；
 *   - collections 缺条目 → 新建（状态固定「想看」+ 评分/短评/进度来自 bili）；
 *   - collections 已有 → 完全跳过（本地优先，不更新状态/评分/短评）→ skippedExisting；
 * - 成功项标记 imported=true；全程 Dispatchers.IO。
 */
class BilibiliImporter(
    private val bilibiliSyncItemDao: BilibiliSyncItemDao,
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao,
) {

    /** 导入 [previews]（勾选态由调用方在预览上维护），返回汇总。 */
    suspend fun import(
        previews: List<BilibiliSyncPreview>,
        overwrite: Boolean = false,
    ): BilibiliImportResult = withContext(Dispatchers.IO) {
        if (previews.isEmpty()) return@withContext BilibiliImportResult()

        // 1) 原始快照整体落库（含未勾选项，供会话内预览与后续对照）
        bilibiliSyncItemDao.upsertAll(previews.map { it.toEntity() })

        val selected = previews.filter { it.selected }
        var imported = 0
        var skippedNotMatched = 0
        var skippedExisting = 0
        var failed = 0

        for (preview in selected) {
            val bgmSubjectId = preview.bgmSubjectId
            if (bgmSubjectId == null) {
                skippedNotMatched++
                continue
            }
            try {
                // 2) subjects：缺条目时占位（元数据后续由详情页拉取刷新）
                if (subjectDao.getById(bgmSubjectId) == null) {
                    subjectDao.upsert(preview.toPlaceholderSubject(bgmSubjectId))
                }

                // 3) collections：本地已有收藏 → 完全跳过（不更新状态/评分/短评，本地优先）；
                //    本地无收藏 → 新建（状态固定为「想看」，评分/短评/进度来自 bili）。
                val existing = collectionDao.getBySubjectId(bgmSubjectId)
                if (existing == null) {
                    collectionDao.insertAll(
                        listOf(
                            preview.toNewCollection(bgmSubjectId),
                        ),
                    )
                    imported++
                } else {
                    skippedExisting++
                }
                bilibiliSyncItemDao.setImported(preview.mediaId, true)
            } catch (_: Exception) {
                failed++
            }
        }

        BilibiliImportResult(
            total = selected.size,
            matched = selected.count { it.isMatched },
            imported = imported,
            skippedNotMatched = skippedNotMatched,
            skippedExisting = skippedExisting,
            failed = failed,
        )
    }

    /** preview → 占位 SubjectEntity（sourceId="bilibili"）。 */
    private fun BilibiliSyncPreview.toPlaceholderSubject(subjectId: Long): SubjectEntity =
        SubjectEntity(
            subjectId = subjectId,
            title = title,
            titleCN = title,
            type = SubjectType.ANIME,
            summary = null,
            coverUrl = cover,
            totalEpisodes = totalEpisodes,
            platform = null,
            volumes = null,
            airDate = null,
            airWeekday = null,
            ratingScore = null,
            ratingTotal = null,
            rank = null,
            biliScore = biliScore,
            biliRatingTotal = null,
            biliSeasonId = seasonId,
            series = null,
            tags = emptyList(),
            lastSyncTime = System.currentTimeMillis(),
            sourceId = "bilibili",
        )

    /** preview → 新 CollectionEntity（状态固定「想看」，本地优先不覆盖已有收藏）。 */
    private fun BilibiliSyncPreview.toNewCollection(subjectId: Long): CollectionEntity =
        CollectionEntity(
            subjectId = subjectId,
            status = WatchStatus.PLAN_TO_WATCH,
            watchedEpisodes = progress,
            rating = biliScore?.takeIf { it > 0f },
            startDate = null,
            finishDate = null,
            personalTags = emptyList(),
            personalImpression = biliComment?.takeIf { it.isNotBlank() },
            remark = null,
            watchedTrackIds = emptyList(),
            createTime = System.currentTimeMillis(),
            updateTime = System.currentTimeMillis(),
        )
}
