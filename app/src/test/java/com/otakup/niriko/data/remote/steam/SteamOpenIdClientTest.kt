package com.otakup.niriko.data.remote.steam

import org.junit.Assert.assertEquals
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
        assertTrue(url.contains("openid.return_to=http%3A%2F%2Flocalhost%2Fsteam-auth"))
        assertTrue(url.contains("openid.realm=http%3A%2F%2Flocalhost"))
        assertTrue(url.contains("openid.identity=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select"))
        assertTrue(url.contains("openid.claimed_id=http%3A%2F%2Fspecs.openid.net%2Fauth%2F2.0%2Fidentifier_select"))
    }

    @Test
    fun buildLoginUrl_steamRequiresHttpProtocolReturnTo() {
        // Steam OpenID 拒绝自定义 scheme（Invalid return protocol），必须 http(s)
        val url = SteamOpenIdClient.buildLoginUrl()
        assertTrue(url.contains("openid.return_to=http%3A%2F%2Flocalhost%2Fsteam-auth"))
        assertTrue(SteamOpenIdClient.RETURN_TO_URL.startsWith("http://"))
    }

    @Test
    fun parseCallback_extractsSteamId64() {
        val url = "http://localhost/steam-auth?openid.ns=...&openid.mode=id_res" +
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
}
