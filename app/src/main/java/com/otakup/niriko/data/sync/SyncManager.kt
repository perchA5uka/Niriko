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

/**
 * 同步文件格式版本。
 *
 * v4（A-D3）：collections 增加 `watchedVolumes`（书籍/漫画卷进度）。
 * 同步没有版本校验，旧版本客户端读新文件会忽略未知键（不会崩）；但旧客户端**回传**
 * 时会把新键抹掉，靠「缺键不覆盖」的合并规则兜底。更新说明要求两台设备都升到 v1.2.1 再同步。
 */
private const val SYNC_VERSION = 4

/**
 * 同步结果。 */
data class SyncResult(
    val success: Boolean,
    val message: String = "",
    val uploaded: Boolean = false,
    val downloaded: Boolean = false,
    val collectionsMerged: Int = 0,
    val workItemsMerged: Int = 0,
    /** 失败种类（A-D5）：UI 按它给人话；成功时为 null。默认值保证旧调用点免改。 */
    val failure: SyncFailureKind? = null,
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
    /** 同步基线（DataStore，A-D4）：远端 ETag / 修改时间，用于条件写入。 */
    private val syncBaselineStore: SyncBaselineStore? = null,
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

    /**
     * 上传本地数据到 WebDAV。
     *
     * @param forceOverwrite 用户显式选择的「覆盖云端」：忽略 `If-Match` 直接 PUT。
     *        只允许由冲突弹窗里的一次点击触发，**绝不自动**。
     */
    suspend fun upload(
        baseUrl: String,
        user: String,
        pass: String,
        forceOverwrite: Boolean = false,
    ): SyncResult = queue.run { uploadLocked(baseUrl, user, pass, forceOverwrite) }

    private suspend fun uploadLocked(
        baseUrl: String,
        user: String,
        pass: String,
        forceOverwrite: Boolean = false,
        allowAutoMerge: Boolean = true,
    ): SyncResult {
        try {
            // 确保远程目录存在。
            // 改造前这里丢弃了 MKCOL 的返回值：目录建不出来（401/403/5xx）也会继续 PUT，
            // 用户最后只看到笼统的「上传失败」，分不清是账号、权限还是服务端问题。
            val ensured = webDavClient.mkcol("$baseUrl$REMOTE_DIR", user, pass)
            if (ensured !is WebDavResult.Ok) {
                val failure = ensured as? WebDavResult.Failure
                val kind = failure?.kind ?: SyncFailureKind.NOT_FOUND
                Log.e(TAG, "MKCOL failed: kind=$kind code=${failure?.code}")
                val message = if (failure != null) {
                    SyncFailureMessages.forMkcol(failure)
                } else {
                    "无法创建远程目录" + SyncFailureMessages.suffix(404)
                }
                return SyncResult(success = false, message = message, failure = kind)
            }

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
            val fileUrl = "$baseUrl$REMOTE_FILE"
            // C-3：上传会覆盖远端，落一份「这次要推上去的数据」当后悔药（失败只记日志）
            com.otakup.niriko.data.snapshot.SnapshotRuntime.preflight(content, "sync-upload")

            // ===== A-D4：条件写入 =====
            // 改造前是裸 PUT：不读远端、不带 If-Match，A 机上传会静默覆盖 B 机写入。
            val baseline = if (forceOverwrite) null else runCatching { syncBaselineStore?.read() }.getOrNull()
            var ifMatch: String? = null
            var weakWarning: String? = null
            if (!forceOverwrite && baseline != null) {
                // HEAD 取远端当前 ETag / Last-Modified / 长度，和基线比
                val head = webDavClient.exists(fileUrl, user, pass)
                when (head) {
                    is WebDavResult.Ok -> {
                        val remoteMeta = head.value
                        when (val decision = SyncConcurrencyPolicy.decide(baseline, remoteMeta, remoteMeta.etag != null)) {
                            is SyncWriteDecision.Proceed -> ifMatch = decision.ifMatch
                            is SyncWriteDecision.WarnThenProceed -> weakWarning = decision.reason
                            is SyncWriteDecision.Overwrite -> Unit
                            is SyncWriteDecision.Conflict -> {
                                if (allowAutoMerge) {
                                    // 自动重合并是**一次性**的：download 会按字段级规则合并并刷新基线，
                                    // 然后 allowAutoMerge=false 重试；写成循环会让两台设备互相刷。
                                    Log.w(
                                        TAG,
                                        "Upload conflict (HEAD): baseline=${decision.baselineEtag} remote=${decision.remoteEtag}",
                                    )
                                    val merged = downloadLocked(baseUrl, user, pass)
                                    if (!merged.success) return merged
                                    return uploadLocked(baseUrl, user, pass, forceOverwrite = false, allowAutoMerge = false)
                                }
                                return conflictResult()
                            }
                        }
                    }
                    is WebDavResult.NotFound -> Unit // 远端文件不存在：没有可冲突的对象
                    is WebDavResult.Failure -> {
                        // 部分服务器禁用 HEAD：基线有 ETag 时仍然发 If-Match，由服务器判定
                        if (baseline.etag != null) ifMatch = baseline.etag else weakWarning = "该服务器不支持并发检测"
                    }
                }
            } else if (forceOverwrite) {
                Log.w(TAG, "Force overwrite: skip If-Match")
            }

            return when (val written = webDavClient.put(fileUrl, content, user, pass, ifMatch)) {
                is WebDavResult.Ok -> {
                    // PUT 响应不总带 ETag：必要时 HEAD 补一次，否则下次只能弱校验
                    val finalMeta = if (written.value.etag == null) {
                        (webDavClient.exists(fileUrl, user, pass) as? WebDavResult.Ok)?.value ?: written.value
                    } else {
                        written.value
                    }
                    writeBaseline(fileUrl, finalMeta, content)
                    Log.i(TAG, "Upload success: ${collections.size} collections, ${workItems.size} workItems")
                    SyncResult(
                        success = true,
                        uploaded = true,
                        message = buildUploadMessage(
                            collections = collections.size,
                            workItems = workItems.size,
                            weakWarning = weakWarning,
                            forceOverwrite = forceOverwrite,
                        ),
                    )
                }
                is WebDavResult.NotFound -> SyncResult(
                    success = false,
                    message = "上传失败：远程路径不存在" + SyncFailureMessages.suffix(404),
                    failure = SyncFailureKind.NOT_FOUND,
                )
                is WebDavResult.Failure -> {
                    if (written.kind == SyncFailureKind.CONFLICT && allowAutoMerge) {
                        // 412：HEAD 之后远端又被改（或服务器拒绝 HEAD、由 If-Match 拦下）
                        Log.w(TAG, "PUT conflict (412); auto merge once")
                        val merged = downloadLocked(baseUrl, user, pass)
                        if (!merged.success) return merged
                        return uploadLocked(baseUrl, user, pass, forceOverwrite = false, allowAutoMerge = false)
                    }
                    Log.e(TAG, "PUT failed: kind=${written.kind} code=${written.code}")
                    SyncResult(
                        success = false,
                        message = if (written.kind == SyncFailureKind.CONFLICT) {
                            conflictResult().message
                        } else {
                            SyncFailureMessages.forFailure(written)
                        },
                        failure = written.kind,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            // 异常走同一套分类，不再把类型信息丢给用户
            val kind = SyncFailureClassifier.classifyException(e)
            return SyncResult(
                success = false,
                message = SyncFailureMessages.forKind(kind),
                failure = kind,
            )
        }
    }

    /** 第二次仍冲突：把选择交给 UI（先合并 / 覆盖云端 / 取消）。 */
    private fun conflictResult(): SyncResult = SyncResult(
        success = false,
        message = "云端在你上次同步后已更新，请选择「先合并」或「覆盖云端」",
        failure = SyncFailureKind.CONFLICT,
    )

    private fun buildUploadMessage(
        collections: Int,
        workItems: Int,
        weakWarning: String?,
        forceOverwrite: Boolean,
    ): String {
        val base = "已上传 ${collections} 条收藏、${workItems} 个手工作品"
        return when {
            forceOverwrite -> "${base}（已忽略并发检查，覆盖云端）"
            weakWarning != null -> "${base}（${weakWarning}）"
            else -> base
        }
    }

    /** 记录基线。失败只记日志：基线丢了最多退回裸 PUT，不能让上传本身失败。 */
    private suspend fun writeBaseline(url: String, meta: RemoteMeta?, payload: String?) {
        val store = syncBaselineStore ?: return
        runCatching {
            store.write(
                SyncBaseline(
                    url = url,
                    etag = meta?.etag,
                    lastModified = meta?.lastModified,
                    length = meta?.length,
                    payloadHash = payload?.let { SyncPayloadHash.sha256Hex(it) },
                    syncTime = System.currentTimeMillis(),
                    deviceId = store.deviceId(),
                ),
            )
        }.onFailure { Log.w(TAG, "Baseline write failed", it) }
    }

    // ==================== 下载与合并 ====================

    /** 下载远程数据并与本地 LWW 合并。 */
    suspend fun download(baseUrl: String, user: String, pass: String): SyncResult =
        queue.run { downloadLocked(baseUrl, user, pass) }

    private suspend fun downloadLocked(baseUrl: String, user: String, pass: String): SyncResult {
        // 先取远端内容：改造前 get 返回 null 时这里直接报「远程无同步数据」并 success=true，
        // 于是 401 / 5xx / 超时全被当成「远端为空」——用户点下载就是「什么都没发生」。
        val fileUrl = "$baseUrl$REMOTE_FILE"
        val fetched = webDavClient.get(fileUrl, user, pass)
        if (fetched !is WebDavResult.Ok) {
            if (fetched is WebDavResult.NotFound) {
                // 远端确实没有文件：基线写成空，下次上传按「新建」处理而不是条件写入
                writeBaseline(fileUrl, null, null)
                return SyncResult(success = true, message = "远程无同步数据")
            }
            val failure = fetched as WebDavResult.Failure
            Log.e(TAG, "Download failed: kind=${failure.kind} code=${failure.code}")
            return SyncResult(
                success = false,
                message = SyncFailureMessages.forFailure(failure),
                failure = failure.kind,
            )
        }
        val remoteContent = fetched.value
        val remoteJson = remoteContent.body

        try {
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

                // F09：分区不参与 WebDAV 同步，但它们的成员关系指向 collections 表 ——
                // 上面刚把 collections 整体重写过，因此这里清掉「已经不在收藏里」的成员，
                // 否则同步完成后分区会显示一个点进去看不到的作品（幽灵成员）。
                // 顺序必须在 collections 写入之后。
                val orphanFolders = database.libraryFolderDao().foldersWithOrphanMembers()
                if (orphanFolders.isNotEmpty()) {
                    database.libraryFolderDao().pruneMembersNotInCollections()
                    orphanFolders.forEach { database.libraryFolderDao().touchFolder(it) }
                }
            }

            // 封面覆盖在 DataStore（不在 Room 事务里）：合并结果整体写回
            // 用局部变量接一下：属性在 lambda 里拿不到智能转换
            val coverStore = coverOverrideStore
            if (coverStore != null && mergedCoverOverrides != localCoverOverrides) {
                runCatching { coverStore.putAll(mergedCoverOverrides, clearFirst = true) }
            }

            // A-D4：下载成功（含合并写库）后记录远端版本标识，供下次上传做条件写入
            writeBaseline(fileUrl, remoteContent.meta, remoteContent.body)

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
            // 远端能取到但内容不是合法同步 JSON：单独归为 PARSE，不要把异常类名甩给用户
            return SyncResult(
                success = false,
                message = SyncFailureMessages.forKind(SyncFailureKind.PARSE),
                failure = SyncFailureKind.PARSE,
            )
        }
    }

    // ==================== 合并逻辑 ====================

    /**
     * 收藏合并：按 subjectId 去重。
     *
     * 不再整行覆盖：先用 updateTime 选基准行，卷进度/集数取 MAX、私密标记取并集，
     * 详见 [SyncMergePolicy.mergeCollections]（纯函数，有单测）。
     */
    private fun mergeCollections(
        local: List<CollectionEntity>,
        remote: List<CollectionEntity>,
    ): List<CollectionEntity> = SyncMergePolicy.mergeCollections(local, remote)

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

    /** 收藏序列化（A-D3）：字段表与备份/解析共用 [CollectionCodec]。 */
    private fun collectionToJson(c: CollectionEntity): JsonObject = CollectionCodec.toJson(c)

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

    /** 收藏解析（A-D3）：缺键字段解析为 null，交给合并侧保留本地值。 */
    private fun parseCollections(element: JsonElement?): List<CollectionEntity> =
        CollectionCodec.parseList(element)

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