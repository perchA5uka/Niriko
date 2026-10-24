package com.otakup.niriko.ui.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「重新登录」状态迁移的纯函数测试。
 *
 * 守护两件事：
 * 1. reloadSeq 单调递增（WebView 的 update 只认这个变化）；
 * 2. 重登期间 isLoggedIn 必须为 false，否则界面停在已登录分支、WebView 不会被组合出来。
 */
class SteamLoginUiStateTest {

    @Test
    fun afterRestartRequest_bumpsReloadSeq_clearsError_andEntersLoggingIn() {
        val before = SteamLoginUiState(
            status = SteamLoginStatus.ERROR,
            steamId64 = "",
            error = "boom",
            loginUrl = "https://steamcommunity.com/openid/login?x=1",
            reloadSeq = 3,
        )

        val after = before.afterRestartRequest()

        assertEquals(4, after.reloadSeq)
        assertEquals(SteamLoginStatus.LOADING, after.status)
        assertNull(after.error)
        assertTrue(after.loggingIn)
        // loginUrl 不变：真正触发重载的是 seq
        assertEquals(before.loginUrl, after.loginUrl)
    }

    @Test
    fun afterRestartRequest_seqIsStrictlyMonotonic() {
        var state = SteamLoginUiState()
        val seen = mutableListOf<Int>()
        repeat(3) {
            state = state.afterRestartRequest()
            seen += state.reloadSeq
        }
        assertEquals(listOf(1, 2, 3), seen)
    }

    @Test
    fun loggingIn_hidesLoggedInBranchEvenWhenSteamIdSaved() {
        val loggedIn = SteamLoginUiState(
            status = SteamLoginStatus.LOGGED_IN,
            steamId64 = "76561198012345678",
        )
        assertTrue(loggedIn.isLoggedIn)

        val restarting = loggedIn.afterRestartRequest()

        // 关键回归：已登录状态点「重新登录」后必须回到 WebView 分支
        assertFalse(restarting.isLoggedIn)
        // 但不抹掉已保存的 id：取消重登后界面还能显示原账号
        assertEquals("76561198012345678", restarting.steamId64)
        assertTrue(restarting.loggingIn)
    }
}
