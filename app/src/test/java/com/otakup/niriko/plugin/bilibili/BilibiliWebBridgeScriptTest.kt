package com.otakup.niriko.plugin.bilibili

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 注入脚本健康检查（防回归）：
 * - 所有占位符（__XXX__）必须被真实值替换完毕，不得残留（残留会导致 JS 运行时 ReferenceError，
 *   历史上出现过 __PAGE_SIZE 缺尾下划线 → 翻页判断崩溃 → 拉取链静默断裂）；
 * - 脚本必须包含关键函数与消息通道，且通过基本语法校验（括号配平）。
 */
class BilibiliWebBridgeScriptTest {

    private fun script(): String = BilibiliWebBridge.injectedJavaScript()

    @Test
    fun `注入脚本不残留占位符`() {
        val js = script()
        // 所有占位符模式（双双下划线包裹）都必须被替换
        val leftovers = Regex(
            "(__PAGE_SIZE__|__PAGE_SIZE|__CONCURRENCY__|__BATCH__|__TIMEOUT_MS__|" +
                "__HOST_API__|__MSG_CHECK_LOGIN__|__MSG_GET_LIST__|__MSG_GET_REVIEW__|" +
                "__MSG_REVIEW_PROGRESS__|__MSG_ERROR__)",
        )
        val match = leftovers.find(js)
        assertFalse("注入脚本残留占位符: ${match?.value}", match != null)
    }

    @Test
    fun `注入脚本包含关键函数与消息通道`() {
        val js = script()
        listOf(
            "function checkLogin",
            "function getList",
            "function getReviews",
            "window.NirikoBridge.postMessage",
            BILI_MSG_CHECK_LOGIN,
            BILI_MSG_GET_LIST,
            BILI_MSG_GET_REVIEW,
            BILI_MSG_REVIEW_PROGRESS,
            BILI_MSG_DEBUG_ERROR,
        ).forEach { token ->
            assertTrue("缺少关键片段: $token", js.contains(token))
        }
    }

    @Test
    fun `脚本包含并发与超时配置`() {
        val js = script()
        assertTrue(js.contains("review/user"))
        assertTrue(js.contains("/x/space/bangumi/follow/list"))
        assertTrue(js.contains("/x/web-interface/nav"))
        assertTrue(js.contains("request.timeout"))
    }

    @Test
    fun `花括号配平`() {
        val js = script()
        fun count(ch: Char) = js.count { it == ch }
        assertTrue("花括号不配平", count('{') == count('}'))
        assertTrue("圆括号不配平", count('(') == count(')'))
    }

    @Test
    fun `脚本非空且含桥接等待`() {
        val js = script()
        assertNotNull(js)
        assertTrue(js.isNotBlank())
        assertTrue(js.contains("NirikoBridge"))
    }
}