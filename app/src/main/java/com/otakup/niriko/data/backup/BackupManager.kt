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

                // 按 FK 顺序插入
                if (subjects.isNotEmpty()) database.subjectDao().insertAll(subjects)
                if (collections.isNotEmpty()) database.collectionDao().insertAll(collections)
                if (workItems.isNotEmpty()) database.workDao().insertAll(workItems)
                if (history.isNotEmpty()) database.searchHistoryDao().insertAll(history)
                if (persons.isNotEmpty()) database.personCollectionDao().insertAll(persons)
                // 阶段 7：外部身份绑定 / 我的每集评分 / 手动录入的权威成绩
                // （这些都是"重建成本高"或"纯用户数据"，必须随备份迁移）
                if (validExternalIds.isNotEmpty()) database.externalIdDao().insertAll(validExternalIds)
                if (validMyEpisodeRatings.isNotEmpty()) {
                    database.externalRatingDao().insertAllMyEpisodeRatings(validMyEpisodeRatings)
                }
                if (validManualAwards.isNotEmpty()) database.manualAwardDao().insertAll(validManualAwards)
            }
            coverOverrideStore?.putAll(coverOverrides, clearFirst = !merge)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            return ImportResult(success = false, error = "数据写入失败: ${e.message}")
        }

        // 解析设置。
        // v3 起完整导出所有设置；v2 及更早只导出过部分设置，直接恢复会把未备份字段清成默认值，
        // 因此旧版本备份不再恢复设置，避免误清空 Steam/WebDAV/Bangumi 等配置。
        val settings = if (version >= 3) parseSettings(root["settings"]) else null

        return ImportResult(
            success = true,
            subjectsCount = subjects.size,
            collectionsCount = collections.size,
            workItemsCount = workItems.size,
            historyCount = history.size,
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
        put("cardGlassLevel", JsonPrimitive(s.cardGlassLevel.name))
        put("splashEnabled", JsonPrimitive(s.splashEnabled))
        put("activeThemePackId", JsonPrimitive(s.activeThemePackId))
        put("reduceMotion", JsonPrimitive(s.reduceMotion))
        put("customIconMode", JsonPrimitive(s.customIconMode))

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

            // 统计与启动
            showAnnuallySummary = o["showAnnuallySummary"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: defaults.showAnnuallySummary,
            startPage = o["startPage"]?.jsonPrimitive?.content ?: defaults.startPage,
        )
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    companion object {
        private const val EXPORT_VERSION = 3
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
    val settings: AppSettings? = null,
)
