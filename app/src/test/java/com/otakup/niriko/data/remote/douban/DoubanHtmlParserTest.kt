package com.otakup.niriko.data.remote.douban

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubanHtmlParserTest {

    private val samplePage = """
        <div class="cover"><img src="https://img1.doubanio.com/view/photo/s_ratio_poster/public/p111.jpg" /></div>
        <div class="cover"><img src="//img2.doubanio.com/view/photo/s_ratio_poster/public/p222.jpg" /></div>
        <div class="cover"><img src="http://img3.doubanio.com/view/photo/s_ratio_poster/public/p333.jpg" /></div>
        <span class="count">(共3张)</span>
    """.trimIndent()

    @Test
    fun `解析出剧照并规范化为 https`() {
        val result = DoubanHtmlParser.parse(samplePage)
        assertFalse(result.blocked)
        assertEquals(3, result.thumbs.size)
        assertTrue(result.thumbs.all { it.startsWith("https://") })
        assertEquals(3, result.totalCount)
    }

    @Test
    fun `反爬页判定为 blocked 而不是没有剧照`() {
        val blocked = DoubanHtmlParser.parse("<html><body>跳转至 sec.douban.com 完成验证</body></html>")
        assertTrue(blocked.blocked)
        assertEquals(0, blocked.thumbs.size)
    }

    @Test
    fun `空页面与 null 都安全`() {
        assertEquals(0, DoubanHtmlParser.parse(null).thumbs.size)
        assertEquals(0, DoubanHtmlParser.parse("").thumbs.size)
        assertFalse(DoubanHtmlParser.parse("").blocked)
    }

    @Test
    fun `结构变化时用兜底正则仍能拿到图`() {
        val changed = """
            <li><img src="https://img1.doubanio.com/view/photo/s_ratio_poster/public/p999.jpg" /></li>
        """.trimIndent()
        assertEquals(1, DoubanHtmlParser.parse(changed).thumbs.size)
    }

    @Test
    fun `缩略图转大图`() {
        assertEquals(
            "https://img1.doubanio.com/view/photo/l/public/p111.jpg",
            DoubanHtmlParser.toLargeUrl("https://img1.doubanio.com/view/photo/s_ratio_poster/public/p111.jpg"),
        )
        assertEquals(
            "https://img1.doubanio.com/view/photo/photo/public/p111.jpg",
            DoubanHtmlParser.toLargeUrl("https://img1.doubanio.com/view/photo/thumb/public/p111.jpg"),
        )
    }

    @Test
    fun `重复图片去重且保序`() {
        val dup = """
            <div class="cover"><img src="https://img1.doubanio.com/view/photo/a/public/p1.jpg" /></div>
            <div class="cover"><img src="https://img1.doubanio.com/view/photo/a/public/p1.jpg" /></div>
            <div class="cover"><img src="https://img1.doubanio.com/view/photo/a/public/p2.jpg" /></div>
        """.trimIndent()
        val thumbs = DoubanHtmlParser.parse(dup).thumbs
        assertEquals(2, thumbs.size)
        assertTrue(thumbs[0].endsWith("p1.jpg"))
    }

    @Test
    fun `搜索页解析与反爬判定`() {
        val html = """
            <div class="result"><div class="content">
            <h3><a href="https://movie.douban.com/subject/10444115/" onclick="moreurl(this, {sid: 10444115, from: 'movie', subject: '进击的巨人'})">进击的巨人</a></h3>
            </div></div>
        """.trimIndent()
        val items = DoubanClient.parseSearchHtml(html)
        assertEquals(1, items.size)
        assertEquals("10444115", items[0].id)
        assertEquals("进击的巨人", items[0].title)
        assertEquals(0, DoubanClient.parseSearchHtml("<html>sec.douban.com</html>").size)
    }
}
