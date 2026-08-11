package com.otakup.niriko.data.sync.bangumi

import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.remote.bangumi.dto.UserCollectionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 合并计划纯函数测试(参照 Kazumi CollectSyncMerger.planBangumi 的用例矩阵):
 * 空 / 仅远程 / 仅本地 / 冲突 + 双优先级。
 */
class BangumiSyncPlannerTest {

    private fun local(subjectId: Long, status: WatchStatus) = CollectionEntity(
        subjectId = subjectId,
        status = status,
    )

    private fun remote(subjectId: Long, type: Int) = UserCollectionDto(
        subjectId = subjectId,
        type = type,
        subject = null,
    )

    @Test
    fun `双边为空 → 空计划`() {
        val plan = BangumiSyncPlanner.plan(emptyList(), emptyList(), BangumiSyncPriority.LOCAL_FIRST)
        assertTrue(plan.localOnly.isEmpty())
        assertTrue(plan.remoteOnly.isEmpty())
        assertTrue(plan.conflict.isEmpty())
        assertEquals(0, plan.match)
        assertEquals(0, plan.totalOperations)
    }

    @Test
    fun `仅远程 → 全部 remoteOnly 且换算本地状态`() {
        val plan = BangumiSyncPlanner.plan(
            local = emptyList(),
            remote = listOf(remote(101, 2), remote(102, 3)),
            priority = BangumiSyncPriority.LOCAL_FIRST,
        )
        assertEquals(listOf(101L, 102L), plan.remoteOnly.map { it.subjectId })
        assertEquals(WatchStatus.COMPLETED, plan.remoteOnly[0].status)
        assertEquals(WatchStatus.WATCHING, plan.remoteOnly[1].status)
        assertTrue(plan.localOnly.isEmpty() && plan.conflict.isEmpty())
    }

    @Test
    fun `仅本地 → 全部 localOnly 上传`() {
        val plan = BangumiSyncPlanner.plan(
            local = listOf(local(201, WatchStatus.PLAN_TO_WATCH), local(202, WatchStatus.DROPPED)),
            remote = emptyList(),
            priority = BangumiSyncPriority.LOCAL_FIRST,
        )
        assertEquals(listOf(201L, 202L), plan.localOnly.map { it.subjectId })
        assertEquals(listOf(1, 5), plan.localOnly.map { it.type })
        assertTrue(plan.remoteOnly.isEmpty() && plan.conflict.isEmpty())
    }

    @Test
    fun `状态一致 → match 不产生操作`() {
        val plan = BangumiSyncPlanner.plan(
            local = listOf(local(301, WatchStatus.WATCHING)),
            remote = listOf(remote(301, 3)),
            priority = BangumiSyncPriority.LOCAL_FIRST,
        )
        assertEquals(1, plan.match)
        assertEquals(0, plan.totalOperations)
    }

    @Test
    fun `冲突 localFirst → conflict 含双状态且按本地上传`() {
        val plan = BangumiSyncPlanner.plan(
            local = listOf(local(401, WatchStatus.ON_HOLD)),
            remote = listOf(remote(401, 2)),
            priority = BangumiSyncPriority.LOCAL_FIRST,
        )
        assertEquals(1, plan.conflict.size)
        val c = plan.conflict.first()
        assertEquals(WatchStatus.ON_HOLD, c.localStatus)
        assertEquals(WatchStatus.COMPLETED, c.remoteStatus)
        assertEquals(BangumiSyncPriority.LOCAL_FIRST, BangumiSyncPriority.LOCAL_FIRST)
    }

    @Test
    fun `冲突 bangumiFirst → 同样产生 conflict 条目`() {
        val plan = BangumiSyncPlanner.plan(
            local = listOf(local(402, WatchStatus.PLAN_TO_WATCH)),
            remote = listOf(remote(402, 5)),
            priority = BangumiSyncPriority.BANGUMI_FIRST,
        )
        assertEquals(1, plan.conflict.size)
        assertEquals(WatchStatus.DROPPED, plan.conflict.first().remoteStatus)
    }

    @Test
    fun `混合场景全部归位`() {
        val plan = BangumiSyncPlanner.plan(
            local = listOf(
                local(1, WatchStatus.WATCHING),   // 一致(远程 3)
                local(2, WatchStatus.DROPPED),      // 冲突(远程 1)
                local(3, WatchStatus.COMPLETED),    // 仅本地
            ),
            remote = listOf(
                remote(1, 3),
                remote(2, 1),
                remote(4, 2),                        // 仅远程
            ),
            priority = BangumiSyncPriority.LOCAL_FIRST,
        )
        assertEquals(1, plan.match)
        assertEquals(listOf(3L), plan.localOnly.map { it.subjectId })
        assertEquals(listOf(4L), plan.remoteOnly.map { it.subjectId })
        assertEquals(listOf(2L), plan.conflict.map { it.subjectId })
        assertEquals(3, plan.totalOperations)
    }

    @Test
    fun `远程未知状态被跳过不落库`() {
        val plan = BangumiSyncPlanner.plan(
            local = emptyList(),
            remote = listOf(remote(500, 99), remote(501, 2)),
            priority = BangumiSyncPriority.LOCAL_FIRST,
        )
        assertEquals(listOf(501L), plan.remoteOnly.map { it.subjectId })
    }
}