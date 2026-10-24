package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分享三卡的纯布局规则测试（C · 8.5/8.6/8.7）。
 *
 * 分页错一页 = 海报少一部作品；标记越界 = 刻度画到框外。这两类都靠这里拦住。
 */
class ShareCardLayoutPolicyTest {

    @Test
    fun `分区海报每页 12 部严格分页`() {
        assertEquals(0, ShareCardLayoutPolicy.pageCount(0))
        assertEquals(1, ShareCardLayoutPolicy.pageCount(1))
        assertEquals(1, ShareCardLayoutPolicy.pageCount(12))
        assertEquals(2, ShareCardLayoutPolicy.pageCount(13))
        assertEquals(3, ShareCardLayoutPolicy.pageCount(25))
        val pages = ShareCardLayoutPolicy.pages(25)
        assertEquals(listOf(0..11, 12..23, 24..24), pages)
        assertTrue(pages.all { it.last - it.first + 1 <= ShareCardLayoutPolicy.POSTER_PER_PAGE })
    }

    @Test
    fun `评分刻度位置夹在 0 到宽度之间`() {
        assertEquals(0f, ShareCardLayoutPolicy.scoreMarkerX(0f, 1000f), 0.01f)
        assertEquals(500f, ShareCardLayoutPolicy.scoreMarkerX(5f, 1000f), 0.01f)
        assertEquals(1000f, ShareCardLayoutPolicy.scoreMarkerX(10f, 1000f), 0.01f)
        assertEquals(0f, ShareCardLayoutPolicy.scoreMarkerX(-3f, 1000f), 0.01f)
        assertEquals(1000f, ShareCardLayoutPolicy.scoreMarkerX(99f, 1000f), 0.01f)
        assertEquals(0f, ShareCardLayoutPolicy.scoreMarkerX(5f, 0f), 0.01f)
    }

    @Test
    fun `对照文案覆盖缺失与持平`() {
        assertNull(ShareCardLayoutPolicy.deltaText(null, 8f))
        assertNull(ShareCardLayoutPolicy.deltaText(8f, null))
        assertEquals("和平均分持平", ShareCardLayoutPolicy.deltaText(8f, 8.02f))
        assertEquals("你比平均高 1.3 分", ShareCardLayoutPolicy.deltaText(9.3f, 8f))
        assertEquals("你比平均低 1.0 分", ShareCardLayoutPolicy.deltaText(7f, 8f))
    }

    @Test
    fun `空周不生成周报`() {
        assertFalse(ShareCardLayoutPolicy.shouldGenerateWeekly(0, 0, 0))
        assertTrue(ShareCardLayoutPolicy.shouldGenerateWeekly(1, 0, 0))
        assertTrue(ShareCardLayoutPolicy.shouldGenerateWeekly(0, 3, 0))
        assertTrue(ShareCardLayoutPolicy.shouldGenerateWeekly(0, 0, 1))
    }
}
