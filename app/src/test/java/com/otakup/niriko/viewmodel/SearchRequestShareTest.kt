package com.otakup.niriko.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * 关键词请求合并单测（第 6 轮 F4）。
 *
 * 建议（120ms 防抖）与正式搜索（300ms 防抖）打的是同一个关键词、同一组筛选条件：
 * 改造前它们各打一遍网络，建议那条还在 2.5s 后被掐断 → 建议永远为空。
 * 现在共用同一个请求（并发合并 + 短窗口复用）。
 */
class SearchRequestShareTest {

    @Test
    fun concurrentSameKeyRunsOnce() = runBlocking {
        val now = 0L
        val share = SearchRequestShare(ttlMs = 5_000L, clock = { now })
        val runs = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val jobs = (1..6).map {
            launch(Dispatchers.Default) {
                share.run("kw:t2") {
                    runs.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    SearchAttempt(total = 1)
                }
            }
        }
        started.await()
        delay(100)
        release.complete(Unit)
        jobs.forEach { it.join() }

        assertEquals("同一 key 的并发请求必须合并成一次", 1, runs.get())
    }

    @Test
    fun completedResultIsReusedWithinTtl() = runBlocking {
        var now = 1_000L
        val share = SearchRequestShare(ttlMs = 5_000L, clock = { now })
        val runs = AtomicInteger(0)

        share.run("kw:t2") { runs.incrementAndGet(); SearchAttempt(total = 1) }
        now += 1_000L // 正式搜索在建议之后 180ms 才发起：必须命中同一个结果
        val second = share.run("kw:t2") { runs.incrementAndGet(); SearchAttempt(total = 2) }

        assertEquals(1, runs.get())
        assertEquals(1, second.total)
    }

    @Test
    fun expiredResultIsRefetched() = runBlocking {
        var now = 1_000L
        val share = SearchRequestShare(ttlMs = 5_000L, clock = { now })
        val runs = AtomicInteger(0)

        share.run("kw:t2") { runs.incrementAndGet(); SearchAttempt(total = 1) }
        now += 6_000L
        share.run("kw:t2") { runs.incrementAndGet(); SearchAttempt(total = 2) }

        assertEquals(2, runs.get())
    }

    @Test
    fun differentKeysRunIndependently() = runBlocking {
        val now = 0L
        val share = SearchRequestShare(clock = { now })
        val runs = AtomicInteger(0)
        share.run("kw:t2") { runs.incrementAndGet(); SearchAttempt() }
        share.run("kw:t1") { runs.incrementAndGet(); SearchAttempt() }
        assertEquals(2, runs.get())
    }

    // ==================== 请求指纹 ====================

    @Test
    fun fingerprintIsStableForSameParams() {
        val a = keywordShareKey("巨人", 2, listOf("日本", "TV"), false, "heat", null, 100)
        val b = keywordShareKey("巨人", 2, listOf("TV", "日本"), false, "heat", null, 100)
        assertEquals("标签顺序不应影响指纹", a, b)
    }

    @Test
    fun fingerprintChangesWithQueryTypeNsfwSortAndLimit() {
        val base = keywordShareKey("巨人", 2, null, false, "heat", null, 100)
        assertNotEquals(base, keywordShareKey("巨人的進擊", 2, null, false, "heat", null, 100))
        assertNotEquals(base, keywordShareKey("巨人", 1, null, false, "heat", null, 100))
        assertNotEquals(base, keywordShareKey("巨人", 2, null, true, "heat", null, 100))
        assertNotEquals(base, keywordShareKey("巨人", 2, null, false, "rank", null, 100))
        assertNotEquals(base, keywordShareKey("巨人", 2, null, false, "heat", listOf(">0"), 100))
        assertNotEquals(base, keywordShareKey("巨人", 2, null, false, "heat", null, 50))
    }

    @Test
    fun fingerprintTrimsQuery() {
        assertEquals(
            keywordShareKey("巨人", 2, null, false, "heat", null, 100),
            keywordShareKey("  巨人  ", 2, null, false, "heat", null, 100),
        )
        assertTrue(keywordShareKey("巨人", 2, null, false, "heat", null, 100).startsWith("kw:巨人"))
    }
}
