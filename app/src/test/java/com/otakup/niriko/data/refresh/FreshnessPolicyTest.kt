package com.otakup.niriko.data.refresh

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 新鲜度判定与退避梯单元测试。
 *
 * 这两块是刷新机制的地基：判错了会导致「该刷不刷」（用户看到陈旧数据）或
 * 「不该刷狂刷」（打满接口）。因此用纯函数 + 注入时钟覆盖边界。
 */
class FreshnessPolicyTest {

    private val now = 1_700_000_000_000L

    private fun snapshot(ageMs: Long, backoffUntil: Long = 0L) = FreshnessSnapshot(
        lastSuccessAt = if (ageMs == Long.MAX_VALUE) 0L else now - ageMs,
        lastAttemptAt = now - ageMs.coerceAtMost(now),
        backoffUntil = backoffUntil,
    )

    // ---------- decide ----------

    @Test
    fun neverSucceeded_isExpired() {
        assertEquals(
            RefreshDecision.EXPIRED,
            FreshnessDecider.decide(RefreshResource.TRENDING, null, now),
        )
    }

    @Test
    fun withinSoftTtl_isFresh() {
        val snap = snapshot(ageMs = 60_000L) // 1 分钟 < 5 分钟软 TTL
        assertEquals(
            RefreshDecision.FRESH,
            FreshnessDecider.decide(RefreshResource.TRENDING, snap, now),
        )
    }

    @Test
    fun betweenSoftAndHard_isRevalidate() {
        val snap = snapshot(ageMs = 10 * 60_000L) // 10 分钟：5min < age < 30min
        assertEquals(
            RefreshDecision.REVALIDATE,
            FreshnessDecider.decide(RefreshResource.TRENDING, snap, now),
        )
    }

    @Test
    fun beyondHardTtl_isExpired() {
        val snap = snapshot(ageMs = 60 * 60_000L) // 1 小时 > 30 分钟硬 TTL
        assertEquals(
            RefreshDecision.EXPIRED,
            FreshnessDecider.decide(RefreshResource.TRENDING, snap, now),
        )
    }

    @Test
    fun inBackoffWindow_isBackoff() {
        val snap = snapshot(ageMs = 60 * 60_000L, backoffUntil = now + 30_000L)
        assertEquals(
            RefreshDecision.BACKOFF,
            FreshnessDecider.decide(RefreshResource.TRENDING, snap, now),
        )
    }

    @Test
    fun forceBypassesSoftTtlAndBackoff() {
        val fresh = snapshot(ageMs = 1_000L)
        assertEquals(
            RefreshDecision.EXPIRED,
            FreshnessDecider.decide(RefreshResource.TRENDING, fresh, now, force = true),
        )
        val backedOff = snapshot(ageMs = 1_000L, backoffUntil = now + 600_000L)
        assertEquals(
            RefreshDecision.EXPIRED,
            FreshnessDecider.decide(RefreshResource.TRENDING, backedOff, now, force = true),
        )
    }

    @Test
    fun detailSoftTtlZero_alwaysRevalidates() {
        // 作品详情 soft=0：即便刚刷过也要再校验（保持「进页面即后台刷新」的既有体感）
        val justNow = snapshot(ageMs = 1L)
        assertEquals(
            RefreshDecision.REVALIDATE,
            FreshnessDecider.decide(RefreshResource.SUBJECT_DETAIL, justNow, now),
        )
    }

    @Test
    fun searchResource_neverFresh() {
        val justNow = snapshot(ageMs = 1L)
        assertEquals(
            RefreshDecision.EXPIRED,
            FreshnessDecider.decide(RefreshResource.SEARCH, justNow, now),
        )
    }

    // ---------- decideByTimestamp（逐行时间戳：详情 / 剧集） ----------

    @Test
    fun byTimestamp_zeroMeansExpired() {
        assertEquals(
            RefreshDecision.EXPIRED,
            FreshnessDecider.decideByTimestamp(RefreshResource.EPISODES, 0L, now),
        )
    }

    @Test
    fun byTimestamp_respectsEpisodesTtl() {
        // 剧集软 TTL 6 小时：修复改造前「一旦落库永不过期」的问题
        assertEquals(
            RefreshDecision.FRESH,
            FreshnessDecider.decideByTimestamp(RefreshResource.EPISODES, now - 60_000L, now),
        )
        assertEquals(
            RefreshDecision.REVALIDATE,
            FreshnessDecider.decideByTimestamp(RefreshResource.EPISODES, now - 12 * 3_600_000L, now),
        )
        assertEquals(
            RefreshDecision.EXPIRED,
            FreshnessDecider.decideByTimestamp(RefreshResource.EPISODES, now - 30L * 24 * 3_600_000L, now),
        )
    }

    @Test
    fun byTimestamp_forceAlwaysExpired() {
        assertEquals(
            RefreshDecision.EXPIRED,
            FreshnessDecider.decideByTimestamp(RefreshResource.EPISODES, now - 1L, now, force = true),
        )
    }

    // ---------- 退避梯 ----------

    @Test
    fun retryLadder_followsAniShelfIntervals() {
        assertEquals(0L, RetryLadder.backoffMs(0))
        assertEquals(30_000L, RetryLadder.backoffMs(1))
        assertEquals(60_000L, RetryLadder.backoffMs(2))
        assertEquals(120_000L, RetryLadder.backoffMs(3))
        assertEquals(300_000L, RetryLadder.backoffMs(4))
        // 第 5 次及以后固定 300s（不会无限增长）
        assertEquals(300_000L, RetryLadder.backoffMs(5))
        assertEquals(300_000L, RetryLadder.backoffMs(99))
    }

    // ---------- 状态迁移 ----------

    @Test
    fun afterSuccess_clearsBackoffAndFailures() {
        val failed = FreshnessSnapshot(failureCount = 3, backoffUntil = now + 300_000L, lastError = "boom")
        val ok = failed.afterSuccess(now)
        assertEquals(now, ok.lastSuccessAt)
        assertEquals(0, ok.failureCount)
        assertEquals(0L, ok.backoffUntil)
        assertEquals(null, ok.lastError)
    }

    @Test
    fun afterFailure_accumulatesAndSchedulesBackoff() {
        val first = FreshnessSnapshot().afterFailure(now, "e1")
        assertEquals(1, first.failureCount)
        assertEquals(now + 30_000L, first.backoffUntil)
        val second = first.afterFailure(now, "e2")
        assertEquals(2, second.failureCount)
        assertEquals(now + 60_000L, second.backoffUntil)
    }
}
