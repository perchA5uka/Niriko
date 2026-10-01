package com.otakup.niriko.data.sync

import org.junit.Assert.assertEquals
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
}
