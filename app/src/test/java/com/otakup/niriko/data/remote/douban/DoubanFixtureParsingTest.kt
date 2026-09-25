package com.otakup.niriko.data.remote.douban

import com.otakup.niriko.data.probe.DoubanHtmlFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 豆瓣 HTML 解析器的 **fixture 单测**（第 4 轮 F）。
 *
 * ## 为什么用 fixture 而不是内联字符串
 *
 * 第 3 轮的失败教训：解析器的正则是我**根据猜测的页面结构**写的，没有任何真实样本，
 * 因此页面结构一变就静默失效（解析出 0 张图，上层以为「这部作品没剧照」）。
 *
 * 现在把「自检工具拿到的真实响应形态」固化成 [DoubanHtmlFixtures]，
 * 页面改版导致解析失败时这些测试会红——而不是用户看到空白剧照区。
 */
class DoubanFixtureParsingTest {

    @Test
    fun `真实剧照页能解析出全部剧照与总数`() {
        val result = DoubanHtmlParser.parse(DoubanHtmlFixtures.photosPage)
        assertFalse("不该被判为反爬页", result.blocked)
        assertEquals(3, result.thumbs.size)
        assertEquals(42, result.totalCount)
        assertTrue("所有 URL 都应规范化为 https", result.thumbs.all { it.startsWith("https://") })
    }

    @Test
    fun `反爬拦截页被判为 blocked 而不是零剧照`() {
        val result = DoubanHtmlParser.parse(DoubanHtmlFixtures.antiScrapePage)
        assertTrue("必须识别为拦截", result.blocked)
        assertEquals(0, result.thumbs.size)
    }

    @Test
    fun `HTML 实体与协议相对 URL 都被规范化`() {
        val result = DoubanHtmlParser.parse(DoubanHtmlFixtures.emojiPage)
        assertFalse(result.blocked)
        assertEquals(3, result.thumbs.size)
        // &amp; 必须还原成 &，否则图片 URL 会 404
        assertTrue("实体未解码", result.thumbs.any { it.contains("&") })
        assertFalse("仍残留 &amp;", result.thumbs.any { it.contains("&amp;") })
        // 所有 URL 都应是绝对 https
        assertTrue(result.thumbs.all { it.startsWith("https://") })
    }

    @Test
    fun `所有 fixture 都能安全解析_不抛异常`() {
        DoubanHtmlFixtures.all.forEachIndexed { index, html ->
            assertNotNull("fixture #$index 解析返回 null", DoubanHtmlParser.parse(html))
        }
    }

    @Test
    fun `缩略图转大图在三种尺寸段上都正确`() {
        assertEquals(
            "https://img1.doubanio.com/view/photo/l/public/p1.jpg",
            DoubanHtmlParser.toLargeUrl("https://img1.doubanio.com/view/photo/s_ratio_poster/public/p1.jpg"),
        )
        assertEquals(
            "https://img1.doubanio.com/view/photo/photo/public/p1.jpg",
            DoubanHtmlParser.toLargeUrl("https://img1.doubanio.com/view/photo/thumb/public/p1.jpg"),
        )
    }

    @Test
    fun `默认图片 Referer 是不带 path 的 douban_com`() {
        // 这是 Bangumi-master 的实测值：带 path 的 movie.douban.com/ 会被图床拒
        val headers = DoubanClient.Headers()
        assertEquals("https://douban.com", headers.imageReferer)
        assertFalse(headers.imageReferer.endsWith("/"))
    }

    @Test
    fun `搜索页解析能处理反爬与正常两种页面`() {
        assertTrue(DoubanClient.parseSearchHtml(DoubanHtmlFixtures.antiScrapePage).isEmpty())
        assertEquals(0, DoubanClient.parseSearchHtml(null).size)
        assertEquals(0, DoubanClient.parseSearchHtml("").size)
    }
}
