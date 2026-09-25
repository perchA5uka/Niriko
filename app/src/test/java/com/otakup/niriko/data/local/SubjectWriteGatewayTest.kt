package com.otakup.niriko.data.local

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 写入网关 diff 逻辑单元测试。
 *
 * 这是「缓存写放大」的直接解药：搜索/榜单/放送日历反复返回同一份数据时，
 * 第二次必须是**零写入**，否则每一次写都会让作品库列表整体重算。
 */
class SubjectWriteGatewayTest {

    private fun subject(
        id: Long,
        title: String = "作品$id",
        rating: Float? = 8.0f,
        lastSyncTime: Long = 0L,
    ) = SubjectEntity(
        subjectId = id,
        title = title,
        titleCN = title,
        type = SubjectType.ANIME,
        ratingScore = rating,
        lastSyncTime = lastSyncTime,
    )

    @Test
    fun brandNewRows_areAllWritten() {
        val incoming = listOf(subject(1), subject(2))
        val writes = SubjectWriteGateway.diffWrites(existing = emptyMap(), incoming = incoming)
        assertEquals(2, writes.size)
    }

    @Test
    fun identicalRows_areSkipped() {
        val existing = mapOf(1L to subject(1), 2L to subject(2))
        val incoming = listOf(subject(1), subject(2))
        val writes = SubjectWriteGateway.diffWrites(existing, incoming)
        assertTrue("内容完全一致时不应产生任何写入", writes.isEmpty())
    }

    @Test
    fun onlyLastSyncTimeDiffers_isStillSkipped() {
        // 关键：lastSyncTime 不参与 diff。否则每次刷新都会推进时间戳 → 永远判定为「变了」
        val existing = mapOf(1L to subject(1, lastSyncTime = 1_000L))
        val incoming = listOf(subject(1, lastSyncTime = 9_999_999L))
        val writes = SubjectWriteGateway.diffWrites(existing, incoming)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun changedField_isWritten() {
        val existing = mapOf(1L to subject(1, rating = 8.0f))
        val incoming = listOf(subject(1, rating = 8.6f))
        val writes = SubjectWriteGateway.diffWrites(existing, incoming)
        assertEquals(1, writes.size)
        assertEquals(8.6f, writes.first().ratingScore)
    }

    @Test
    fun mixedBatch_writesOnlyChangedOnes() {
        val existing = mapOf(
            1L to subject(1, rating = 8.0f),
            2L to subject(2, rating = 7.0f),
            3L to subject(3, rating = 6.0f),
        )
        val incoming = listOf(
            subject(1, rating = 8.0f), // 未变
            subject(2, rating = 7.5f), // 变了
            subject(4),                // 新增
        )
        val writes = SubjectWriteGateway.diffWrites(existing, incoming)
        assertEquals(setOf(2L, 4L), writes.map { it.subjectId }.toSet())
    }

    @Test
    fun contentEquals_ignoresLastSyncTimeOnly() {
        assertTrue(SubjectWriteGateway.contentEquals(subject(1, lastSyncTime = 1L), subject(1, lastSyncTime = 2L)))
        assertFalse(SubjectWriteGateway.contentEquals(subject(1, title = "A"), subject(1, title = "B")))
    }
}
