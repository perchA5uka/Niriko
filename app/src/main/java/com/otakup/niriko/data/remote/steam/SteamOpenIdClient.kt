package com.otakup.niriko.data.remote.steam

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Steam OpenID 2.0 登录客户端（纯函数，便于单测）。
 *
 * Steam 官方登录协议为 OpenID 2.0（社区常称 "Sign in through Steam"）：
 * 应用构造 `https://steamcommunity.com/openid/login` 的 checkid_setup 请求，
 * 用户在 Steam 页面确认后回跳 return_to，携带 openid.* 参数；
 * 从 `openid.claimed_id`（形如 `https://steamcommunity.com/openid/id/<steamid64>`）
 * 提取 SteamID64。登录本身无需 API key（key 仅用于后续 GetOwnedGames 拉库）。
 *
 * 本客户端只做 URL 构造与回跳解析（纯函数）；网络/WebView 交互由调用方负责。
 */
object SteamOpenIdClient {

    /** OpenID 认证端点。 */
    const val OPENID_ENDPOINT = "https://steamcommunity.com/openid/login"

    /** OpenID 2.0 命名空间常量。 */
    private const val NS = "http://specs.openid.net/auth/2.0"
    private const val IDENTIFIER_SELECT =
        "http://specs.openid.net/auth/2.0/identifier_select"

    /**
     * 回跳地址（WebView 拦截用）。
     *
     * Steam OpenID 2.0 **只接受 http/https 协议的 return_to**（自定义 scheme 会被拒，
     * 报 "Invalid return protocol"）。这里用 `https://localhost/steam-auth`：
     * - 必须用 **https**：若 return_to/realm 用 http，Steam 会把整个 OpenID 流程降级为
     *   http 明文访问 `steamcommunity.com`，被 Akamai 边缘节点拒绝（Access Denied，
     *   errors.edgesuite.net）；
     * - 回跳由 WebView `shouldOverrideUrlLoading` 拦截解析（[RETURN_TO_URL] 前缀匹配），
     *   不会真正发起 localhost 请求，因此不会触发证书/cleartext 错误。
     */
    const val RETURN_TO_SCHEME = "https"
    const val RETURN_TO_HOST = "localhost"
    const val RETURN_TO_PATH = "/steam-auth"

    /** 完整 return_to URL：https://localhost/steam-auth。 */
    const val RETURN_TO_URL = "$RETURN_TO_SCHEME://$RETURN_TO_HOST$RETURN_TO_PATH"

    /** claimed_id 前缀（提取 SteamID64 用）。 */
    private const val CLAIMED_ID_PREFIX = "https://steamcommunity.com/openid/id/"

    /**
     * 构造 OpenID checkid_setup 请求 URL（登录页地址）。
     * @param returnTo 回跳地址；默认 [RETURN_TO_URL]
     * @param realm 声明域；默认 https://localhost（须与 return_to 同源前缀，且用 https 避免流程降级）
     */
    fun buildLoginUrl(
        returnTo: String = RETURN_TO_URL,
        realm: String = "$RETURN_TO_SCHEME://$RETURN_TO_HOST",
    ): String {
        val params = mapOf(
            "openid.ns" to NS,
            "openid.mode" to "checkid_setup",
            "openid.return_to" to returnTo,
            "openid.realm" to realm,
            "openid.identity" to IDENTIFIER_SELECT,
            "openid.claimed_id" to IDENTIFIER_SELECT,
        )
        return OPENID_ENDPOINT + "?" + params.entries.joinToString("&") { (k, v) ->
            "$k=${java.net.URLEncoder.encode(v, StandardCharsets.UTF_8.name())}"
        }
    }

    /** OpenID 回跳参数（解析后的键值映射）。 */
    data class OpenIdCallback(
        val claimedId: String? = null,
        val steamId64: String? = null,
        val mode: String? = null,
        /** 回跳原始参数（诊断用）。 */
        val rawParams: Map<String, String> = emptyMap(),
    )

    /**
     * 解析 OpenID 回跳 URL 参数。
     * 支持完整 URL（含 query）或纯 query 字符串。
     */
    fun parseCallback(callbackUrl: String): OpenIdCallback {
        // 有 '?' 取其后（完整 URL），否则整串即 query
        val query = if ('?' in callbackUrl) {
            callbackUrl.substringAfter('?')
        } else {
            callbackUrl
        }
        val rawParams = query.split('&')
            .filter { it.isNotBlank() }
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val k = decode(pair.substring(0, idx))
                val v = decode(pair.substring(idx + 1))
                k to v
            }
            .toMap()

        val claimedId = rawParams["openid.claimed_id"]
        val steamId64 = claimedId
            ?.takeIf { it.startsWith(CLAIMED_ID_PREFIX) }
            ?.removePrefix(CLAIMED_ID_PREFIX)
            ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }

        return OpenIdCallback(
            claimedId = claimedId,
            steamId64 = steamId64,
            mode = rawParams["openid.mode"],
            rawParams = rawParams,
        )
    }

    /** URL 解码（宽松，失败返回原串）。 */
    private fun decode(s: String): String = try {
        URLDecoder.decode(s, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        s
    }
}
