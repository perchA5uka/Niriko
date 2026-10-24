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
 * 本客户端只做 URL 构造、回跳判定与失败分类（纯函数）；网络/WebView 交互由调用方负责。
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

    /**
     * WebView 使用的桌面版 Chrome UA。
     *
     * 为什么必须显式设置：WebView 默认 UA 里带 `; wv` 标记，Steam 前置的 Akamai 边缘节点
     * 会把它当成「非浏览器客户端」直接拦成 Access Denied（errors.edgesuite.net），首跳即失败。
     * 为什么不用移动 UA：OpenID 登录页是桌面布局，移动 UA 会触发另一套风控，且页面会被压窄。
     */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36"

    /**
     * 是否为 OpenID 回跳 URL（WebView 拦截判据，纯函数）。
     *
     * 为什么精确到 scheme/host/path 而不是 startsWith 前缀：旧实现用前缀匹配，
     * `/steam-authX` 这类相邻路径也会被当成回跳；而 scheme/host 大小写敏感又会漏掉
     * 少数内核规范化后的 `HTTPS://LOCALHOST/...`。
     * 只接受 http/https：自定义 scheme 会被 Steam 以 "Invalid return protocol" 拒掉。
     */
    fun isCallbackUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val uri = try {
            java.net.URI(trimmed)
        } catch (_: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != RETURN_TO_SCHEME && scheme != "http") return false
        if (!RETURN_TO_HOST.equals(uri.host, ignoreCase = true)) return false
        return when (uri.path) {
            RETURN_TO_PATH, "$RETURN_TO_PATH/" -> true
            else -> false
        }
    }

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

    /**
     * WebView `onReceivedHttpError` 是否该接管：只看主框架、只看 4xx/5xx。
     * 子框架（统计图/广告）出错不该把整个登录页顶掉。
     */
    fun isMainFrameHttpError(isForMainFrame: Boolean, statusCode: Int): Boolean =
        isForMainFrame && statusCode >= 400

    /** HTTP 错误的可见文案（纯函数；UI 只负责把它渲染成 HTML）。 */
    data class HttpErrorPage(
        val statusCode: Int,
        val title: String,
        val detail: String,
        /** 是否建议改用手动 SteamID64（403 与 5xx 的共同出路）。 */
        val suggestManualSteamId: Boolean,
    )

    /**
     * 4xx/5xx 对应的错误文案；statusCode < 400 返回 null（不是错误）。
     *
     * 403 单独成文案：拦掉请求的是 Akamai 边缘节点，返回的是它自己的 Access Denied 页，
     * 用户看到的是英文报错且**不是账号问题**——他最该知道的是「可以改用手动 SteamID64」。
     */
    fun httpErrorPage(statusCode: Int): HttpErrorPage? = when {
        statusCode < 400 -> null
        statusCode == 403 -> HttpErrorPage(
            statusCode = statusCode,
            title = "Steam 拒绝了本次访问（403）",
            detail = "多为网络或风控拦截，不是账号问题。可改用下方「手动输入 SteamID64」继续。",
            suggestManualSteamId = true,
        )
        statusCode in 500..599 -> HttpErrorPage(
            statusCode = statusCode,
            title = "Steam 服务暂时不可用（$statusCode）",
            detail = "稍后重试，或改用下方「手动输入 SteamID64」。",
            suggestManualSteamId = true,
        )
        else -> HttpErrorPage(
            statusCode = statusCode,
            title = "Steam 登录页加载失败（$statusCode）",
            detail = "可改用下方「手动输入 SteamID64」继续。",
            suggestManualSteamId = true,
        )
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
