package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SteamDao
import com.otakup.niriko.data.local.dao.SteamLibraryItemDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.SteamBindingEntity
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.local.entity.SteamLibraryItemEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Steam 导入结果汇总。 */
data class SteamImportResult(
    val total: Int = 0,
    val matched: Int = 0,
    val imported: Int = 0,
    val placeholderCreated: Int = 0,
    val skippedExisting: Int = 0,
    val skippedNotMatched: Int = 0,
    val failed: Int = 0,
)

/**
 * Steam 游戏库 → Niriko 收藏导入器。
 *
 * - 原始快照整体写 `steam_library_items`（imported=false），与收藏主表解耦；
 * - 仅处理勾选项（[SteamLibraryPreview.selected]），三种情形：
 *   1. **匹配到 Bangumi**（bgmSubjectId > 0）：subjects 缺则占位（sourceId="bangumi"，
 *      详情页打开时由 DataSourceChain 用真实元数据刷新）；collections 缺则新建，已存在跳过；
 *   2. **占位候选**（isPlaceholder）：创建负数占位 SubjectEntity（subjectId=-appId，
 *      sourceId="steam"，标题/封面/简介来自 Steam），写 steam_bindings(-appId ↔ appId)
 *      与 steam_games(subjectId=-appId)，再建收藏——使其像普通作品一样展示；
 *   3. **未匹配且非占位** → skippedNotMatched。
 * - 状态按游玩时长推断：playtime>0 → WATCHING（游玩中），否则 PLAN_TO_WATCH；
 *   游玩时长（分钟）写入 watchedEpisodes（游戏时长字段，UI 已支持）。
 * - 成功项标记 imported=true；全程 Dispatchers.IO。
 */
class SteamLibraryImporter(
    private val steamLibraryItemDao: SteamLibraryItemDao,
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao,
    private val steamDao: SteamDao? = null,
) {

    /** 导入 [previews]（勾选态由调用方在预览上维护），返回汇总。 */
    suspend fun import(
        previews: List<SteamLibraryPreview>,
        steamId64: String,
    ): SteamImportResult = withContext(Dispatchers.IO) {
        if (previews.isEmpty()) return@withContext SteamImportResult()

        // 1) 原始快照整体落库（含未勾选项，供会话内预览与后续对照）
        steamLibraryItemDao.upsertAll(previews.map { it.toEntity(steamId64) })

        val selected = previews.filter { it.selected }
        var imported = 0
        var placeholderCreated = 0
        var skippedExisting = 0
        var skippedNotMatched = 0
        var failed = 0

        for (preview in selected) {
            try {
                when {
                    // 情形 1：匹配到 Bangumi 正式词条
                    (preview.bgmSubjectId ?: 0) > 0 -> {
                        val subjectId = preview.bgmSubjectId!!
                        if (subjectDao.getById(subjectId) == null) {
                            subjectDao.upsert(preview.toPlaceholderSubject(subjectId))
                        }
                        val existing = collectionDao.getBySubjectId(subjectId)
                        if (existing == null) {
                            collectionDao.insertAll(listOf(preview.toNewCollection(subjectId)))
                            imported++
                        } else {
                            skippedExisting++
                        }
                    }

                    // 情形 2：占位候选（Bangumi 无词条）→ 创建负数占位条目
                    preview.isPlaceholder -> {
                        val subjectId = preview.placeholderSubjectId
                        if (subjectDao.getById(subjectId) == null) {
                            subjectDao.upsert(preview.toPlaceholderSubject(subjectId))
                        }
                        // 占位条目绑定到自身（steam_bindings/steam_games 以负数 subjectId 关联）
                        steamDao?.upsertBinding(
                            SteamBindingEntity(
                                subjectId = subjectId,
                                steamAppId = preview.appId,
                                matchMethod = "PLACEHOLDER",
                                confidence = 1f,
                                createTime = System.currentTimeMillis(),
                            )
                        )
                        steamDao?.upsertGame(
                            SteamGameEntity(
                                subjectId = subjectId,
                                appId = preview.appId,
                                name = preview.name,
                                headerImage = preview.coverUrl,
                                lastUpdated = 0L,
                            )
                        )
                        val existing = collectionDao.getBySubjectId(subjectId)
                        if (existing == null) {
                            collectionDao.insertAll(listOf(preview.toNewCollection(subjectId)))
                            placeholderCreated++
                            imported++
                        } else {
                            skippedExisting++
                        }
                    }

                    // 情形 3：未匹配且非占位（理论上不发生，防御）
                    else -> skippedNotMatched++
                }
                steamLibraryItemDao.setImported(preview.appId, true)
            } catch (_: Exception) {
                failed++
            }
        }

        SteamImportResult(
            total = selected.size,
            matched = selected.count { it.isMatched },
            imported = imported,
            placeholderCreated = placeholderCreated,
            skippedExisting = skippedExisting,
            skippedNotMatched = skippedNotMatched,
            failed = failed,
        )
    }

    /** preview → 占位 SubjectEntity（sourceId 按匹配结果区分）。 */
    private fun SteamLibraryPreview.toPlaceholderSubject(subjectId: Long): SubjectEntity =
        SubjectEntity(
            subjectId = subjectId,
            title = name,
            titleCN = name,
            type = SubjectType.GAME,
            summary = null,
            coverUrl = coverUrl,
            totalEpisodes = null,
            platform = "Steam",
            volumes = null,
            airDate = null,
            airWeekday = null,
            ratingScore = null,
            ratingTotal = null,
            rank = null,
            biliScore = null,
            biliRatingTotal = null,
            biliSeasonId = null,
            series = null,
            tags = emptyList(),
            lastSyncTime = System.currentTimeMillis(),
            // 正式匹配条目用 "steam"（详情页由 Steam 数据补充）；占位条目也标 "steam"
            sourceId = "steam",
        )

    /** preview → 新 CollectionEntity（状态按游玩时长推断，时长写入 watchedEpisodes）。 */
    private fun SteamLibraryPreview.toNewCollection(subjectId: Long): CollectionEntity =
        CollectionEntity(
            subjectId = subjectId,
            status = inferStatus(playtimeForeverMinutes),
            watchedEpisodes = playtimeForeverMinutes.takeIf { it > 0 },
            rating = null,
            startDate = null,
            finishDate = null,
            personalTags = emptyList(),
            personalImpression = null,
            remark = null,
            watchedTrackIds = emptyList(),
            createTime = System.currentTimeMillis(),
            updateTime = System.currentTimeMillis(),
        )

    /** preview → 快照实体。 */
    private fun SteamLibraryPreview.toEntity(steamId64: String): SteamLibraryItemEntity =
        SteamLibraryItemEntity(
            appId = appId,
            steamId64 = steamId64,
            name = name,
            coverUrl = coverUrl,
            playtimeForeverMinutes = playtimeForeverMinutes,
            playtime2WeeksMinutes = playtime2WeeksMinutes,
            bgmSubjectId = bgmSubjectId,
            isPlaceholder = isPlaceholder,
            imported = false,
            importTime = System.currentTimeMillis(),
        )

    companion object {
        /**
         * 游玩时长 → 收藏状态推断。
         * playtime>0 → WATCHING（游玩中）；否则 PLAN_TO_WATCH。
         */
        fun inferStatus(playtimeMinutes: Int): WatchStatus =
            if (playtimeMinutes > 0) WatchStatus.WATCHING else WatchStatus.PLAN_TO_WATCH
    }
}
