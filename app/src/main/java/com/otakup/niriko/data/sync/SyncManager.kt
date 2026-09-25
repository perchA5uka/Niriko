package com.otakup.niriko.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.local.WorkItem
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.SearchHistoryEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
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
private const val SYNC_VERSION = 2

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
 * 同步数据格式：JSON，包含收藏(collections)、手工作品(workItems)、搜索历史(searchHistory)。
 * 不包含 subjects 表（太大且可重新拉取）。
 */
class SyncManager(
    private val database: NirikoDatabase,
    private val webDavClient: WebDavClient,
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

            val syncJson = buildSyncJson(collections, workItems, history)
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

            // 获取本地数据
            val localCollections = database.collectionDao().getAll()
            val localWorkItems = database.workDao().getAll()
            val localHistory = database.searchHistoryDao().getAll()

            // LWW 合并
            val mergedCollections = mergeCollections(localCollections, remoteCollections)
            val mergedWorkItems = mergeWorkItems(localWorkItems, remoteWorkItems)
            val mergedHistory = mergeHistory(localHistory, remoteHistory)

            // 写入数据库（整体事务：失败自动回滚，不出现半清空状态）
            database.withTransaction {
                // FK 完整性：同步数据不含 subjects 表，先为缺失的引用插入占位条目
                val referencedIds = mergedCollections.map { it.subjectId }.distinct()
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
            }

            Log.i(TAG, "Download & merge success: " +
                    "collections=${mergedCollections.size}, workItems=${mergedWorkItems.size}")

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

    private fun parseStringList(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    private fun parseLongList(element: JsonElement?): List<Long> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }
    }
}
