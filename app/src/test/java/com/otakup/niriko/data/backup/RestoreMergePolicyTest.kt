package com.otakup.niriko.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 恢复合并策略单元测试（计划 B2-4，用户确认：冲突则本地优先）。
 */
class RestoreMergePolicyTest {

    private data class Row(val id: Long, val value: String)

    private fun row(id: Long, value: String = "v$id") = Row(id, value)

    private val keyOf: (Row) -> Long = { it.id }

    @Test
    fun emptyLocal_keepsEverything() {
        val backup = listOf(row(1), row(2))
        assertEquals(backup, RestoreMergePolicy.keepOnlyNew(backup, emptySet<Long>(), keyOf))
    }

    @Test
    fun emptyBackup_returnsEmpty() {
        assertEquals(emptyList<Row>(), RestoreMergePolicy.keepOnlyNew(emptyList(), setOf(1L), keyOf))
    }

    @Test
    fun existingLocalRecords_areDropped() {
        val backup = listOf(row(1, "backup"), row(2, "backup"))
        val merged = RestoreMergePolicy.keepOnlyNew(backup, setOf(1L), keyOf)
        assertEquals(listOf(row(2, "backup")), merged)
    }

    @Test
    fun allConflicting_dropsAll() {
        val backup = listOf(row(1), row(2))
        assertEquals(emptyList<Row>(), RestoreMergePolicy.keepOnlyNew(backup, setOf(1L, 2L), keyOf))
    }

    @Test
    fun noneConflicting_keepsAll() {
        val backup = listOf(row(1), row(2))
        assertEquals(backup, RestoreMergePolicy.keepOnlyNew(backup, setOf(9L), keyOf))
    }

    @Test
    fun compositeKey_matchesOnWholeKey() {
        val backup = listOf("1" to "tmdb", "1" to "vndb", "2" to "tmdb")
        val merged = RestoreMergePolicy.keepOnlyNew(backup, setOf("1" to "tmdb")) { it.first to it.second }
        assertEquals(listOf("1" to "vndb", "2" to "tmdb"), merged)
    }

    @Test
    fun duplicateKeysInsideBackup_areKeptAsIs() {
        // 策略只负责「本地优先」，备份内部的重复交给数据库主键约束
        val backup = listOf(row(5), row(5))
        assertEquals(backup, RestoreMergePolicy.keepOnlyNew(backup, setOf(1L), keyOf))
    }
}
