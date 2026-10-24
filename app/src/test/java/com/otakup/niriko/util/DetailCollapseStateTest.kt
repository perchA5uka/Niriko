package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 折叠状态编解码的纯规则测试（C · 6.15）。
 *
 * 这段串会直接进 DataStore 并随备份走，所以三件事必须成立：
 * 同一份集合编码稳定（便于去重）、脏数据不抛异常、不同作品的折叠互不串味。
 */
class DetailCollapseStateTest {

    @Test
    fun `编码稳定_与输入顺序无关`() {
        val a = DetailCollapseState.encode(mapOf(2L to setOf("vndb", "steam"), 1L to setOf("anilist")))
        val b = DetailCollapseState.encode(mapOf(1L to setOf("anilist"), 2L to setOf("vndb", "steam")))
        assertEquals(a, b)
        assertEquals("1:anilist;2:steam,vndb", a)
    }

    @Test
    fun `往返一致且忽略空集合`() {
        val encoded = DetailCollapseState.encode(mapOf(7L to setOf("steam"), 8L to emptySet()))
        assertEquals("7:steam", encoded)
        assertEquals(mapOf(7L to setOf("steam")), DetailCollapseState.decode(encoded))
        assertEquals(emptyMap<Long, Set<String>>(), DetailCollapseState.decode(""))
        assertEquals(emptyMap<Long, Set<String>>(), DetailCollapseState.decode(null))
    }

    @Test
    fun `脏数据不抛异常_只丢坏行`() {
        val decoded = DetailCollapseState.decode("abc:steam;12:;:x;13:a|b;14:anilist")
        // 12 有 id 无 key、13 的 key 里含分隔符会被当普通字符保留、abc 非数字被丢
        assertEquals(setOf("a|b"), decoded[13L])
        assertEquals(setOf("anilist"), decoded[14L])
        assertFalse(decoded.containsKey(12L))
    }

    @Test
    fun `toggle 增加与移除并清理空集合`() {
        var encoded = DetailCollapseState.encode(emptyMap())
        encoded = DetailCollapseState.toggle(encoded, 42L, "steam")
        assertTrue(DetailCollapseState.isCollapsed(encoded, 42L, "steam"))
        encoded = DetailCollapseState.toggle(encoded, 42L, "vndb")
        assertEquals(setOf("steam", "vndb"), DetailCollapseState.collapsedSections(encoded, 42L))
        encoded = DetailCollapseState.toggle(encoded, 42L, "steam")
        assertEquals(setOf("vndb"), DetailCollapseState.collapsedSections(encoded, 42L))
        encoded = DetailCollapseState.toggle(encoded, 42L, "vndb")
        // 全部移除后该作品不应留下 "42:" 这种空行
        assertEquals("", encoded)
    }

    @Test
    fun `不同作品互不影响`() {
        var encoded = DetailCollapseState.toggle(null, 1L, "steam")
        encoded = DetailCollapseState.toggle(encoded, 2L, "vndb")
        assertTrue(DetailCollapseState.isCollapsed(encoded, 1L, "steam"))
        assertFalse(DetailCollapseState.isCollapsed(encoded, 1L, "vndb"))
        assertTrue(DetailCollapseState.isCollapsed(encoded, 2L, "vndb"))
    }

    @Test
    fun `clear 只清指定作品`() {
        var encoded = DetailCollapseState.toggle(null, 1L, "steam")
        encoded = DetailCollapseState.toggle(encoded, 2L, "vndb")
        val cleared = DetailCollapseState.clear(encoded, 1L)
        assertEquals(emptySet<String>(), DetailCollapseState.collapsedSections(cleared, 1L))
        assertEquals(setOf("vndb"), DetailCollapseState.collapsedSections(cleared, 2L))
    }
}
