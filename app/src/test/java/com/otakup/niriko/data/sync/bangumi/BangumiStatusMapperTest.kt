package com.otakup.niriko.data.sync.bangumi

import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.sync.bangumi.BangumiStatusMapper.toBangumiType
import com.otakup.niriko.data.sync.bangumi.BangumiStatusMapper.toWatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WatchStatus ↔ Bangumi 官方 CollectionType 双向映射测试。
 * 覆盖计划 2.2 全部 5 组正向映射 + 非法值回退(Kazumi collect_type_mapper 的已知坑)。
 */
class BangumiStatusMapperTest {

    @Test
    fun `本地到官方 5 组映射正确`() {
        assertEquals(1, WatchStatus.PLAN_TO_WATCH.toBangumiType())
        assertEquals(2, WatchStatus.COMPLETED.toBangumiType())
        assertEquals(3, WatchStatus.WATCHING.toBangumiType())
        assertEquals(4, WatchStatus.ON_HOLD.toBangumiType())
        assertEquals(5, WatchStatus.DROPPED.toBangumiType())
    }

    @Test
    fun `官方到本地 5 组映射正确`() {
        assertEquals(WatchStatus.PLAN_TO_WATCH, 1.toWatchStatus())
        assertEquals(WatchStatus.COMPLETED, 2.toWatchStatus())
        assertEquals(WatchStatus.WATCHING, 3.toWatchStatus())
        assertEquals(WatchStatus.ON_HOLD, 4.toWatchStatus())
        assertEquals(WatchStatus.DROPPED, 5.toWatchStatus())
    }

    @Test
    fun `非法官方值返回 null 而非误映射`() {
        assertNull(0.toWatchStatus())
        assertNull(6.toWatchStatus())
        assertNull(99.toWatchStatus())
    }

    @Test
    fun `双向往返恒等`() {
        for (status in WatchStatus.entries) {
            assertEquals(status, status.toBangumiType().toWatchStatus())
        }
    }
}