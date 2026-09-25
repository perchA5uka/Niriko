package com.otakup.niriko.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 搜索失败语义与榜单缓存指纹（第 6 轮 F5 / R4）。
 *
 * 这两条都是「用户能直接感知的 bug」：
 * - 失败被吞成空列表 → 断网显示成「未找到相关作品」；
 * - 榜单缓存 key 只有 type-offset → 切筛选/R18 命中旧榜单。
 */
class SubjectSearchOutcomeTest {

    @Test
    fun networkMessagesDistinguishCause() {
        assertEquals("与信息源断开连接", networkFailureMessage(SocketTimeoutException("timeout")))
        assertEquals("与网络断开连接", networkFailureMessage(UnknownHostException("dns")))
        assertEquals("与信息源断开连接", networkFailureMessage(ConnectException("refused")))
        assertEquals("网络请求失败，请重试", networkFailureMessage(IllegalStateException("boom")))
    }

    @Test
    fun outcomeDefaultsMeanSuccess() {
        val outcome = SearchOutcome(results = emptyList(), total = 0)
        assertFalse(outcome.remoteFailed)
        assertFalse(outcome.offline)
        assertNull(outcome.failureReason)
    }

    @Test
    fun rankingCacheKeyWithoutFingerprintKeepsLegacyShape() {
        assertEquals("2-0", rankingCacheKey(type = 2, offset = 0, fingerprint = ""))
        assertEquals("4-20", rankingCacheKey(type = 4, offset = 20, fingerprint = ""))
    }

    @Test
    fun rankingCacheKeyIncludesFingerprint() {
        val a = rankingCacheKey(2, 0, "nsfw=false;sort=rank")
        val b = rankingCacheKey(2, 0, "nsfw=true;sort=rank")
        val c = rankingCacheKey(2, 20, "nsfw=false;sort=rank")
        assertNotEquals("切 R18 不能命中旧榜单", a, b)
        assertNotEquals("不同 offset 必须是不同的 key", a, c)
        assertEquals(a, rankingCacheKey(2, 0, "nsfw=false;sort=rank"))
    }
}
