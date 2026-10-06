package com.otakup.niriko.data.backup

import android.util.Log
import androidx.room.withTransaction
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.local.WorkItem
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.PersonCollectionEntity
import com.otakup.niriko.data.local.entity.SearchHistoryEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.WorkType
import com.otakup.niriko.data.model.collection.SortOrder
import com.otakup.niriko.data.settings.AppSettings
import com.otakup.niriko.data.settings.ThemeMode
import com.otakup.niriko.data.settings.CardGlassLevel
import com.otakup.niriko.data.settings.WallpaperAtmosphere
import com.otakup.niriko.data.remote.BangumiClient.BangumiEndpoint
import com.otakup.niriko.data.sync.bangumi.BangumiSyncPriority
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * 数据备份/恢复管理器。
 * 导出全部数据为单个 JSON 文件，可从 JSON 文件恢复数据。
 */
class BackupManager(
    private val database: NirikoDatabase,
    /**
     * 封面覆盖存储（阶段 7 纳入备份）。
     * 可空以便既有调用方与单测不必构造 DataStore。
     */
    private val coverOverrideStore: com.otakup.niriko.data.settings.CoverOverrideStore? = null,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    // ==================== 导出 ====================

    /**
     * 导出全部数据为 JSON 字符串。
     */
    suspend fun exportToJson(settings: AppSettings? = null): String {
        val subjects = database.subjectDao().getAll()
        // 阶段 B：私密收藏不跟随导出/备份
        val collections = database.collectionDao().getAll().filter { !it.isPrivate }
        val workItems = database.workDao().getAll()
        val history = database.searchHistoryDao().getAll()
        val persons = database.personCollectionDao().getAll()
        // 阶段 7：新增的「用户数据」表（外部身份绑定、我的每集评分、手动录入的权威成绩）
        val externalIds = database.externalIdDao().getAll()
        val myEpisodeRatings = database.externalRatingDao().getAllMyEpisodeRatings()
        val manualAwards = database.manualAwardDao().getAll()
        val coverOverrides = coverOverrideStore?.all().orEmpty()
        // F09：作品库自定义分区（分区 + 成员关系）。成员关系一并在分区对象里导出，
        // 因为它脱离分区没有意义；身份用**分区名**（自增 id 换库即失效，见 BackupFolderNames）。
        val libraryFolders = database.libraryFolderDao().getFolders()
        // 一次取全部成员再按分区分组：避免「每个分区一次查询」的 N+1（分区多时导出会明显变慢）
        val folderMembers = database.libraryFolderDao().getAllMembers().groupBy { it.folderId }

        return json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("version", JsonPrimitive(EXPORT_VERSION))
            put("exportTime", JsonPrimitive(System.currentTimeMillis()))
            put("appVersion", JsonPrimitive(APP_VERSION))

            put("subjects", buildJsonArray {
                subjects.forEach { s -> add(subjectToJson(s)) }
            })
            put("collections", buildJsonArray {
                collections.forEach { c -> add(collectionToJson(c)) }
            })
            put("workItems", buildJsonArray {
                workItems.forEach { w -> add(workItemToJson(w)) }
            })
            put("searchHistory", buildJsonArray {
                history.forEach { h -> add(searchHistoryToJson(h)) }
            })
            put("personCollections", buildJsonArray {
                persons.forEach { p -> add(personCollectionToJson(p)) }
            })
            put("externalIds", buildJsonArray {
                externalIds.forEach { e ->
                    add(buildJsonObject {
                        put("subjectId", JsonPrimitive(e.subjectId))
                        put("provider", JsonPrimitive(e.provider))
                        put("externalId", JsonPrimitive(e.externalId))
                        e.titleSnapshot?.let { put("titleSnapshot", JsonPrimitive(it)) }
                        put("confidence", JsonPrimitive(e.confidence))
                        put("bindMethod", JsonPrimitive(e.bindMethod))
                        e.subKey?.let { put("subKey", JsonPrimitive(it)) }
                        put("boundAt", JsonPrimitive(e.boundAt))
                    })
                }
            })
            put("myEpisodeRatings", buildJsonArray {
                myEpisodeRatings.forEach { r ->
                    add(buildJsonObject {
                        put("epId", JsonPrimitive(r.epId))
                        put("subjectId", JsonPrimitive(r.subjectId))
                        put("score", JsonPrimitive(r.score))
                        r.comment?.let { put("comment", JsonPrimitive(it)) }
                        put("rewatch", JsonPrimitive(r.rewatch))
                        put("ratedAt", JsonPrimitive(r.ratedAt))
                    })
                }
            })
            put("manualAwards", buildJsonArray {
                manualAwards.forEach { a ->
                    add(buildJsonObject {
                        put("subjectId", JsonPrimitive(a.subjectId))
                        put("sourceId", JsonPrimitive(a.sourceId))
                        a.score?.let { put("score", JsonPrimitive(it)) }
                        put("scoreMax", JsonPrimitive(a.scoreMax))
                        a.rankPosition?.let { put("rankPosition", JsonPrimitive(it)) }
                        a.note?.let { put("note", JsonPrimitive(it)) }
                        a.url?.let { put("url", JsonPrimitive(it)) }
                        put("createTime", JsonPrimitive(a.createTime))
                    })
                }
            })
            put("coverOverrides", buildJsonObject {
                coverOverrides.forEach { (id, uri) -> put(id.toString(), JsonPrimitive(uri)) }
            })
            put("libraryFolders", buildJsonArray {
                libraryFolders.forEach { folder ->
                    add(buildJsonObject {
                        put("name", JsonPrimitive(folder.name))
                        put("sortOrder", JsonPrimitive(folder.sortOrder))
                        put("isCollapsed", JsonPrimitive(folder.isCollapsed))
                        put("createdAt", JsonPrimitive(folder.createdAt))
                        put(
                            "members",
                            buildJsonArray {
                                folderMembers[folder.id]
                                    .orEmpty()
                                    .sortedBy { it.sortOrder }
                                    .forEach { member -> add(JsonPrimitive(member.subjectId)) }
                            },
                        )
                    })
                }
            })
            if (settings != null) {
                put("settings", settingsToJson(settings))
            }
        })
    }

    // ==================== 导入 ====================

    /**
     * 从 JSON 字符串导入/恢复数据。
     * 默认模式为全覆盖（清空现有数据后导入）。
     */
    suspend fun importFromJson(jsonString: String, merge: Boolean = false): ImportResult {
        val root = try {
            json.decodeFromJsonElement<JsonObject>(json.parseToJsonElement(jsonString))
        } catch (e: Exception) {
            return ImportResult(success = false, error = "JSON 解析失败: ${e.message}")
        }

        val version = root["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        if (version < 1 || version > EXPORT_VERSION) {
            return ImportResult(success = false, error = "不支持的备份版本: $version")
        }

        // 解析各表数据
        val subjects = parseSubjects(root["subjects"])
        val collections = parseCollections(root["collections"])
        val workItems = parseWorkItems(root["workItems"])
        val history = parseSearchHistory(root["searchHistory"])
        val persons = parsePersonCollections(root["personCollections"])
        val externalIds = parseExternalIds(root["externalIds"])
        val myEpisodeRatings = parseMyEpisodeRatings(root["myEpisodeRatings"])
        val manualAwards = parseManualAwards(root["manualAwards"])
        val coverOverrides = parseCoverOverrides(root["coverOverrides"])
        // F09：作品库自定义分区（v4 起）。旧备份没有这个字段 → 空列表，导入照常成功。
        val backupFolders = parseBackupFolders(root["libraryFolders"])
        // 修复 BUG-8：新表此前不校验 subjectId，导入会写进孤儿数据
        val knownSubjectIds = subjects.map { it.subjectId }.toHashSet()
        val validExternalIds = externalIds.filter { it.subjectId in knownSubjectIds }
        val validMyEpisodeRatings = myEpisodeRatings.filter { it.subjectId in knownSubjectIds }
        val validManualAwards = manualAwards.filter { it.subjectId in knownSubjectIds }

        // 数据校验
        if (collections.any { c -> subjects.none { s -> s.subjectId == c.subjectId } }) {
            return ImportResult(success = false, error = "备份数据不完整：存在收藏指向不存在的作品")
        }

        // 写入数据库（整体事务：失败自动回滚，不出现半清空状态）
        // 计划 B2-4（用户确认：冲突则本地优先）：合并模式下这些表的 insertAll 是 REPLACE 语义，
        // 直接写会把本地记录覆盖成备份里的旧值，因此先按业务主键剔除「本地已有」的备份记录，
        // 只并入本地没有的，本地值一律保留。merge=false 的全覆盖模式行为不变。
        var insertedSubjects = 0
        var insertedCollections = 0
        var insertedWorkItems = 0
        var insertedHistory = 0
        var insertedFolders = 0
        try {
            database.withTransaction {
                if (!merge) {
                    // 全覆盖模式：先清空
                    database.collectionDao().clearAll()
                    database.subjectDao().clearAll()
                    database.workDao().clearAll()
                    database.searchHistoryDao().clearAll()
                    database.personCollectionDao().clearAll()
                }

                // 本地已有键（merge=false 时为空集 → 全部插入）
                val localSubjectIds =
                    if (merge) database.subjectDao().getAll().map { it.subjectId }.toSet() else emptySet()
                val localCollectionIds =
                    if (merge) database.collectionDao().getAll().map { it.subjectId }.toSet() else emptySet()
                val localWorkIds =
                    if (merge) database.workDao().getAll().map { it.id }.toSet() else emptySet()
                val localHistoryIds =
                    if (merge) database.searchHistoryDao().getAll().map { it.id }.toSet() else emptySet()
                val localPersonIds =
                    if (merge) database.personCollectionDao().getAll().map { it.personId }.toSet() else emptySet()
                val localExternalIds =
                    if (merge) database.externalIdDao().getAll().map { it.subjectId to it.provider }.toSet()
                    else emptySet()
                val localEpisodeRatingIds =
                    if (merge) database.externalRatingDao().getAllMyEpisodeRatings().map { it.epId }.toSet()
                    else emptySet()
                val localAwardIds =
                    if (merge) database.manualAwardDao().getAll().map { it.id }.toSet() else emptySet()

                val subjectsToInsert = RestoreMergePolicy.keepOnlyNew(subjects, localSubjectIds) { it.subjectId }
                val collectionsToInsert =
                    RestoreMergePolicy.keepOnlyNew(collections, localCollectionIds) { it.subjectId }
                val workItemsToInsert = RestoreMergePolicy.keepOnlyNew(workItems, localWorkIds) { it.id }
                val historyToInsert = RestoreMergePolicy.keepOnlyNew(history, localHistoryIds) { it.id }
                val personsToInsert = RestoreMergePolicy.keepOnlyNew(persons, localPersonIds) { it.personId }
                val externalIdsToInsert =
                    RestoreMergePolicy.keepOnlyNew(validExternalIds, localExternalIds) { it.subjectId to it.provider }
                val episodeRatingsToInsert =
                    RestoreMergePolicy.keepOnlyNew(validMyEpisodeRatings, localEpisodeRatingIds) { it.epId }
                val awardsToInsert =
                    RestoreMergePolicy.keepOnlyNew(validManualAwards, localAwardIds) { it.id }

                // 按 FK 顺序插入
                if (subjectsToInsert.isNotEmpty()) database.subjectDao().insertAll(subjectsToInsert)
                if (collectionsToInsert.isNotEmpty()) database.collectionDao().insertAll(collectionsToInsert)
                if (workItemsToInsert.isNotEmpty()) database.workDao().insertAll(workItemsToInsert)
                if (historyToInsert.isNotEmpty()) database.searchHistoryDao().insertAll(historyToInsert)
                if (personsToInsert.isNotEmpty()) database.personCollectionDao().insertAll(personsToInsert)
                // 阶段 7：外部身份绑定 / 我的每集评分 / 手动录入的权威成绩
                // （这些都是"重建成本高"或"纯用户数据"，必须随备份迁移）
                if (externalIdsToInsert.isNotEmpty()) database.externalIdDao().insertAll(externalIdsToInsert)
                if (episodeRatingsToInsert.isNotEmpty()) {
                    database.externalRatingDao().insertAllMyEpisodeRatings(episodeRatingsToInsert)
                }
                if (awardsToInsert.isNotEmpty()) database.manualAwardDao().insertAll(awardsToInsert)

                insertedSubjects = subjectsToInsert.size
                insertedCollections = collectionsToInsert.size
                insertedWorkItems = workItemsToInsert.size
                insertedHistory = historyToInsert.size
                // F09：分区与成员在同一事务里恢复 —— 分区行与关系行要么都进去、要么都不进去。
                // 成员只保留「指向本次备份确实存在的作品」的那些（与上面几张表同一条防孤儿规则）。
                if (backupFolders.isNotEmpty()) {
                    insertedFolders = BackupFolderRestore.restore(
                        dao = database.libraryFolderDao(),
                        folders = backupFolders.map { folder ->
                            folder.copy(subjectIds = folder.subjectIds.filter { it in knownSubjectIds })
                        },
                        fullReplace = !merge,
                    ).touched
                }
            }
            // 封面覆盖同样本地优先：本地已有该作品的覆盖 → 保留本地
            val coverToWrite = if (merge) {
                val localCoverIds = runCatching { coverOverrideStore?.all()?.keys }
                    .getOrNull()
                    ?.toSet()
                    ?: emptySet()
                // 用 Pair 列表而不是 Map.Entry：toMap() 只对 Iterable<Pair> 可用
                val backupCovers = coverOverrides.map { it.key to it.value }
                RestoreMergePolicy.keepOnlyNew(backupCovers, localCoverIds) { it.first }.toMap()
            } else {
                coverOverrides
            }
            coverOverrideStore?.putAll(coverToWrite, clearFirst = !merge)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            return ImportResult(success = false, error = "数据写入失败: ${e.message}")
        }

        // 解析设置。
        // v3 起完整导出所有设置；v2 及更早只导出过部分设置，直接恢复会把未备份字段清成默认值，
        // 因此旧版本备份不再恢复设置，避免误清空 Steam/WebDAV/Bangumi 等配置。
        val settings = if (version >= 3) parseSettings(root["settings"]) else null

        // 计数按「实际写入」返回：合并模式下被本地保留的冲突记录不计入
        return ImportResult(
            success = true,
            subjectsCount = insertedSubjects,
            collectionsCount = insertedCollections,
            workItemsCount = insertedWorkItems,
            historyCount = insertedHistory,
            foldersCount = insertedFolders,
            settings = settings,
        )
    }

    // ==================== JSON 序列化 ====================

    private fun subjectToJson(s: SubjectEntity): JsonObject = buildJsonObject {
        put("subjectId", JsonPrimitive(s.subjectId))
        put("title", JsonPrimitive(s.title))
        s.titleCN?.let { put("titleCN", JsonPrimitive(it)) }
        put("type", JsonPrimitive(s.type.name))
        s.summary?.let { put("summary", JsonPrimitive(it)) }
        s.coverUrl?.let { put("coverUrl", JsonPrimitive(it)) }
        s.totalEpisodes?.let { put("totalEpisodes", JsonPrimitive(it)) }
        s.platform?.let { put("platform", JsonPrimitive(it)) }
        s.volumes?.let { put("volumes", JsonPrimitive(it)) }
        s.airDate?.let { put("airDate", JsonPrimitive(it)) }
        s.airWeekday?.let { put("airWeekday", JsonPrimitive(it)) }
        s.ratingScore?.let { put("ratingScore", JsonPrimitive(it.toDouble())) }
        s.ratingTotal?.let { put("ratingTotal", JsonPrimitive(it)) }
        s.biliScore?.let { put("biliScore", JsonPrimitive(it.toDouble())) }
        s.biliRatingTotal?.let { put("biliRatingTotal", JsonPrimitive(it)) }
        s.biliSeasonId?.let { put("biliSeasonId", JsonPrimitive(it)) }
        s.series?.let { put("series", JsonPrimitive(it)) }
        if (s.tags.isNotEmpty()) {
            put("tags", buildJsonArray { s.tags.forEach { add(JsonPrimitive(it)) } })
        }
        put("lastSyncTime", JsonPrimitive(s.lastSyncTime))
        put("sourceId", JsonPrimitive(s.sourceId))
        s.rank?.let { put("rank", JsonPrimitive(it)) }
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

    private fun searchHistoryToJson(h: SearchHistoryEntity): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(h.id))
        put("keyword", JsonPrimitive(h.keyword))
        put("createTime", JsonPrimitive(h.createTime))
    }

    private fun personCollectionToJson(p: PersonCollectionEntity): JsonObject = buildJsonObject {
        put("personId", JsonPrimitive(p.personId))
        put("name", JsonPrimitive(p.name))
        p.nameCn?.let { put("nameCn", JsonPrimitive(it)) }
        p.imageUrl?.let { put("imageUrl", JsonPrimitive(it)) }
        if (p.career.isNotEmpty()) {
            put("career", buildJsonArray { p.career.forEach { add(JsonPrimitive(it)) } })
        }
        put("createTime", JsonPrimitive(p.createTime))
    }

    private fun settingsToJson(s: AppSettings): JsonObject = buildJsonObject {
        // 外观
        put("themeMode", JsonPrimitive(s.themeMode.name))
        put("dynamicColor", JsonPrimitive(s.dynamicColor))
        put("oledDark", JsonPrimitive(s.oledDark))
        put("themeColorIndex", JsonPrimitive(s.themeColorIndex))
        put("customSeedColor", JsonPrimitive(s.customSeedColor))
        put("wallpaperEnabled", JsonPrimitive(s.wallpaperEnabled))
        put("wallpaperUri", JsonPrimitive(s.wallpaperUri))
        put("wallpaperLibraryUri", JsonPrimitive(s.wallpaperLibraryUri))
        put("wallpaperDiscoverUri", JsonPrimitive(s.wallpaperDiscoverUri))
        put("wallpaperStatsUri", JsonPrimitive(s.wallpaperStatsUri))
        put("wallpaperSettingsUri", JsonPrimitive(s.wallpaperSettingsUri))
        put("wallpaperBlurDp", JsonPrimitive(s.wallpaperBlurDp))
        put("wallpaperAtmosphere", JsonPrimitive(s.wallpaperAtmosphere.name))
        // 壁纸库（R3）：整表编码成一行 JSON 存进设置备份，恢复时由 WallpaperLibraryCodec 解析
        put("wallpaperLibrary", JsonPrimitive(com.otakup.niriko.data.wallpaper.WallpaperLibraryCodec.encode(s.wallpaperLibraryEntries)))
        put("wallpaperRotationEnabled", JsonPrimitive(s.wallpaperRotationEnabled))
        put("wallpaperRotationFavoritesOnly", JsonPrimitive(s.wallpaperRotationFavoritesOnly))
        put("cardGlassLevel", JsonPrimitive(s.cardGlassLevel.name))
        put("splashEnabled", JsonPrimitive(s.splashEnabled))
        put("activeThemePackId", JsonPrimitive(s.activeThemePackId))
        put("reduceMotion", JsonPrimitive(s.reduceMotion))
        put("customIconMode", JsonPrimitive(s.customIconMode))

        // 豆瓣（灰色通道）：此前完全没进 settings JSON，恢复时会被清成默认值（本次一并补上）
        put("doubanPhotosEnabled", JsonPrimitive(s.doubanPhotosEnabled))
        put("doubanAntiSpoiler", JsonPrimitive(s.doubanAntiSpoiler))
        put("doubanImageReferer", JsonPrimitive(s.doubanImageReferer))
        put("doubanApiReferer", JsonPrimitive(s.doubanApiReferer))
        // 首启标记（B1 新增）：不进备份的话，恢复后会被重置为 false → 又弹一次引导
        put("firstRunCompleted", JsonPrimitive(s.firstRunCompleted))

        // 收藏
        put("defaultSortOrder", JsonPrimitive(s.defaultSortOrder.name))
        put("showStatusTags", JsonPrimitive(s.showStatusTags))
        put("showProgressBar", JsonPrimitive(s.showProgressBar))

        // 搜索
        put("nsfwEnabled", JsonPrimitive(s.nsfwEnabled))
        put("showSearchSuggestions", JsonPrimitive(s.showSearchSuggestions))

        // 数据源
        put("activeDataSourceId", JsonPrimitive(s.activeDataSourceId))
        put("bangumiEndpoint", JsonPrimitive(s.bangumiEndpoint.name))
        put("steamApiKey", JsonPrimitive(s.steamApiKey))
        put("steamId64", JsonPrimitive(s.steamId64))
        put("steamWebApiToken", JsonPrimitive(s.steamWebApiToken))

        // WebDAV
        put("webDavUrl", JsonPrimitive(s.webDavUrl))
        put("webDavUsername", JsonPrimitive(s.webDavUsername))
        put("webDavPassword", JsonPrimitive(s.webDavPassword))
        put("webDavAutoSync", JsonPrimitive(s.webDavAutoSync))

        // Bangumi 账号
        put("bangumiAccessToken", JsonPrimitive(s.bangumiAccessToken))
        put("bangumiTokenType", JsonPrimitive(s.bangumiTokenType))
        put("bangumiUsername", JsonPrimitive(s.bangumiUsername))
        put("bangumiSyncEnabled", JsonPrimitive(s.bangumiSyncEnabled))
        put("bangumiSyncPriority", JsonPrimitive(s.bangumiSyncPriority.name))
        put("bangumiAutoSync", JsonPrimitive(s.bangumiAutoSync))

        // 统计与启动
        put("showAnnuallySummary", JsonPrimitive(s.showAnnuallySummary))
        put("startPage", JsonPrimitive(s.startPage))
    }

    // ==================== JSON 反序列化 ====================

    private fun parseSubjects(element: JsonElement?): List<SubjectEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.map { obj ->
            val o = obj.jsonObject
            SubjectEntity(
                subjectId = o["subjectId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@map null,
                title = o["title"]?.jsonPrimitive?.content ?: "",
                titleCN = o["titleCN"]?.jsonPrimitive?.content,
                type = o["type"]?.jsonPrimitive?.content?.let { try { SubjectType.valueOf(it) } catch (_: Exception) { null } } ?: return@map null,
                summary = o["summary"]?.jsonPrimitive?.content,
                coverUrl = o["coverUrl"]?.jsonPrimitive?.content,
                totalEpisodes = o["totalEpisodes"]?.jsonPrimitive?.content?.toIntOrNull(),
                platform = o["platform"]?.jsonPrimitive?.content,
                volumes = o["volumes"]?.jsonPrimitive?.content?.toIntOrNull(),
                airDate = o["airDate"]?.jsonPrimitive?.content,
                airWeekday = o["airWeekday"]?.jsonPrimitive?.content?.toIntOrNull(),
                ratingScore = o["ratingScore"]?.jsonPrimitive?.content?.toFloatOrNull(),
                ratingTotal = o["ratingTotal"]?.jsonPrimitive?.content?.toIntOrNull(),
                biliScore = o["biliScore"]?.jsonPrimitive?.content?.toFloatOrNull(),
                biliRatingTotal = o["biliRatingTotal"]?.jsonPrimitive?.content?.toIntOrNull(),
                biliSeasonId = o["biliSeasonId"]?.jsonPrimitive?.content?.toIntOrNull(),
                series = o["series"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
                tags = parseStringList(o["tags"]),
                sourceId = o["sourceId"]?.jsonPrimitive?.content ?: "bangumi",
                lastSyncTime = o["lastSyncTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                rank = o["rank"]?.jsonPrimitive?.content?.toIntOrNull(),
            )
        }.filterNotNull()
    }

    /**
     * 解析备份里的作品库分区（F09）。
     *
     * 容错口径与其它解析函数一致：字段缺失用默认值，整条记录结构不对就跳过，
     * **不因为一个新字段让整份备份导入失败**（旧版本备份根本没有这个字段）。
     */
    private fun parseBackupFolders(element: JsonElement?): List<BackupFolder> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            BackupFolder(
                name = name,
                sortOrder = obj["sortOrder"]?.jsonPrimitive?.intOrNull ?: 0,
                isCollapsed = obj["isCollapsed"]?.jsonPrimitive?.booleanOrNull ?: false,
                createdAt = obj["createdAt"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis(),
                subjectIds = (obj["members"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.longOrNull }
                    ?.filter { it > 0L }
                    .orEmpty(),
            )
        }
    }

    private fun parseCollections(element: JsonElement?): List<CollectionEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.map { obj ->
            val o = obj.jsonObject
            CollectionEntity(
                id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                subjectId = o["subjectId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@map null,
                status = o["status"]?.jsonPrimitive?.content?.let { try { WatchStatus.valueOf(it) } catch (_: Exception) { null } } ?: return@map null,
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
        }.filterNotNull()
    }

    private fun parseWorkItems(element: JsonElement?): List<WorkItem> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.map { obj ->
            val o = obj.jsonObject
            WorkItem(
                id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                title = o["title"]?.jsonPrimitive?.content ?: return@map null,
                type = o["type"]?.jsonPrimitive?.content?.let { try { WorkType.valueOf(it) } catch (_: Exception) { null } } ?: return@map null,
                status = o["status"]?.jsonPrimitive?.content?.let { try { WatchStatus.valueOf(it) } catch (_: Exception) { null } } ?: return@map null,
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
        }.filterNotNull()
    }

    private fun parseSearchHistory(element: JsonElement?): List<SearchHistoryEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { obj ->
            val o = obj.jsonObject
            SearchHistoryEntity(
                id = o["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                keyword = o["keyword"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                createTime = o["createTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
            )
        }
    }

    private fun parseExternalIds(element: JsonElement?): List<com.otakup.niriko.data.local.entity.SubjectExternalIdEntity> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val subjectId = o["subjectId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val provider = o["provider"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val externalId = o["externalId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            com.otakup.niriko.data.local.entity.SubjectExternalIdEntity(
                subjectId = subjectId,
                provider = provider,
                externalId = externalId,
                titleSnapshot = o["titleSnapshot"]?.jsonPrimitive?.contentOrNull,
                confidence = o["confidence"]?.jsonPrimitive?.floatOrNull ?: 0f,
                bindMethod = o["bindMethod"]?.jsonPrimitive?.contentOrNull
                    ?: com.otakup.niriko.data.local.entity.SubjectExternalIdEntity.METHOD_MANUAL,
                subKey = o["subKey"]?.jsonPrimitive?.contentOrNull,
                boundAt = o["boundAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
    }

    private fun parseMyEpisodeRatings(element: JsonElement?): List<com.otakup.niriko.data.local.entity.EpisodeMyRatingEntity> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val epId = o["epId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val subjectId = o["subjectId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val score = o["score"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
            com.otakup.niriko.data.local.entity.EpisodeMyRatingEntity(
                epId = epId,
                subjectId = subjectId,
                score = score,
                comment = o["comment"]?.jsonPrimitive?.contentOrNull,
                rewatch = o["rewatch"]?.jsonPrimitive?.booleanOrNull ?: false,
                ratedAt = o["ratedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
    }

    private fun parseManualAwards(element: JsonElement?): List<com.otakup.niriko.data.local.entity.ManualAwardEntity> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val subjectId = o["subjectId"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val sourceId = o["sourceId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            com.otakup.niriko.data.local.entity.ManualAwardEntity(
                subjectId = subjectId,
                sourceId = sourceId,
                score = o["score"]?.jsonPrimitive?.floatOrNull,
                scoreMax = o["scoreMax"]?.jsonPrimitive?.floatOrNull ?: 40f,
                rankPosition = o["rankPosition"]?.jsonPrimitive?.intOrNull,
                note = o["note"]?.jsonPrimitive?.contentOrNull,
                url = o["url"]?.jsonPrimitive?.contentOrNull,
                createTime = o["createTime"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
    }

    private fun parseCoverOverrides(element: JsonElement?): Map<Long, String> {
        val obj = element as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (key, value) ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            val uri = value.jsonPrimitive.contentOrNull ?: return@mapNotNull null
            if (uri.isBlank()) null else id to uri
        }.toMap()
    }

    private fun parsePersonCollections(element: JsonElement?): List<PersonCollectionEntity> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { obj ->
            val o = obj.jsonObject
            PersonCollectionEntity(
                personId = o["personId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null,
                name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                nameCn = o["nameCn"]?.jsonPrimitive?.content,
                imageUrl = o["imageUrl"]?.jsonPrimitive?.content,
                career = parseStringList(o["career"]),
                createTime = o["createTime"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis(),
            )
        }
    }

    private fun parseLongList(element: JsonElement?): List<Long> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }
    }

    private fun parseSettings(element: JsonElement?): AppSettings? {
        val o = element as? JsonObject ?: return null
        val defaults = AppSettings()
        return AppSettings(
            // 外观
            themeMode = o["themeMode"]?.jsonPrimitive?.content?.let { n ->
                try { ThemeMode.valueOf(n) } catch (_: Exception) { null }
            } ?: defaults.themeMode,
            dynamicColor = o["dynamicColor"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.dynamicColor,
            oledDark = o["oledDark"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.oledDark,
            themeColorIndex = o["themeColorIndex"]?.jsonPrimitive?.content?.toIntOrNull() ?: defaults.themeColorIndex,
            customSeedColor = o["customSeedColor"]?.jsonPrimitive?.content?.toIntOrNull() ?: defaults.customSeedColor,
            wallpaperEnabled = o["wallpaperEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.wallpaperEnabled,
            wallpaperUri = o["wallpaperUri"]?.jsonPrimitive?.content ?: defaults.wallpaperUri,
            wallpaperLibraryUri = o["wallpaperLibraryUri"]?.jsonPrimitive?.content ?: defaults.wallpaperLibraryUri,
            wallpaperDiscoverUri = o["wallpaperDiscoverUri"]?.jsonPrimitive?.content ?: defaults.wallpaperDiscoverUri,
            wallpaperStatsUri = o["wallpaperStatsUri"]?.jsonPrimitive?.content ?: defaults.wallpaperStatsUri,
            wallpaperSettingsUri = o["wallpaperSettingsUri"]?.jsonPrimitive?.content ?: defaults.wallpaperSettingsUri,
            wallpaperBlurDp = o["wallpaperBlurDp"]?.jsonPrimitive?.content?.toIntOrNull() ?: defaults.wallpaperBlurDp,
            wallpaperAtmosphere = o["wallpaperAtmosphere"]?.jsonPrimitive?.content?.let { n ->
                try { WallpaperAtmosphere.valueOf(n) } catch (_: Exception) { null }
            } ?: defaults.wallpaperAtmosphere,
            wallpaperLibraryEntries = com.otakup.niriko.data.wallpaper.WallpaperLibraryCodec.decode(
                o["wallpaperLibrary"]?.jsonPrimitive?.content,
            ),
            wallpaperRotationEnabled = o["wallpaperRotationEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: defaults.wallpaperRotationEnabled,
            wallpaperRotationFavoritesOnly = o["wallpaperRotationFavoritesOnly"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: defaults.wallpaperRotationFavoritesOnly,
            cardGlassLevel = o["cardGlassLevel"]?.jsonPrimitive?.content?.let { n ->
                try { CardGlassLevel.valueOf(n) } catch (_: Exception) { null }
            } ?: defaults.cardGlassLevel,
            splashEnabled = o["splashEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.splashEnabled,
            activeThemePackId = o["activeThemePackId"]?.jsonPrimitive?.content ?: defaults.activeThemePackId,
            reduceMotion = o["reduceMotion"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.reduceMotion,
            customIconMode = o["customIconMode"]?.jsonPrimitive?.content ?: defaults.customIconMode,

            // 收藏
            defaultSortOrder = o["defaultSortOrder"]?.jsonPrimitive?.content?.let { n ->
                try { SortOrder.valueOf(n) } catch (_: Exception) { null }
            } ?: defaults.defaultSortOrder,
            showStatusTags = o["showStatusTags"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.showStatusTags,
            showProgressBar = o["showProgressBar"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.showProgressBar,

            // 搜索
            nsfwEnabled = o["nsfwEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.nsfwEnabled,
            showSearchSuggestions = o["showSearchSuggestions"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.showSearchSuggestions,

            // 数据源
            activeDataSourceId = o["activeDataSourceId"]?.jsonPrimitive?.content ?: defaults.activeDataSourceId,
            bangumiEndpoint = o["bangumiEndpoint"]?.jsonPrimitive?.content?.let { n ->
                try { BangumiEndpoint.valueOf(n) } catch (_: Exception) { null }
            } ?: defaults.bangumiEndpoint,
            steamApiKey = o["steamApiKey"]?.jsonPrimitive?.content ?: defaults.steamApiKey,
            steamId64 = o["steamId64"]?.jsonPrimitive?.content ?: defaults.steamId64,
            steamWebApiToken = o["steamWebApiToken"]?.jsonPrimitive?.content ?: defaults.steamWebApiToken,

            // WebDAV
            webDavUrl = o["webDavUrl"]?.jsonPrimitive?.content ?: defaults.webDavUrl,
            webDavUsername = o["webDavUsername"]?.jsonPrimitive?.content ?: defaults.webDavUsername,
            webDavPassword = o["webDavPassword"]?.jsonPrimitive?.content ?: defaults.webDavPassword,
            webDavAutoSync = o["webDavAutoSync"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.webDavAutoSync,

            // Bangumi 账号
            bangumiAccessToken = o["bangumiAccessToken"]?.jsonPrimitive?.content ?: defaults.bangumiAccessToken,
            bangumiTokenType = o["bangumiTokenType"]?.jsonPrimitive?.content ?: defaults.bangumiTokenType,
            bangumiUsername = o["bangumiUsername"]?.jsonPrimitive?.content ?: defaults.bangumiUsername,
            bangumiSyncEnabled = o["bangumiSyncEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.bangumiSyncEnabled,
            bangumiSyncPriority = o["bangumiSyncPriority"]?.jsonPrimitive?.content?.let { n ->
                try { BangumiSyncPriority.valueOf(n) } catch (_: Exception) { null }
            } ?: defaults.bangumiSyncPriority,
            bangumiAutoSync = o["bangumiAutoSync"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.bangumiAutoSync,

            // 豆瓣（灰色通道）—— 见 settingsToJson 同名键
            doubanPhotosEnabled = o["doubanPhotosEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.doubanPhotosEnabled,
            doubanAntiSpoiler = o["doubanAntiSpoiler"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.doubanAntiSpoiler,
            doubanImageReferer = o["doubanImageReferer"]?.jsonPrimitive?.content ?: defaults.doubanImageReferer,
            doubanApiReferer = o["doubanApiReferer"]?.jsonPrimitive?.content ?: defaults.doubanApiReferer,

            // 统计与启动
            showAnnuallySummary = o["showAnnuallySummary"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.showAnnuallySummary,
            startPage = o["startPage"]?.jsonPrimitive?.content ?: defaults.startPage,
            // 首启标记（B1 新增）：不进备份的话，恢复后会被重置为 false → 又弹一次引导
            firstRunCompleted = o["firstRunCompleted"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.firstRunCompleted,
        )
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    companion object {
        /**
     * 备份格式版本（F09 起为 4）。
     *
     * v3 → v4：新增 `libraryFolders`（作品库自定义分区）与每个分区下的 `members`。
     * 旧版本备份（≤3）没有这两个字段 → 解析成空列表，导入照常成功（不视为错误）。
     */
    private const val EXPORT_VERSION = 4
        private val APP_VERSION: String = com.otakup.niriko.BuildConfig.VERSION_NAME
        private const val TAG = "BackupManager"
    }
}

/** 导入结果。 */
data class ImportResult(
    val success: Boolean,
    val error: String? = null,
    val subjectsCount: Int = 0,
    val collectionsCount: Int = 0,
    val workItemsCount: Int = 0,
    val historyCount: Int = 0,
    /** F09：本次恢复涉及的作品库分区数（新建 + 填充成员）。0 = 备份里没有分区或已存在同名分区。 */
    val foldersCount: Int = 0,
    val settings: AppSettings? = null,
)
