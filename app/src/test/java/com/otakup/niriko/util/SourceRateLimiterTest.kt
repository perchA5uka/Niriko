package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按源限流闸门单元测试（计划 B4 · 4-12：IGDB 4 req/s、MusicBrainz 1 req/s、VNDB 200/5min）。
 */
class SourceRateLimiterTest {

    private val limiter = SourceRateLimiter()

    @Test
    fun unknownHost_neverWaits() {
        repeat(50) { assertEquals(0L, limiter.acquire("example.com", 0L)) }
    }

    @Test
    fun igdb_allowsFourPerSecond() {
        // 窗口内未满 → 突发放行；满了之后按 250ms 匀速排队
        repeat(4) { assertEquals(0L, limiter.acquire("api.igdb.com", 0L)) }
        assertEquals(250L, limiter.acquire("api.igdb.com", 0L))
        assertEquals(500L, limiter.acquire("api.igdb.com", 0L))
    }

    @Test
    fun igdb_windowSlidesAfterOneSecond() {
        repeat(4) { limiter.acquire("api.igdb.com", 0L) }
        // 1 秒后窗口清空，恢复放行
        assertEquals(0L, limiter.acquire("api.igdb.com", 1_000L))
    }

    @Test
    fun musicBrainz_allowsOnePerSecond() {
        assertEquals(0L, limiter.acquire("musicbrainz.org", 0L))
        // t=500 时再请求：要等到 t=1000 才够 1 秒
        assertEquals(500L, limiter.acquire("musicbrainz.org", 500L))
    }

    @Test
    fun subdomainsMatchRule() {
        assertEquals(0L, limiter.acquire("www.musicbrainz.org", 0L))
        assertTrue(limiter.acquire("www.musicbrainz.org", 0L) > 0L)
    }

    @Test
    fun vndb_allowsTwoHundredPerFiveMinutes() {
        repeat(200) { assertEquals(0L, limiter.acquire("api.vndb.org", 0L)) }
        // 配额用尽后间隔 = 300000 / 200 = 1500ms
        assertEquals(1_500L, limiter.acquire("api.vndb.org", 0L))
        assertEquals(3_000L, limiter.acquire("api.vndb.org", 0L))
    }

    @Test
    fun concurrentCallsQueueInsteadOfBursting() {
        // 第二条在第一条之后排队：等待时间随队列增长，而不是都返回 1000
        assertEquals(0L, limiter.acquire("musicbrainz.org", 0L))
        val first = limiter.acquire("musicbrainz.org", 0L)
        val second = limiter.acquire("musicbrainz.org", 0L)
        assertEquals(1_000L, first)
        assertEquals(2_000L, second)
    }

    @Test
    fun rulesAreConfigurable() {
        val custom = SourceRateLimiter(
            listOf(SourceRateLimiter.RateRule("api.test.dev", maxRequests = 2, windowMs = 500L)),
        )
        assertEquals(0L, custom.acquire("api.test.dev", 0L))
        assertEquals(0L, custom.acquire("api.test.dev", 0L))
        assertEquals(250L, custom.acquire("api.test.dev", 0L))
    }
}
