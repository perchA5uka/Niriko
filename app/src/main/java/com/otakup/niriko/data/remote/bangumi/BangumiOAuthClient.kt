package com.otakup.niriko.data.remote.bangumi

import android.util.Log
import com.otakup.niriko.data.remote.BangumiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request

private const val TAG = "BangumiOAuth"

/**
 * Bangumi OAuth 2.0 授权码流程客户端。
 *
 * ## 为什么需要它
 *
 * NSFW 检索需要 access token，而本 App 此前只能**手动粘贴** token——没有 OAuth UI。
 * 本对象补齐「授权 → 回调拿 code → 换 token → 校验 /v0/me」四步。
 *
 * ## 设计约束
 *
 * - **不内置 client id / secret**：由用户自行在 bgm.tv 创建应用后填写（对齐 Bangumi 应用条款，
 *   内置公共应用容易被封）。因此本对象的所有方法都要求调用方显式传入凭据。
 * - **站点域与 API 域分离**：`/oauth/authorize` 与 `/oauth/access_token` 在 **站点域**
 *   （默认 `https://bgm.tv`），而 `/v0/me` 在 **API 域**（[BangumiClient.baseUrl]）。
 *   国区用户可能把 API 切到反代，但 OAuth 仍须走官方站点域，否则授权页拿不到真实会话。
 * - **换 token 走 [BangumiClient.authApiService]**：它固定官方域、带 User-Agent、
 *   且不会挂 baseUrl 重写拦截器（对齐「auth 一律走官方域」的项目约定）。
 *
 * ## 响应格式
 *
 * Bangumi 的 `/oauth/access_token` 在不同版本上返回形态不一致：官方 v0 文档是 JSON
 * （`access_token` / `token_type` / `expires_in` / `refresh_token`），而部分部署直接返回
 * **裸 token 字符串**。因此必须先按 JSON 解析，失败再整串当 token——两种都要能过。
 */
object BangumiOAuthClient {

    /** 官方站点域（OAuth 授权页与 token 端点所在域）。 */
    const val DEFAULT_SITE_BASE = "https://bgm.tv"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 换 token 结果。 */
    data class TokenResult(
        val accessToken: String,
        val tokenType: String,
        /** 原始响应体（诊断用，最多保留 300 字）。 */
        val rawBody: String,
    )

    /**
     * 构造授权页 URL。
     *
     * `state` 用于防 CSRF，同时让回调能被可靠识别；调用方应把它一并保存，
     * 在 [exchangeCode] 时校验回来的一致。
     */
    fun buildAuthorizeUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        siteBase: String = DEFAULT_SITE_BASE,
    ): String {
        val base = siteBase.trim().trimEnd('/')
        return "$base/oauth/authorize" +
            "?client_id=" + encode(clientId) +
            "&response_type=code" +
            "&redirect_uri=" + encode(redirectUri) +
            "&state=" + encode(state)
    }

    /**
     * 用回调拿到的 code 换取 access token。
     *
     * 失败抛 [IllegalStateException]，message 里带**响应原文摘要**——没有这个，
     * 线上出问题时只能看到「登录失败」三个字，无从排查。
     */
    suspend fun exchangeCode(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        code: String,
        siteBase: String = DEFAULT_SITE_BASE,
    ): TokenResult = withContext(Dispatchers.IO) {
        val base = siteBase.trim().trimEnd('/')
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .build()

        val request = Request.Builder()
            .url("$base/oauth/access_token")
            .post(form)
            .header("Accept", "application/json")
            .build()

        // authApiService 所在 client：固定官方域 + User-Agent，不带 baseUrl 重写
        BangumiClient.authOkHttpClient.newCall(request).execute().use { response ->
            val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
            val summary = body.take(300)
            if (!response.isSuccessful) {
                Log.w(TAG, "access_token HTTP ${response.code}: $summary")
                error("HTTP ${response.code}：${summary.ifBlank { "(空响应)" }}")
            }
            val parsed = parseTokenResponse(body)
                ?: error("响应中找不到 access_token：${summary.ifBlank { "(空响应)" }}")
            Log.i(TAG, "access_token OK (type=${parsed.tokenType})")
            parsed
        }
    }

    /**
     * 校验 token 并取用户名（GET /v0/me）。
     *
     * 走 [BangumiClient.authApiService]：固定官方域、读取 [BangumiClient.authTokenProvider]。
     * 调用方需**先**把 token 写进设置，provider 才会带上它（因此这里不再单收 token 参数，
     * 避免出现「传进来的 token」与「拦截器实际用的 token」两份真相）。
     */
    suspend fun verify(): String = withContext(Dispatchers.IO) {
        val me = BangumiClient.authApiService.me()
        me.nickname.ifBlank { me.username }.ifBlank { "uid=${me.id}" }
    }

    /**
     * 解析 token 响应。JSON 优先，裸 token 兜底。
     *
     * 裸 token 的判定条件：整串非空、不含 `{`、且不含空白（token 是 URL-safe 串）。
     */
    internal fun parseTokenResponse(body: String): TokenResult? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("{")) {
            val obj = runCatching {
                json.parseToJsonElement(trimmed) as? JsonObject
            }.getOrNull()
            val token = obj?.get("access_token")?.jsonPrimitive?.content
            if (!token.isNullOrBlank()) {
                val type = obj.get("token_type")?.jsonPrimitive?.content
                    ?.takeIf { it.isNotBlank() } ?: "Bearer"
                return TokenResult(token.trim(), type, trimmed.take(300))
            }
            return null
        }

        // 裸 token 兜底（部分部署直接返回 token 字符串）
        return if (trimmed.none { it.isWhitespace() }) {
            TokenResult(trimmed, "Bearer", trimmed.take(300))
        } else {
            null
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
