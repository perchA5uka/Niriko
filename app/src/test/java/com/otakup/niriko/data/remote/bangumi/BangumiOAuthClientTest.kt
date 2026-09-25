package com.otakup.niriko.data.remote.bangumi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bangumi OAuth 授权码流程的纯函数单测（第 4 轮 R4c）。
 *
 * 这里只测**不需要网络**的两部分：授权 URL 构造、token 响应解析。
 * 「换 token → 调 /v0/me」需要真实账号，属于真机验证范围。
 */
class BangumiOAuthClientTest {

    // ==================== 授权 URL ====================

    @Test
    fun `授权 URL 包含全部必需参数`() {
        val url = BangumiOAuthClient.buildAuthorizeUrl(
            clientId = "bgm123456",
            redirectUri = "niriko://oauth/bangumi",
            state = "abc-123",
        )
        assertTrue(url.startsWith("https://bgm.tv/oauth/authorize?"))
        assertTrue(url.contains("client_id=bgm123456"))
        assertTrue(url.contains("response_type=code"))
        // redirect_uri 必须被 URL 编码（否则 :// 会被解析器截断）
        assertTrue("redirect_uri 未编码", url.contains("redirect_uri=niriko%3A%2F%2Foauth%2Fbangumi"))
        assertTrue(url.contains("state=abc-123"))
    }

    @Test
    fun `站点域可控以支持镜像`() {
        val url = BangumiOAuthClient.buildAuthorizeUrl(
            clientId = "x",
            redirectUri = "niriko://cb",
            state = "s",
            siteBase = "https://mirror.example.com/",
        )
        assertTrue(url.startsWith("https://mirror.example.com/oauth/authorize?"))
    }

    // ==================== token 响应解析 ====================

    @Test
    fun `解析标准 JSON 响应`() {
        val body = """
            {"access_token":"tok_abc","token_type":"Bearer","expires_in":604800,"refresh_token":"ref_1"}
        """.trimIndent()
        val result = BangumiOAuthClient.parseTokenResponse(body)
        assertNotNull(result)
        assertEquals("tok_abc", result!!.accessToken)
        assertEquals("Bearer", result.tokenType)
    }

    @Test
    fun `JSON 缺少 token_type 时回退 Bearer`() {
        val result = BangumiOAuthClient.parseTokenResponse("""{"access_token":"tok_abc"}""")
        assertNotNull(result)
        assertEquals("Bearer", result!!.tokenType)
    }

    @Test
    fun `裸 token 响应也能解析`() {
        // 部分部署直接返回 token 字符串而不是 JSON
        val result = BangumiOAuthClient.parseTokenResponse("abcdef123456")
        assertNotNull(result)
        assertEquals("abcdef123456", result!!.accessToken)
        assertEquals("Bearer", result.tokenType)
    }

    @Test
    fun `裸 token 前后空白会被裁掉`() {
        val result = BangumiOAuthClient.parseTokenResponse("  tok_x  \n")
        assertNotNull(result)
        assertEquals("tok_x", result!!.accessToken)
    }

    @Test
    fun `空响应返回 null`() {
        assertNull(BangumiOAuthClient.parseTokenResponse(""))
        assertNull(BangumiOAuthClient.parseTokenResponse("   \n  "))
    }

    @Test
    fun `JSON 里没有 access_token 返回 null 而不是崩`() {
        assertNull(BangumiOAuthClient.parseTokenResponse("""{"error":"invalid_grant"}"""))
    }

    @Test
    fun `含空格的多词响应不被当成裸 token`() {
        // 例如 HTML 错误页：必须返回 null，而不是把它存成 token
        assertNull(BangumiOAuthClient.parseTokenResponse("Bad Request Error"))
    }

    @Test
    fun `rawBody 被保留用于排障`() {
        val result = BangumiOAuthClient.parseTokenResponse("""{"access_token":"t"}""")
        assertNotNull(result)
        assertTrue(result!!.rawBody.isNotBlank())
    }
}
