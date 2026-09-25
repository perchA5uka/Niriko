package com.otakup.niriko.data.remote

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 认证头注入单测（第 6 轮 F6）。
 *
 * 根因：authInterceptor 只挂在 authHttpClient 上，主 client 从不带 token ——
 * 已登录用户走 nsfw=true 的 v0 检索永远缺 Authorization。
 * 同时必须保证**反代域拿不到用户凭据**（域名白名单）。
 */
class BangumiAuthHeaderTest {

    private val token = BangumiClient.AuthToken(tokenType = "Bearer", accessToken = "abc123")

    @After
    fun restoreTokenProvider() {
        BangumiClient.authTokenProvider = null
    }

    @Test
    fun officialHostGetsAuthorization() {
        assertEquals("Bearer abc123", BangumiClient.authHeaderValue("api.bgm.tv", token))
    }

    @Test
    fun proxyHostNeverGetsAuthorization() {
        assertNull(BangumiClient.authHeaderValue("bgmapi.anibt.net", token))
        assertNull(BangumiClient.authHeaderValue("lain.bgm.tv", token))
    }

    @Test
    fun missingTokenMeansNoHeader() {
        assertNull(BangumiClient.authHeaderValue("api.bgm.tv", null))
        assertNull(BangumiClient.authHeaderValue("api.bgm.tv", BangumiClient.AuthToken(accessToken = " ")))
    }

    @Test
    fun blankTokenTypeFallsBackToBearer() {
        assertEquals(
            "Bearer abc123",
            BangumiClient.authHeaderValue("api.bgm.tv", BangumiClient.AuthToken(tokenType = "", accessToken = "abc123")),
        )
    }

    @Test
    fun interceptorInjectsOnOfficialDomain() {
        BangumiClient.authTokenProvider = { token }
        val chain = FakeChain(request("https://api.bgm.tv/v0/search/subjects?limit=3"))

        BangumiClient.authInterceptor.intercept(chain)

        assertEquals("Bearer abc123", chain.lastRequest?.header("Authorization"))
    }

    @Test
    fun interceptorSkipsProxyDomain() {
        BangumiClient.authTokenProvider = { token }
        val chain = FakeChain(request("https://bgmapi.anibt.net/v0/search/subjects?limit=3"))

        BangumiClient.authInterceptor.intercept(chain)

        assertNull("反代域不允许带上用户凭据", chain.lastRequest?.header("Authorization"))
    }

    @Test
    fun interceptorSkipsWhenLoggedOut() {
        BangumiClient.authTokenProvider = null
        val chain = FakeChain(request("https://api.bgm.tv/v0/search/subjects?limit=3"))

        BangumiClient.authInterceptor.intercept(chain)

        assertNull(chain.lastRequest?.header("Authorization"))
    }

    private fun request(url: String): Request = Request.Builder().url(url).build()

    /** 最小可用的 Interceptor.Chain：只用到 request / proceed。 */
    private class FakeChain(private val original: Request) : Interceptor.Chain {

        var lastRequest: Request? = null

        override fun request(): Request = original

        override fun proceed(request: Request): Response {
            lastRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        override fun connection(): Connection? = null

        override fun call(): Call = throw UnsupportedOperationException("call 未在单测里使用")

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
