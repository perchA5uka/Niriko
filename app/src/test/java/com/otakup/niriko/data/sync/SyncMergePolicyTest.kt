package com.otakup.niriko.data.sync

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDAV 同步合并策略单元测试（计划 B4 · 4-8）。
 */
class SyncMergePolicyTest {

    private data class Row(val key: Long, val value: String, val time: Long)

    @Test
    fun lastWriteWins_prefersNewerTimestamp() {
        val local = listOf(Row(1, "local", 100))
        val remote = listOf(Row(1, "remote", 200))
        val merged = SyncMergePolicy.lastWriteWins(local, remote, { it.key }, { it.time })
        assertEquals(listOf(Row(1, "remote", 200)), merged)
    }

    @Test
    fun lastWriteWins_keepsLocalWhenNewerOrEqual() {
        val older = SyncMergePolicy.lastWriteWins(
            listOf(Row(1, "local", 300)), listOf(Row(1, "remote", 200)), { it.key }, { it.time },
        )
        assertEquals("local", older.single().value)
        // 时间戳相等时保留本地（不来回抖动）
        val equal = SyncMergePolicy.lastWriteWins(
            listOf(Row(2, "local", 500)), listOf(Row(2, "remote", 500)), { it.key }, { it.time },
        )
        assertEquals("local", equal.single().value)
    }

    @Test
    fun lastWriteWins_unionsOneSidedRows() {
        val merged = SyncMergePolicy.lastWriteWins(
            listOf(Row(1, "local", 10)),
            listOf(Row(2, "remote", 20)),
            { it.key },
            { it.time },
        )
        assertEquals(setOf(1L, 2L), merged.map { it.key }.toSet())
    }

    @Test
    fun lastWriteWins_emptySides() {
        val rows = listOf(Row(1, "a", 1))
        assertEquals(rows, SyncMergePolicy.lastWriteWins(emptyList(), rows, { it.key }, { it.time }))
        assertEquals(rows, SyncMergePolicy.lastWriteWins(rows, emptyList(), { it.key }, { it.time }))
    }

    @Test
    fun lastWriteWins_compositeKey() {
        val local = listOf(Pair("tmdb_tv" to 1L, "local"), Pair("imdb" to 1L, "local"))
        val remote = listOf(Pair("tmdb_tv" to 1L, "remote"))
        val merged = SyncMergePolicy.lastWriteWins(
            local.map { Triple(it.first.first, it.first.second, it.second) },
            remote.map { Triple(it.first.first, it.first.second, it.second) },
            { it.first to it.second },
            { 0L },
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun localFirst_keepsLocalCoversAndAddsRemoteOnly() {
        val merged = SyncMergePolicy.localFirst(
            mapOf(1L to "local://1"),
            mapOf(1L to "remote://1", 2L to "remote://2"),
        )
        assertEquals("local://1", merged[1L])
        assertEquals("remote://2", merged[2L])
    }

    @Test
    fun localFirst_emptyLocalTakesRemote() {
        val remote = mapOf(1L to "remote://1")
        assertEquals(remote, SyncMergePolicy.localFirst(emptyMap<Long, String>(), remote))
    }

    // ===== A-D3：进度字段的 MAX 合并 =====

    @Test
    fun maxOfNullable_keepsWhicheverSideHasValue() {
        assertEquals(3, SyncMergePolicy.maxOfNullable(null, 3))
        assertEquals(3, SyncMergePolicy.maxOfNullable(3, null))
        assertEquals(5, SyncMergePolicy.maxOfNullable(3, 5))
        assertEquals(5, SyncMergePolicy.maxOfNullable(5, 3))
        assertNull(SyncMergePolicy.maxOfNullable(null, null))
    }

    private fun collection(
        subjectId: Long = 1L,
        updateTime: Long = 1_000L,
        status: WatchStatus = WatchStatus.WATCHING,
        watchedEpisodes: Int? = null,
        watchedVolumes: Int? = null,
        rating: Float? = null,
        isPrivate: Boolean = false,
        remark: String? = null,
    ) = CollectionEntity(
        id = subjectId,
        subjectId = subjectId,
        status = status,
        watchedEpisodes = watchedEpisodes,
        watchedVolumes = watchedVolumes,
        isPrivate = isPrivate,
        rating = rating,
        remark = remark,
        createTime = 1L,
        updateTime = updateTime,
    )

    @Test
    fun mergeCollections_takesMaxProgressAndNewerRowFields() {
        // 远端更新（评分 9）+ 本地卷进度 15：既拿远端评分，也不丢本地卷进度
        val local = collection(updateTime = 1_000L, watchedVolumes = 15, watchedEpisodes = 5, rating = 6f)
        val remote = collection(updateTime = 2_000L, watchedVolumes = 20, watchedEpisodes = 4, rating = 9f)

        val merged = SyncMergePolicy.mergeCollections(listOf(local), listOf(remote)).single()

        assertEquals(9f, merged.rating) // 其余字段取基准行（远端）
        assertEquals(5, merged.watchedEpisodes) // 集数取 MAX，不是基准行的 4
        assertEquals(20, merged.watchedVolumes) // 卷进度取 MAX
    }

    @Test
    fun mergeCollections_missingRemoteKeyKeepsLocalVolumes() {
        // 旧客户端回传的行没有 watchedVolumes（解析为 null），不能把本地 12 卷抹掉
        val local = collection(updateTime = 1_000L, watchedVolumes = 12)
        val remote = collection(updateTime = 2_000L, watchedVolumes = null)

        val merged = SyncMergePolicy.mergeCollections(listOf(local), listOf(remote)).single()

        assertEquals(12, merged.watchedVolumes)
    }

    @Test
    fun mergeCollections_unionOfPrivateFlag() {
        // A-D8：本地私密 + 远端非私密 → 仍私密（远端无法取消本地私密）
        val localPrivate = SyncMergePolicy.mergeCollections(
            listOf(collection(updateTime = 1_000L, isPrivate = true)),
            listOf(collection(updateTime = 2_000L, isPrivate = false)),
        ).single()
        assertTrue(localPrivate.isPrivate)

        // 本地非私密 + 远端私密 → 私密（并集）
        val remotePrivate = SyncMergePolicy.mergeCollections(
            listOf(collection(updateTime = 1_000L, isPrivate = false)),
            listOf(collection(updateTime = 2_000L, isPrivate = true)),
        ).single()
        assertTrue(remotePrivate.isPrivate)

        // 两侧都非私密 → 非私密
        val publicRow = SyncMergePolicy.mergeCollections(
            listOf(collection(updateTime = 1_000L)),
            listOf(collection(updateTime = 2_000L)),
        ).single()
        assertFalse(publicRow.isPrivate)
    }

    @Test
    fun mergeCollections_keepsRowsOnlyOnOneSide() {
        val merged = SyncMergePolicy.mergeCollections(
            listOf(collection(subjectId = 1L, watchedVolumes = 3)),
            listOf(collection(subjectId = 2L, watchedVolumes = 7)),
        )
        assertEquals(setOf(1L, 2L), merged.map { it.subjectId }.toSet())
        assertEquals(3, merged.first { it.subjectId == 1L }.watchedVolumes)
        assertEquals(7, merged.first { it.subjectId == 2L }.watchedVolumes)
    }
}