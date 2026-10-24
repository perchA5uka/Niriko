package com.otakup.niriko.data.sync

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.model.WatchStatus
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * A-D3：收藏 JSON 编解码（纯 JVM，手搓 JSON，不连 Room / OkHttp）。
 *
 * 重点不是「字段能读回来」，而是**缺键不覆盖**：旧客户端回传的行没有卷进度键，
 * 解析成 null 后合并必须保留本地值，否则新字段会被一次同步抹空。
 */
class CollectionJsonRoundTripTest {

    private fun entity(
        watchedEpisodes: Int? = 3,
        watchedVolumes: Int? = 12,
        rating: Float? = 8.5f,
        isPrivate: Boolean = false,
    ) = CollectionEntity(
        id = 7L,
        subjectId = 100L,
        status = WatchStatus.WATCHING,
        watchedEpisodes = watchedEpisodes,
        watchedVolumes = watchedVolumes,
        isPrivate = isPrivate,
        rating = rating,
        startDate = LocalDate.of(2026, 1, 2),
        finishDate = null,
        personalTags = listOf("百合", "2026"),
        personalImpression = "印象",
        remark = "备注",
        watchedTrackIds = listOf(11L, 12L),
        createTime = 1_000L,
        updateTime = 2_000L,
    )

    @Test
    fun roundTripKeepsVolumesAndEveryOtherField() {
        val original = entity()

        val json = CollectionCodec.toJson(original)
        assertTrue(CollectionCodec.hasWatchedVolumes(json))
        assertEquals(12, json["watchedVolumes"]?.jsonPrimitive?.content?.toInt())

        val parsed = CollectionCodec.fromJson(json)
        assertEquals(original, parsed)
    }

    @Test
    fun nullVolumesIsOmittedInsteadOfWrittenAsNull() {
        val json = CollectionCodec.toJson(entity(watchedVolumes = null))
        // 缺键而非 null：远端才能区分「旧客户端没有这个字段」
        assertFalse(CollectionCodec.hasWatchedVolumes(json))
        assertNull(CollectionCodec.fromJson(json)?.watchedVolumes)
    }

    @Test
    fun isPrivateNeverLeavesTheDevice() {
        val json = CollectionCodec.toJson(entity(isPrivate = true))
        assertFalse(json.containsKey("isPrivate"))
    }

    @Test
    fun legacyJsonWithoutVolumesKeyMergesWithoutLosingLocalVolumes() {
        // 旧客户端回传：没有 watchedVolumes 键，但 updateTime 更新（LWW 基准会选它）
        val legacy = buildJsonObject {
            put("id", JsonPrimitive(7L))
            put("subjectId", JsonPrimitive(100L))
            put("status", JsonPrimitive("WATCHING"))
            put("watchedEpisodes", JsonPrimitive(3))
            put("createTime", JsonPrimitive(1_000L))
            put("updateTime", JsonPrimitive(9_000L))
        }
        val remote = CollectionCodec.fromJson(legacy)
        assertNull(remote?.watchedVolumes)

        val local = entity(watchedVolumes = 12).copy(updateTime = 1_000L)
        val merged = SyncMergePolicy.mergeCollections(listOf(local), listOf(requireNotNull(remote))).single()

        assertEquals(12, merged.watchedVolumes)
        assertEquals(9_000L, merged.updateTime) // 其余字段仍按 LWW 取基准行
    }

    @Test
    fun parseListSkipsBrokenRowsButKeepsValidOnes() {
        val valid = CollectionCodec.toJson(entity())
        val broken = buildJsonObject {
            put("subjectId", JsonPrimitive(100L)) // 缺 status
        }
        val noSubject = buildJsonObject {
            put("status", JsonPrimitive("WATCHING"))
        }

        val parsed = CollectionCodec.parseList(
            kotlinx.serialization.json.buildJsonArray {
                add(valid)
                add(broken)
                add(noSubject)
            },
        )

        assertEquals(1, parsed.size)
        assertEquals(100L, parsed.single().subjectId)
    }

    @Test
    fun mergeRemote12AndLocal15Keeps15() {
        val local = entity(watchedVolumes = 15).copy(updateTime = 1_000L)
        val remote = CollectionCodec.fromJson(CollectionCodec.toJson(entity(watchedVolumes = 12)))!!
        val merged = SyncMergePolicy.mergeCollections(listOf(local), listOf(remote)).single()
        assertEquals(15, merged.watchedVolumes)
    }

    @Test
    fun remoteNewerVolumesAndRatingWinWithoutDroppingEither() {
        // 远端 20 卷 + 评分 9（更新），本地 15 卷 + 评分 6：合并后 20 卷 + 9 分
        val local = entity(watchedVolumes = 15, rating = 6f).copy(updateTime = 1_000L)
        val remote = CollectionCodec.fromJson(
            CollectionCodec.toJson(entity(watchedVolumes = 20, rating = 9f).copy(updateTime = 5_000L)),
        )!!
        val merged = SyncMergePolicy.mergeCollections(listOf(local), listOf(remote)).single()
        assertEquals(20, merged.watchedVolumes)
        assertEquals(9f, merged.rating)
    }
}
