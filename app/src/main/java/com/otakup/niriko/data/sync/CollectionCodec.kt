package com.otakup.niriko.data.sync

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.model.WatchStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

/**
 * 收藏记录的同步 JSON 编解码（A-D3）。
 *
 * 抽成纯 object 的三个理由：
 * 1. 卷进度 [CollectionEntity.watchedVolumes] 此前没进 JSON，换设备/恢复备份后会归零；
 * 2. 「缺键 ≠ null」需要可断言：旧客户端回传的行没有 watchedVolumes 键，解析为 null，
 *    合并侧据此**保留本地值**，否则新字段会被旧客户端一次性抹空；
 * 3. 不连 Room / OkHttp，可以直接用 JVM 单测锁定字段表。
 *
 * 注意：`isPrivate` **不**进 JSON —— 私密收藏本来就不上传（[SyncManager] 上传前过滤），
 * 把它写进远端等于泄漏。
 */
internal object CollectionCodec {

    const val KEY_WATCHED_EPISODES = "watchedEpisodes"
    const val KEY_WATCHED_VOLUMES = "watchedVolumes"

    /** 序列化：null 字段直接省略（缺键语义见 [fromJson]）。 */
    fun toJson(c: CollectionEntity): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(c.id))
        put("subjectId", JsonPrimitive(c.subjectId))
        put("status", JsonPrimitive(c.status.name))
        c.watchedEpisodes?.let { put(KEY_WATCHED_EPISODES, JsonPrimitive(it)) }
        // A-D3：卷进度（书籍/漫画）。动画/三次元用它为 null，不影响旧字段。
        c.watchedVolumes?.let { put(KEY_WATCHED_VOLUMES, JsonPrimitive(it)) }
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

    /**
     * 反序列化。缺键字段解析为 null（不是 0），整行不可用时返回 null。
     *
     * 缺键与显式 null 都落到 null：合并侧看到 null 就保留本地值（MAX / 并集），
     * 所以旧客户端回传不会把新字段抹掉。
     */
    fun fromJson(o: JsonObject): CollectionEntity? {
        return try {
            val subjectId = o["subjectId"]?.stringValue()?.toLongOrNull() ?: return null
            val statusName = o["status"]?.stringValue() ?: return null
            CollectionEntity(
                id = o["id"]?.stringValue()?.toLongOrNull() ?: 0L,
                subjectId = subjectId,
                status = WatchStatus.valueOf(statusName),
                watchedEpisodes = o[KEY_WATCHED_EPISODES]?.stringValue()?.toIntOrNull(),
                watchedVolumes = o[KEY_WATCHED_VOLUMES]?.stringValue()?.toIntOrNull(),
                rating = o["rating"]?.stringValue()?.toFloatOrNull(),
                startDate = o["startDate"]?.stringValue()?.toLocalDateOrNull(),
                finishDate = o["finishDate"]?.stringValue()?.toLocalDateOrNull(),
                personalTags = o["personalTags"].toStringList(),
                personalImpression = o["personalImpression"]?.stringValue(),
                remark = o["remark"]?.stringValue(),
                watchedTrackIds = o["watchedTrackIds"].toLongList(),
                createTime = o["createTime"]?.stringValue()?.toLongOrNull() ?: System.currentTimeMillis(),
                updateTime = o["updateTime"]?.stringValue()?.toLongOrNull() ?: System.currentTimeMillis(),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 解析 JSON 数组；非数组 / 坏行一律跳过（同步不能因为一行坏数据整体失败）。 */
    fun parseList(element: JsonElement?): List<CollectionEntity> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { row ->
            (row as? JsonObject)?.let { fromJson(it) }
        }
    }

    /** 该行是否带卷进度键（诊断/测试用：区分「旧客户端没这个字段」与「值为 null」）。 */
    fun hasWatchedVolumes(o: JsonObject): Boolean = o.containsKey(KEY_WATCHED_VOLUMES)

    private fun JsonElement.stringValue(): String? = (this as? JsonPrimitive)?.content

    private fun JsonElement?.toStringList(): List<String> {
        val array = this as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    private fun JsonElement?.toLongList(): List<Long> {
        val array = this as? JsonArray ?: return emptyList()
        return array.mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }
    }

    private fun String.toLocalDateOrNull(): LocalDate? =
        try { LocalDate.parse(this) } catch (_: Exception) { null }
}
