package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TTL + LRU 缓存单元测试（详情页扩展数据的短时缓存依赖它）。
 */
class TtlCacheTest {

    private var now = 1_000_000L

    private fun cache(ttlMs: Long = 1_000L, max: Int = 3) = TtlCache<String, String>(ttlMs, max) { now }

    @Test
    fun hitWithinTtl() {
        val c = cache()
        c.put("a", "1")
        now += 999
        assertEquals("1", c.get("a"))
    }

    @Test
    fun missAfterTtl() {
        val c = cache()
        c.put("a", "1")
        now += 1_000
        assertNull(c.get("a"))
    }

    @Test
    fun expiredEntryIsEvicted() {
        val c = cache()
        c.put("a", "1")
        now += 1_000
        c.get("a")
        assertEquals(0, c.size())
    }

    @Test
    fun evictsLeastRecentlyUsed() {
        val c = cache(ttlMs = 10_000L, max = 2)
        c.put("a", "1")
        c.put("b", "2")
        c.get("a")       // a 变成最近使用
        c.put("c", "3")  // 淘汰 b
        assertEquals("1", c.get("a"))
        assertEquals("3", c.get("c"))
        assertNull(c.get("b"))
    }

    @Test
    fun invalidateRemovesEntry() {
        val c = cache()
        c.put("a", "1")
        c.invalidate("a")
        assertNull(c.get("a"))
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(cache().get("nope"))
    }
}
