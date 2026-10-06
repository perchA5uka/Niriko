package com.otakup.niriko.data.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PagePagerTest {

    @Test
    fun `未取满 total 时继续翻页`() {
        assertEquals(50, PagePager.nextOffset(loaded = 50, total = 300))
    }

    @Test
    fun `取满 total 时停止`() {
        assertNull(PagePager.nextOffset(loaded = 300, total = 300))
        assertNull(PagePager.nextOffset(loaded = 320, total = 300))
    }

    @Test
    fun `服务端没给 total 时继续翻（靠空页收敛）`() {
        assertEquals(50, PagePager.nextOffset(loaded = 50, total = 0))
        assertEquals(100, PagePager.nextOffset(loaded = 100, total = -1))
    }

    @Test
    fun `一条都没拿到就停止`() {
        assertNull(PagePager.nextOffset(loaded = 0, total = 300))
    }

    @Test
    fun `到达安全上限后停止`() {
        assertNull(PagePager.nextOffset(loaded = PagePager.MAX_ITEMS, total = 10_000))
        assertEquals(PagePager.MAX_ITEMS - 1, PagePager.nextOffset(loaded = PagePager.MAX_ITEMS - 1, total = 10_000))
    }
}
