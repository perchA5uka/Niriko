package com.otakup.niriko.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.local.WorkItem
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.EpisodeMyRatingEntity
import com.otakup.niriko.data.local.entity.ManualAwardEntity
import com.otakup.niriko.data.local.entity.SearchHistoryEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.settings.CoverOverrideStore
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.WorkType
import com.otakup.niriko.util.AsyncSerialQueue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

private const val TAG = "SyncManager"
private const val SYNC_VERSION = 3

/**
 * 同步结果。 */
data class SyncResult(
    val success: Boolean,
    val message: String = "",
    val uploaded: Boolean = false,
    val downloaded: Boolean = false,
    val collectionsMerged: Int = 0,
    val workItemsMerged: Int = 0,
)

/**
 * WebDAV 同步引擎。
 * 上传本地数据到 WebDAV，下载远程数据并与本地 LWW 合并。
 *
 * 同步数据格式：JSON，包含收藏(collections)、手工作品(workItems)、搜索历史(searchHistory)，
 * 以及 v3 起纳入的外部身份绑定(externalIds)、我的每集评分(episodeMyRatings)、
 * 手动权威成绩(manualAwards)、封面覆盖(coverOverrides)（计划 B4 · 4-8）。
 * 不包含 subjects 表（太大且可重新拉取）。
 */
class SyncManager(
    private val database: NirikoDatabase,
    private val webDavClient: WebDavClient,
    /** 封面覆盖（DataStore）：v3 起随同步走，计划 B4 · 4-8。 */
    private val coverOverrideStore: CoverOverrideStore? = null,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 所有远端读写串行化（参考 Kazumi `WebDav._webDavOperationQueue`）。
     *
     * 自动同步接入后，后台的「下载合并 + 上传」可能与设置页的手动同步同时发生；
     * 两者都是「读远端 → 合并 → 写远端」，交错执行会让后一次基于过期快照覆盖前一次的结果。
     */
    private val queue = AsyncSerialQueue()

    /** 当前是否有同步在执行（自动路径可据此跳过）。 */
    val isBusy: Boolean get() = queue.isBusy

    companion object {
        const val REMOTE_DIR = "/niriko"
        const val REMOTE_FILE = "$REMOTE_DIR/sync.json"
    }

    // ==================== 上传 ====================

    /** 上传本地数据到 WebDAV。 */
    suspend fun upload(baseUrl: String, user: String, pass: String): SyncResult =
        queue.run { uploadLocked(baseUrl, user, pass) }

    private suspend fun uploadLocked(baseUrl: String, user: String, pass: String): SyncResult {
        try {
            // 确保远程目录存在
            webDavClient.mkcol("$baseUrl$REMOTE_DIR", user, pass)

            // 构建同步 JSON
            // 阶段 B：私密收藏不跟随 WebDAV 同步
            val collections = database.collectionDao().getAll().filter { !it.isPrivate }
            val workItems = database.workDao().getAll()
            val history = database.searchHistoryDao().getAll()

            // 计划 B4 · 4-8：此前只有 collections / workItems / history 三个字段，
            // 外部绑定、我的每集评分、手动成绩、封面覆盖都不同步（换设备就丢）
            val externalIds = database.externalIdDao().getAll()
            val episodeRatings = database.externalRatingDao().getAllMyEpisodeRatings()
            val manualAwards = database.manualAwardDao().getAll()
            val coverOverrides = runCatching { coverOverrideStore?.all() }.getOrNull().orEmpty()

            val syncJson = buildSyncJson(
                collections = collections,
                workItems = workItems,
                history = history,
                externalIds = externalIds,
                episodeRatings = episodeRatings,
                manualAwards = manualAwards,
                coverOverrides = coverOverrides,
            )
            val content = json.encodeToString(JsonObject.serializer(), syncJson)

            val ok = webDavClient.put("$baseUrl$REMOTE_FILE", content, user, pass)
            if (ok) {
                Log.i(TAG, "Upload success: ${collections.size} collections, ${workItems.size} workItems")
                return SyncResult(success = true, uploaded = true, message = "上传成功")
            }
            return SyncResult(success = false, message = "上传失败")
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            return SyncResult(success = false, message = "上传失败: ${e.message}")
        }
    }

    // ==================== 下载与合并 ====================

    /** 下载远程数据并与本地 LWW 合并。 */
    suspend fun download(baseUrl: String, user: String, pass: String): SyncResult =
        queue.run { downloadLocked(baseUrl, user, pass) }

    private suspend fun downloadLocked(baseUrl: String, user: String, pass: String): SyncResult {
        try {
            val remoteJson = webDavClient.get("$baseUrl$REMOTE_FILE", user, pass)
                ?: return SyncResult(success = true, message = "远程无同步数据")

            val remoteRoot = json.parseToJsonElement(remoteJson).jsonObject

            // 解析远程数据
            val remoteCollections = parseCollections(remoteRoot["collections"])
            val remoteWorkItems = parseWorkItems(remoteRoot["workItems"])
            val remoteHistory = parseSearchHistory(remoteRoot["searchHistory"])
            // v3（计划 B4 · 4-8）：老版本的远端文件没有这些字段 → 解析为空列表，行为与之前一致
            val remoteExternalIds = parseExternalIds(remoteRoot["externalIds"])
            val remoteEpisodeRatings = parseEpisodeMyRatings(remoteRoot["episodeMyRatings"])
            val remoteManualAwards = parseManualAwards(remoteRoot["manualAwards"])
            val remoteCoverOverrides = parseCoverOverrides(remoteRoot["coverOverrides"])

            // 获取本地数据
            val localCollections = database.collectionDao().getAll()
            val localWorkItems = database.workDao().getAll()
            val localHistory = database.searchHistoryDao().getAll()
            val localExternalIds = database.externalIdDao().getAll()
            val localEpisodeRatings = database.externalRatingDao().getAllMyEpisodeRatings()
            val localManualAwards = database.manualAwardDao().getAll()
            val localCoverOverrides = runCatching { coverOverrideStore?.all() }.getOrNull().orEmpty()

            // LWW 合并
            val mergedCollections = mergeCollections(localCollections, remoteCollections)
            val mergedWorkItems = mergeWorkItems(localWorkItems, remoteWorkItems)
            val mergedHistory = mergeHistory(localHistory, remoteHistory)
            // 有修改时间的走 LWW；封面覆盖没有时间戳 → 本地优先（见 SyncMergePolicy）
            val mergedExternalIds = SyncMergePolicy.lastWriteWins(
                localExternalIds, remoteExternalIds, { it.subjectId to it.provider }, { it.boundAt },
            )
            val mergedEpisodeRatings = SyncMergePolicy.lastWriteWins(
                localEpisodeRatings, remoteEpisodeRatings, { it.epId }, { it.ratedAt },
            )
            val mergedManualAwards = SyncMergePolicy.lastWriteWins(
                localManualAwards, remoteManualAwards, { it.id }, { it.createTime },
            )
            val mergedCoverOverrides = SyncMergePolicy.localFirst(localCoverOverrides, remoteCoverOverrides)

            // 写入数据库（整体事务：失败自动回滚，不出现半清空状态）
            database.withTransaction {
                // FK 完整性：同步数据不含 subjects 表，先为缺失的引用插入占位条目
                val referencedIds = (
                    mergedCollections.map { it.subjectId } +
                        mergedExternalIds.map { it.subjectId } +
                        mergedEpisodeRatings.map { it.subjectId } +
                        mergedManualAwards.map { it.subjectId }
                    ).distinct()
                if (referencedIds.isNotEmpty()) {
                    val existing = database.subjectDao().getExistingIds(referencedIds).toSet()
                    val missing = referencedIds.filter { it !in existing }
                    if (missing.isNotEmpty()) {
                        database.subjectDao().insertAll(
                            missing.map {
                                SubjectEntity(
                                    subjectId = it,
                                    title = "未知作品",
                                    type = SubjectType.OTHER,
                                )
                            }
                        )
                    }
                }

                database.collectionDao().clearAll()
                if (mergedCollections.isNotEmpty()) {
                    database.collectionDao().insertAll(mergedCollections)
                }
                database.workDao().clearAll()
                if (mergedWorkItems.isNotEmpty()) {
                    database.workDao().insertAll(mergedWorkItems)
                }
                database.searchHistoryDao().clearAll()
                if (mergedHistory.isNotEmpty()) {
                    database.searchHistoryDao().insertAll(mergedHistory)
                }
                // v3（计划 B4 · 4-8）
                database.externalIdDao().clearAll()
                if (mergedExternalIds.isNotEmpty()) {
                    database.externalIdDao().insertAll(mergedExternalIds)
                }
                database.externalRatingDao().clearAllMyEpisodeRatings()
                if (mergedEpisodeRatings.isNotEmpty()) {
                    database.externalRatingDao().insertAllMyEpisodeRatings(mergedEpisodeRatings)
                }
                database.manualAwardDao().clearAll()
                if (mergedManualAwards.isNotEmpty()) {
                    database.manualAwardDao().insertAll(mergedManualAwards)
                }
            }

            // 封面覆盖在 DataStore（不在 Room 事务里）：合并结果整体写回
            // 用局部变量接一下：属性在 lambda 里拿不到智能转换
            val coverStore = coverOverrideStore
            if (coverStore != null && mergedCoverOverrides != localCoverOverrides) {
                runCatching { coverStore.putAll(mergedCoverOverrides, clearFirst = true) }
            }

            Log.i(TAG, "Download & merge success: " +
                    "collections=${mergedCollections.size}, workItems=${mergedWorkItems.size}, " +
                    "externalIds=${mergedExternalIds.size}, episodeRatings=${mergedEpisodeRatings.size}, " +
                    "manualAwards=${mergedManualAwards.size}, covers=${mergedCoverOverrides.size}")

            return SyncResult(
                success = true,
                downloaded = true,
                collectionsMerged = mergedCollections.size,
                workItemsMerged = mergedWorkItems.size,
                message = "同步完成",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            return SyncResult(success = false, message = "下载失败: ${e.message}")
        }
    }

    // ==================== 合并逻辑 ====================

    /** 收藏合并：按 subjectId 去重，保留 updateTime 较新的。 */
    private fun mergeCollections(
        local: List<CollectionEntity>,
        remote: List<CollectionEntity>,
    ): List<CollectionEntity> {
        val map = mutableMapOf<Long, CollectionEntity>() // subjectId → entity
        local.forEach { map[it.subjectId] = it }
        remote.forEach { r ->
            val existing = map[r.subjectId]
            if (existing == null || r.updateTime > existing.updateTime) {
                map[r.subjectId] = r
            }
        }
        return map.values.toList()
    }

    /** 手工作品合并：按 id 去重，保留 updateTime 较新的。 */
    private fun mergeWorkItems(
        local: List<WorkItem>,
        remote: List<WorkItem>,
    ): List<WorkItem> {
        val map = mutableMapOf<Long, WorkItem>() // id → entity
        local.forEach { map[it.id] = it }
        remote.forEach { r ->
            val existing = map[r.id]
            if (existing == null || r.updateTime > existing.updateTime) {
                map[r.id] = r
            }
        }
        return map.values.toList()
    }

    /** 搜索历史合并：简单做并集去重（按 keyword）。 */
    private fun mergeHistory(
        local: List<SearchHistoryEntity>,
        remote: List<SearchHistoryEntity>,
    ): List<SearchHistoryEntity> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<SearchHistoryEntity>()
        (local + remote).sortedByDescending { it.createTime }.forEach {
            if (it.keyword !in seen) {
                seen.add(it.keyword)
                result.add(it)
            }
        }
        return result
    }

    // ==================== JSON 构建/解析 ====================

    private fun buildSyncJson(
        collections: List<CollectionEntity>,
        workItems: List<WorkItem>,
        history: List<SearchHistoryEntity>,
        externalIds: List<SubjectExternalIdEntity> = emptyList(),
        episodeRatings: List<EpisodeMyRatingEntity> = emptyList(),
        manualAwards: List<ManualAwardEntity> = emptyList(),
        coverOverrides: Map<Long, String> = emptyMap(),
    ): JsonObject = buildJsonObject {
        put("version", JsonPrimitive(SYNC_VERSION))
        put("syncTime", JsonPrimitive(System.currentTimeMillis()))

        put("collections", buildJsonArray {
            collections.forEach { add(collectionToJson(it)) }
        })
        put("workItems", buildJsonArray {
            workItems.forEach { add(workItemToJson(it)) }
        })
        put("searchHistory", buildJsonArray {
            history.forEach { add(historyToJson(it)) }
        })
        // v3（计划 B4 · 4-8）
        put("externalIds", buildJsonArray {
            externalIds.forEach { add(externalIdToJson(it)) }
        })
        put("episodeMyRatings", buildJsonArray {
            episodeRatings.forEach { add(episodeMyRatingToJson(it)) }
        })
        put("manualAwards", buildJsonArray {
            manualAwards.forEach { add(manualAwardToJson(it)) }
        })
        put("coverOverrides", buildJsonObject {
            coverOverrides.forEach { (subjectId, uri) -> put(subjectId.toString(), JsonPrimitive(uri)) }
        })
    }

    // ===== v3 新增序列化（计划 B4 · 4-8）=====

    private fun externalIdToJson(e: SubjectExternalIdEntity): JsonObject = buildJsonObject {
        put("subjectId", JsonPrimitive(e.subjectId))
        put("provider", JsonPrimitive(e.provider))
        put("externalId", JsonPrimitive(e.externalId))
        e.titleSnapshot?.let { put("titleSnapshot", JsonPrimitive(it)) }
        put("confidence", JsonPrimitive(e.confidence.toDouble()))
        put("bindMethod", JsonPrimitive(e.bindMethod))
        e.subKey?.let { put("subKey", JsonPrimitive(it)) }
        put("boundAt", JsonPrimitive(e.boundAt))
    }

    private fun episodeMyRatingToJson(r: EpisodeMyRatingEntity): JsonObject = buildJsonObject {
        put("epId", JsonPrimitive(r.epId))
        put("subjectId", JsonPrimitive(r.subjectId))
        put("score", JsonPrimitive(r.score.toDouble()))
        r.comment?.let { put("comment", JsonPrimitive(it)) }
        put("rewatch", JsonPrimitive(r.rewatch))
        put("ratedAt", JsonPrimitive(r.ratedAt))
    }

    private fun manualAwardToJson(a: ManualAwardEntity): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(a.id))
        put("subjectId", JsonPrimitive(a.subjectId))
        put("sourceId", JsonPrimitive(a.sourceId))
        a.score?.let { put("score", JsonPrimitive(it.toDouble())) }
        put("scoreMax", JsonPrimitive(a.scoreMax.toDouble()))
        a.rankPosition?.let { put("rankPosition", JsonPrimitive(it)) }
        a.note?.let { put("note", JsonPrimitive(it)) }
        a.url?.let { put("url", JsonPrimitive(it)) }
        put("createTime", JsonPrimitive(a.createTime))
    }

    private fun collectionToJson(c: CollectionEntity): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(c.id))
        put("subjectId", JsonPrimitive(c.subjectId))
        put("status", JsonPrimitive(c.status.name))
        c.watchedEpisodes?.let { put("watchedEpisodes", JsonPrimitive(it)) }
        c.rating?.let { put("rating", JsonPrimitive(it.toDouble())) }
        c.startDate?.let { put("startDate", JsonPrimitive(it.toString())) }
        c.finishDate?.let { put("finishDate", JsonPrimitive(it.toString())) }
        if (c.personalTags.isNotEmpty()) {
            put("personalTags", buildJsonArray { c.personalTags.forEach { add(JsonPrimitive(it)) } })
        }
        c.personalImpression?.let { put("personalImpression", JsonPrimitive(it)) }
        c.remark?.let { put("remark", JsonPrimitive(it)) }
        if (c.watchedTrackIds.isNotEmpty()) {
            put("watchedTrackIds", buildJsonArray { c.watchedTrackIds.forEach { add(JsonPrimitive(it)) } })
        }
        put("createTime", JsonPrimitive(c.createTime))
        put("updateTime", JsonPrimitive(c.updateTime))
    }

    private fun workItemToJson(w: WorkItem): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(w.id))
        put("title", JsonPrimitive(w.title))
        put("type", JsonPrimitive(w.type.name))
        put("status", JsonPrimitive(w.status.name))
        w.totalEpisodes?.let { put("totalEpisodes", JsonPrimitive(it)) }
        w.watchedEpisodes?.let { put("watchedEpisodes", JsonPrimitive(it)) }
        w.rating?.let { put("rating", JsonPrimitive(it.toDouble())) }
        w.startDate?.let { put("startDate", JsonPrimitive(it.toString())) }
        w.finishDate?.let { put("finishDate", JsonPrimitive(it.toString())) }
        if (w.tags.isNotEmpty()) {
            put("tags", buildJsonArray { w.tags.forEach { add(JsonPrimitive(it)) } })
        }
        w.coverPath?.let { put("coverPath", JsonPrimitive(it)) }
        w.remark?.let { put("remark", JsonPrimitive(it)) }
        put("createTime", JsonPrimitive(w.createTime))
        put("updateTime", JsonPrimitive(w.updateTime))
    }

    private fun historyToJson(h: SearchHistoryEntity): JsonObject = buildJsonObject {
        put("keyword", JsonPrimitive(h.keyword))
        put("createTime", JsonPrimitive(h.createTime))
    }

    private fun parseCollections(element: JsonElement?): List<CollectionEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { obj ->
            val o = obj.jsonObject
            try {
                CollectionEntity(
                    id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    subjectId = o["subjectId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null,
                    status = WatchStatus.valueOf(o["status"]?.jsonPrimitive?.content ?: return@mapNotNull null),
                    watchedEpisodes = o["watchedEpisodes"]?.jsonPrimitive?.content?.toIntOrNull(),
                    rating = o["rating"]?.jsonPrimitive?.content?.toFloatOrNull(),
                    startDate = o["startDate"]?.jsonPrimitive?.content?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } },
                    finishDate = o["finishDate"]?.jsonPrimitive?.content?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } },
                    personalTags = parseStringList(o["personalTags"]),
                    personalImpression = o["personalImpression"]?.jsonPrimitive?.content,
                    remark = o["remark"]?.jsonPrimitive?.content,
                    watchedTrackIds = parseLongList(o["watchedTrackIds"]),
                    createTime = o["createTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
                    updateTime = o["updateTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
                )
            } catch (_: Exception) { null }
        }
    }

    private fun parseWorkItems(element: JsonElement?): List<WorkItem> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { obj ->
            val o = obj.jsonObject
            try {
                WorkItem(
                    id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    title = o["title"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    type = WorkType.valueOf(o["type"]?.jsonPrimitive?.content ?: return@mapNotNull null),
                    status = WatchStatus.valueOf(o["status"]?.jsonPrimitive?.content ?: return@mapNotNull null),
                    totalEpisodes = o["totalEpisodes"]?.jsonPrimitive?.content?.toIntOrNull(),
                    watchedEpisodes = o["watchedEpisodes"]?.jsonPrimitive?.content?.toIntOrNull(),
                    rating = o["rating"]?.jsonPrimitive?.content?.toFloatOrNull(),
                    startDate = o["startDate"]?.jsonPrimitive?.content?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } },
                    finishDate = o["finishDate"]?.jsonPrimitive?.content?.let { try { LocalDate.parse(it) } catch (_: Exception) { null } },
                    tags = parseStringList(o["tags"]),
                    coverPath = o["coverPath"]?.jsonPrimitive?.content,
                    remark = o["remark"]?.jsonPrimitive?.content,
                    createTime = o["createTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
                    updateTime = o["updateTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
                )
            } catch (_: Exception) { null }
        }
    }

    private fun parseSearchHistory(element: JsonElement?): List<SearchHistoryEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { obj ->
            val o = obj.jsonObject
            val keyword = o["keyword"]?.jsonPrimitive?.content ?: return@mapNotNull null
            SearchHistoryEntity(
                keyword = keyword,
                createTime = o["createTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
            )
        }
    }

    // ===== v3 新增解析（计划 B4 · 4-8）=====

    private fun parseExternalIds(element: JsonElement?): List<SubjectExternalIdEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { obj ->
            val o = try { obj.jsonObject } catch (_: Exception) { return@mapNotNull null }
            val subjectId = o["subjectId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val provider = o["provider"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val externalId = o["externalId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            SubjectExternalIdEntity(
                subjectId = subjectId,
                provider = provider,
                externalId = externalId,
                titleSnapshot = o["titleSnapshot"]?.jsonPrimitive?.content,
                confidence = o["confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                bindMethod = o["bindMethod"]?.jsonPrimitive?.content ?: SubjectExternalIdEntity.METHOD_MANUAL,
                subKey = o["subKey"]?.jsonPrimitive?.content,
                boundAt = o["boundAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        }
    }

    private fun parseEpisodeMyRatings(element: JsonElement?): List<EpisodeMyRatingEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { obj ->
            val o = try { obj.jsonObject } catch (_: Exception) { return@mapNotNull null }
            val epId = o["epId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val subjectId = o["subjectId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            EpisodeMyRatingEntity(
                epId = epId,
                subjectId = subjectId,
                score = o["score"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return@mapNotNull null,
                comment = o["comment"]?.jsonPrimitive?.content,
                rewatch = o["rewatch"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                ratedAt = o["ratedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        }
    }

    private fun parseManualAwards(element: JsonElement?): List<ManualAwardEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { obj ->
            val o = try { obj.jsonObject } catch (_: Exception) { return@mapNotNull null }
            val subjectId = o["subjectId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
            val sourceId = o["sourceId"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ManualAwardEntity(
                id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                subjectId = subjectId,
                sourceId = sourceId,
                score = o["score"]?.jsonPrimitive?.content?.toFloatOrNull(),
                scoreMax = o["scoreMax"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 40f,
                rankPosition = o["rankPosition"]?.jsonPrimitive?.content?.toIntOrNull(),
                note = o["note"]?.jsonPrimitive?.content,
                url = o["url"]?.jsonPrimitive?.content,
                createTime = o["createTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        }
    }

    private fun parseCoverOverrides(element: JsonElement?): Map<Long, String> {
        val obj = element as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (key, value) ->
            val subjectId = key.toLongOrNull() ?: return@mapNotNull null
            val uri = (value as? JsonPrimitive)?.content ?: return@mapNotNull null
            subjectId to uri
        }.toMap()
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    private fun parseLongList(element: JsonElement?): List<Long> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }
    }
}
