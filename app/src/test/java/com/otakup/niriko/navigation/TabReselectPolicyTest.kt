package com.otakup.niriko.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏重选判决（F11）的纯函数测试。
 *
 * 锁住三条语义，防止以后有人把「重选」实现成「无论如何都再刷一次」：
 * 1. 切页不归内容层管；
 * 2. 不在顶部时**只回顶、不刷新**；
 * 3. 已在顶部时只有声明了刷新语义的页才发请求。
 *
 * 这里**不测**滚动状态本身（LazyListState 需要 Compose 运行时）：判决只接受
 * `atTop` / `contentScrollable` 这两个已经算好的布尔量，滚动状态的读取在
 * `decideTabReselect(signal, page, refresh, listState)` 里，是同一判决的一层薄封装。
 */
class TabReselectPolicyTest {

    @Test
    fun 非当前页一律不消费() {
        for (refresh in listOf(true, false)) {
            for (atTop in listOf(true, false)) {
                for (scrollable in listOf(true, false)) {
                    assertEquals(
                        "切页不能被内容层消费 (refresh=$refresh atTop=$atTop scrollable=$scrollable)",
                        TabReselectAction.NONE,
                        decideTabReselect(
                            isCurrentPage = false,
                            supportsRefreshAtTop = refresh,
                            atTop = atTop,
                            contentScrollable = scrollable,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun 当前页不在顶部时先回顶而不是刷新() {
        assertEquals(
            TabReselectAction.SCROLL_TO_TOP,
            decideTabReselect(isCurrentPage = true, supportsRefreshAtTop = true, atTop = false, contentScrollable = true),
        )
        // 不支持刷新的页（作品库/统计）同样要回顶
        assertEquals(
            TabReselectAction.SCROLL_TO_TOP,
            decideTabReselect(isCurrentPage = true, supportsRefreshAtTop = false, atTop = false, contentScrollable = true),
        )
    }

    @Test
    fun 当前页已在顶部时只有发现页刷新() {
        assertEquals(
            TabReselectAction.REFRESH_AT_TOP,
            decideTabReselect(isCurrentPage = true, supportsRefreshAtTop = true, atTop = true, contentScrollable = true),
        )
        // 作品库/统计在顶部重选 = 无操作（绝不误发请求）
        assertEquals(
            TabReselectAction.NONE,
            decideTabReselect(isCurrentPage = true, supportsRefreshAtTop = false, atTop = true, contentScrollable = true),
        )
    }

    @Test
    fun 内容不足一屏时不会产生无意义回顶() {
        // 不可滚动 + 不支持刷新 → 什么都不做
        assertEquals(
            TabReselectAction.NONE,
            decideTabReselect(
                isCurrentPage = true,
                supportsRefreshAtTop = false,
                atTop = true,
                contentScrollable = false,
            ),
        )
        // 不可滚动 + 支持刷新（发现页空态/骨架）→ 仍然算「在顶部」，照常刷新
        assertEquals(
            TabReselectAction.REFRESH_AT_TOP,
            decideTabReselect(
                isCurrentPage = true,
                supportsRefreshAtTop = true,
                atTop = true,
                contentScrollable = false,
            ),
        )
    }

    @Test
    fun 空信号与陌生页号都不消费() {
        val signal = TabReselectSignal(id = 7, page = TopLevelDestination.Discover.ordinal, action = TabReselectAction.REFRESH_AT_TOP)
        // 从未重选过（id = 0）
        assertEquals(
            TabReselectAction.NONE,
            decideTabReselect(TabReselectSignal.None, page = TopLevelDestination.Discover.ordinal, supportsRefreshAtTop = true, atTop = true, contentScrollable = true),
        )
        // null（调用方没拿到信号）
        assertEquals(
            TabReselectAction.NONE,
            decideTabReselect(null, page = TopLevelDestination.Discover.ordinal, supportsRefreshAtTop = true, atTop = true, contentScrollable = true),
        )
        // 事件是给别的页的
        assertEquals(
            TabReselectAction.NONE,
            decideTabReselect(signal, page = TopLevelDestination.Library.ordinal, supportsRefreshAtTop = false, atTop = false, contentScrollable = true),
        )
        // 事件正是给这一页的 → 走同一判决
        assertEquals(
            TabReselectAction.REFRESH_AT_TOP,
            decideTabReselect(signal, page = TopLevelDestination.Discover.ordinal, supportsRefreshAtTop = true, atTop = true, contentScrollable = true),
        )
    }

    /** 只有发现页在顶部重选时才刷新；作品库/统计即使支持「回顶」也不刷新（本批的取舍）。 */
    @Test
    fun 每页声明的处置符合本批约定() {
        assertEquals(TabReselectAction.REFRESH_AT_TOP, tabReselectActionFor(TopLevelDestination.Discover.ordinal))
        assertEquals(TabReselectAction.SCROLL_TO_TOP, tabReselectActionFor(TopLevelDestination.Library.ordinal))
        assertEquals(TabReselectAction.SCROLL_TO_TOP, tabReselectActionFor(TopLevelDestination.Stats.ordinal))
        assertEquals(TabReselectAction.NONE, tabReselectActionFor(TopLevelDestination.Settings.ordinal))
        assertEquals(TabReselectAction.NONE, tabReselectActionFor(-1))
    }

    @Test
    fun 扩展判据与枚举一一对应() {
        assertTrue(TabReselectAction.SCROLL_TO_TOP.shouldScrollToTop())
        assertFalse(TabReselectAction.REFRESH_AT_TOP.shouldScrollToTop())
        assertTrue(TabReselectAction.REFRESH_AT_TOP.shouldRefreshAtTop())
        assertFalse(TabReselectAction.SCROLL_TO_TOP.shouldRefreshAtTop())
        assertFalse(TabReselectAction.NONE.shouldRefreshAtTop())
        assertFalse(TabReselectAction.NONE.shouldScrollToTop())
        assertFalse(TabReselectAction.NONE.isConsumed())
        assertTrue(TabReselectAction.SCROLL_TO_TOP.isConsumed())
        assertTrue(TabReselectAction.REFRESH_AT_TOP.isConsumed())
    }

    @Test
    fun 重选信号带页号与处置() {
        val signal = TabReselectSignal(id = 3, page = 1, action = TabReselectAction.REFRESH_AT_TOP)
        assertEquals(3, signal.id)
        assertEquals(1, signal.page)
        assertEquals(TabReselectAction.REFRESH_AT_TOP, signal.action)
        assertEquals(TabReselectSignal.None, TabReselectSignal.None.copy())
        assertEquals(0, TabReselectSignal.None.id)
    }
}
