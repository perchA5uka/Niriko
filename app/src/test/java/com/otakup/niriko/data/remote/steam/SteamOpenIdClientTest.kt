package com.otakup.niriko.data.remote.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SteamOpenIdClient 纯函数单元测试。
 */
class SteamOpenIdClientTest {

    @Test
    fun buildLoginUrl_containsRequiredParams() {
        val url = SteamOpenIdClient.buildLoginUrl()
        assertTrue(url.startsWith(SteamOpenIdClient.OPENID_ENDPOINT + "?"))
        assertTrue(url.contains("openid.ns=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0"))
        assertTrue(url.contains("openid.mode=checkid_setup"))
        assertTrue(url.contains("openid.return_to=https%3A%2F%2Flocalhost%2Fsteam-auth"))
        assertTrue(url.contains("openid.realm=https%3A%2F%2Flocalhost"))
        assertTrue(url.contains("openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select"))
        assertTrue(url.contains("openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select"))
    }

    @Test
    fun buildLoginUrl_usesHttpsReturnTo() {
        // Steam 接受 http/https 的 return_to,但 http 会触发 Akamai Access Denied,
        // 必须全程 https 避免流程降级
        val url = SteamOpenIdClient.buildLoginUrl()
        assertTrue(url.contains("openid.return_to=https%3A%2F%2Flocalhost%2Fsteam-auth"))
        assertTrue(url.contains("openid.realm=https%3A%2F%2Flocalhost"))
        assertTrue(SteamOpenIdClient.RETURN_TO_URL.startsWith("https://"))
    }

    @Test
    fun parseCallback_extractsSteamId64() {
        val url = "https://localhost/steam-auth?openid.ns=...&openid.mode=id_res" +
            "&openid.claimed_id=https%3A%2F%2Fsteamcommunity.com%2Fopenid%2Fid%2F76561198012345678" +
            "&openid.identity=https%3A%2F%2Fsteamcommunity.com%2Fopenid%2Fid%2F76561198012345678"
        val callback = SteamOpenIdClient.parseCallback(url)
        assertEquals("76561198012345678", callback.steamId64)
        assertEquals("id_res", callback.mode)
        assertEquals(
            "https://steamcommunity.com/openid/id/76561198012345678",
            callback.claimedId,
        )
    }

    @Test
    fun parseCallback_handlesPlainQueryString() {
        val query = "openid.mode=id_res&openid.claimed_id=https%3A%2F%2Fsteamcommunity.com%2Fopenid%2Fid%2F76561198012345678"
        val callback = SteamOpenIdClient.parseCallback(query)
        assertEquals("76561198012345678", callback.steamId64)
    }

    @Test
    fun parseCallback_invalidClaimedId_returnsNullSteamId64() {
        // 非 Steam claimed_id（如恶意/错误回跳）
        val url = "niriko://steam-auth?openid.claimed_id=https%3A%2F%2Fevil.com%2Fid%2F76561198012345678"
        val callback = SteamOpenIdClient.parseCallback(url)
        assertNull(callback.steamId64)
        // claimed_id 非数字
        val url2 = "niriko://steam-auth?openid.claimed_id=https%3A%2F%2Fsteamcommunity.com%2Fopenid%2Fid%2Fnotanumber"
        assertNull(SteamOpenIdClient.parseCallback(url2).steamId64)
    }

    @Test
    fun parseCallback_emptyUrl_returnsEmpty() {
        val callback = SteamOpenIdClient.parseCallback("")
        assertNull(callback.steamId64)
        assertTrue(callback.rawParams.isEmpty())
    }

    @Test
    fun isCallbackUrl_acceptsHttpsAndHttpReturnTo() {
        assertTrue(SteamOpenIdClient.isCallbackUrl("https://localhost/steam-auth"))
        assertTrue(
            SteamOpenIdClient.isCallbackUrl(
                "https://localhost/steam-auth?openid.mode=id_res&openid.claimed_id=x",
            ),
        )
        // Steam 个别情况下会以 http 回跳（老实现为此单独写了兜底）
        assertTrue(SteamOpenIdClient.isCallbackUrl("http://localhost/steam-auth?openid.mode=id_res"))
        // 有些内核会把 scheme/host 规范化成大写
        assertTrue(SteamOpenIdClient.isCallbackUrl("HTTPS://LOCALHOST/steam-auth"))
    }

    @Test
    fun isCallbackUrl_rejectsEverythingElse() {
        assertFalse(SteamOpenIdClient.isCallbackUrl("https://store.steampowered.com/"))
        assertFalse(
            SteamOpenIdClient.isCallbackUrl("https://steamcommunity.com/openid/login?openid.mode=checkid_setup"),
        )
        // 只有主机没有 path：不是回跳
        assertFalse(SteamOpenIdClient.isCallbackUrl("https://localhost"))
        // 前缀相同的相邻路径不能被误判（旧实现 startsWith 的缺陷）
        assertFalse(SteamOpenIdClient.isCallbackUrl("https://localhost/steam-authX"))
        // 同前缀的恶意主机
        assertFalse(SteamOpenIdClient.isCallbackUrl("https://localhost.evil.com/steam-auth"))
        assertFalse(SteamOpenIdClient.isCallbackUrl("ftp://localhost/steam-auth"))
        assertFalse(SteamOpenIdClient.isCallbackUrl(""))
        assertFalse(SteamOpenIdClient.isCallbackUrl("   "))
    }

    @Test
    fun buildLoginUrl_realmIsSameOriginPrefixAsReturnTo() {
        val url = SteamOpenIdClient.buildLoginUrl()
        val returnTo = decodeParam(url, "openid.return_to")
        val realm = decodeParam(url, "openid.realm")

        assertTrue(returnTo != null && returnTo.startsWith("https://"))
        assertTrue(realm != null && realm.startsWith("https://"))
        // Steam 要求 return_to 落在 realm 之内（同源前缀），否则直接拒绝
        assertEquals(realm, returnTo!!.substringBefore(SteamOpenIdClient.RETURN_TO_PATH))
        assertEquals(SteamOpenIdClient.RETURN_TO_HOST, java.net.URI(returnTo).host)
        assertEquals(SteamOpenIdClient.RETURN_TO_HOST, java.net.URI(realm!!).host)
    }

    @Test
    fun desktopUserAgent_isDesktopChromeWithoutWebViewMarker() {
        val ua = SteamOpenIdClient.DESKTOP_USER_AGENT
        assertTrue(ua.contains("Windows NT"))
        assertTrue(ua.contains("Chrome/120"))
        // 默认 WebView UA 里的 wv 标记正是被 Akamai 拦成 403 的判据
        assertFalse(ua.contains("; wv"))
        assertFalse(ua.contains("Mobile"))
    }

    @Test
    fun isMainFrameHttpError_onlyMainFrameAndErrorStatus() {
        assertTrue(SteamOpenIdClient.isMainFrameHttpError(isForMainFrame = true, statusCode = 403))
        assertTrue(SteamOpenIdClient.isMainFrameHttpError(isForMainFrame = true, statusCode = 500))
        // 子框架（统计图/广告）出错不该把登录页顶掉
        assertFalse(SteamOpenIdClient.isMainFrameHttpError(isForMainFrame = false, statusCode = 403))
        // 2xx/3xx 不是错误
        assertFalse(SteamOpenIdClient.isMainFrameHttpError(isForMainFrame = true, statusCode = 200))
        assertFalse(SteamOpenIdClient.isMainFrameHttpError(isForMainFrame = true, statusCode = 302))
    }

    @Test
    fun httpErrorPage_403_pointsToManualSteamId64() {
        val page = SteamOpenIdClient.httpErrorPage(403)!!
        assertEquals(403, page.statusCode)
        assertTrue(page.title.contains("403"))
        assertTrue(page.detail.contains("手动输入 SteamID64"))
        assertTrue(page.suggestManualSteamId)
    }

    @Test
    fun httpErrorPage_covers5xxAndOther4xx_butNullBelow400() {
        assertNull(SteamOpenIdClient.httpErrorPage(200))
        assertNull(SteamOpenIdClient.httpErrorPage(304))

        val serverError = SteamOpenIdClient.httpErrorPage(503)!!
        assertTrue(serverError.title.contains("503"))
        assertTrue(serverError.suggestManualSteamId)

        val notFound = SteamOpenIdClient.httpErrorPage(404)!!
        assertTrue(notFound.title.contains("404"))
        assertTrue(notFound.suggestManualSteamId)
    }

    /** 从 loginUrl 里取并解码某个 query 参数（测试用）。 */
    private fun decodeParam(url: String, key: String): String? =
        url.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
}
