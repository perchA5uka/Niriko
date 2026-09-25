package com.otakup.niriko.data.refresh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 展示文案纯函数单元测试（诊断页与「上次更新」标签共用）。
 */
class RefreshStatusLabelsTest {

    private val now = 1_700_000_000_000L

    @Test
    fun knownKeysHaveFriendlyNames() {
        assertEquals("Steam 排行榜", RefreshStatusLabels.label(RefreshKeys.STEAM_CHART))
        assertEquals("放送日历", RefreshStatusLabels.label(RefreshKeys.BROADCAST_CALENDAR))
    }

    @Test
    fun trendingKeysAreDescribedByModeAndType() {
        val key = "trending:SEASONAL:2:false:heat"
        val label = RefreshStatusLabels.label(key)
        assertTrue(label, label.contains("发现页趋势"))
        assertTrue(label, label.contains("当季热门"))
        assertTrue(label, label.contains("类型 2"))
        // 未选中类型时不应出现多余后缀
        assertTrue(!RefreshStatusLabels.label("trending:ALL_TIME:all:false:rank").contains("类型"))
    }

    @Test
    fun unknownKeyFallsBackToRawKey() {
        assertEquals("something:else", RefreshStatusLabels.label("something:else"))
    }

    @Test
    fun formatAgoBuckets() {
        assertEquals("刚刚", RefreshStatusLabels.formatAgo(now - 10_000, now))
        assertEquals("5 分钟前", RefreshStatusLabels.formatAgo(now - 5 * 60_000, now))
        assertEquals("3 小时前", RefreshStatusLabels.formatAgo(now - 3 * 3_600_000L, now))
        assertEquals("2 天前", RefreshStatusLabels.formatAgo(now - 2 * 86_400_000L, now))
        assertEquals("从未", RefreshStatusLabels.formatAgo(0L, now))
    }

    @Test
    fun formatUntilRoundsUp() {
        assertEquals("马上", RefreshStatusLabels.formatUntil(now, now))
        assertEquals("马上", RefreshStatusLabels.formatUntil(now - 1_000, now))
        // 退避梯最短 30 秒：不足一分钟必须单独成档，否则会误报成「1 分钟后」
        assertEquals("不到 1 分钟", RefreshStatusLabels.formatUntil(now + 1_000, now))
        assertEquals("不到 1 分钟", RefreshStatusLabels.formatUntil(now + 30_000, now))
        assertEquals("1 分钟后", RefreshStatusLabels.formatUntil(now + 60_000, now))
        assertEquals("2 分钟后", RefreshStatusLabels.formatUntil(now + 61_000, now))
        assertEquals("5 分钟后", RefreshStatusLabels.formatUntil(now + 5 * 60_000, now))
        assertEquals("2 小时后", RefreshStatusLabels.formatUntil(now + 2 * 3_600_000L, now))
    }

    @Test
    fun describeNeverRefreshed() {
        assertEquals("尚未刷新", RefreshStatusLabels.describe(null, now))
        assertEquals("尚未刷新", RefreshStatusLabels.describe(FreshnessSnapshot(), now))
    }

    @Test
    fun describeReportsBackoff() {
        val snap = FreshnessSnapshot(
            lastSuccessAt = now - 3_600_000,
            failureCount = 2,
            backoffUntil = now + 5 * 60_000,
        )
        val text = RefreshStatusLabels.describe(snap, now)
        assertTrue(text, text.contains("上次 1 小时前"))
        assertTrue(text, text.contains("5 分钟后重试"))
    }

    @Test
    fun describeReportsAccumulatedFailuresAfterSuccess() {
        val snap = FreshnessSnapshot(lastSuccessAt = now - 60_000, failureCount = 3)
        val text = RefreshStatusLabels.describe(snap, now)
        assertTrue(text, text.contains("累计失败 3 次"))
    }

    @Test
    fun diagnoseOrderPutsProblemsFirst() {
        val backing = "b" to FreshnessSnapshot(lastSuccessAt = now - 1_000, backoffUntil = now + 60_000)
        val healthy = "a" to FreshnessSnapshot(lastSuccessAt = now - 1_000)
        val failed = "c" to FreshnessSnapshot(failureCount = 1)
        val sorted = listOf(healthy, failed, backing)
            .sortedWith { a, b -> RefreshStatusLabels.diagnoseOrder(a, b, now) }
        assertEquals(listOf("b", "c", "a"), sorted.map { it.first })
    }
}
