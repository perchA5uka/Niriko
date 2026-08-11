package com.otakup.niriko.plugin.bilibili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BilibiliMatcher 匹配策略测试：
 * - season_id 反查优先；
 * - 标题兜底（含「（仅限港澳台）」后缀剥离）；
 * - 均不中返回 null。
 */
class BilibiliMatcherTest {

    private val siteMapLookup: (Int) -> Long? = { seasonId ->
        mapOf(28225642 to 123L, 3398 to 256L)[seasonId]
    }

    // ==================== season_id 反查 ====================

    @Test
    fun `season id 反查命中返回 bgmId`() {
        assertEquals(
            123L,
            BilibiliMatcher.match(seasonId = 28225642, title = "任意标题", siteMapLookup = siteMapLookup, localTitles = emptyMap()),
        )
    }

    @Test
    fun `season id 为 0 或空时不走反查`() {
        // seasonId 0 → 反查跳过（siteMapLookup 不会命中），走标题兜底失败 → null
        assertNull(
            BilibiliMatcher.match(seasonId = 0, title = "找不到的标题", siteMapLookup = siteMapLookup, localTitles = emptyMap()),
        )
        assertNull(
            BilibiliMatcher.match(seasonId = null, title = "找不到的标题", siteMapLookup = siteMapLookup, localTitles = emptyMap()),
        )
    }

    // ==================== 标题兜底 ====================

    @Test
    fun `反查未命中时按标题匹配本地 title`() {
        assertNull(BilibiliMatcher.match(seasonId = 999999, title = "鬼灭之刃 柱训练篇", siteMapLookup = siteMapLookup, localTitles = emptyMap()))
        assertEquals(
            888L,
            BilibiliMatcher.match(
                seasonId = 999999,
                title = "鬼灭之刃 柱训练篇",
                siteMapLookup = siteMapLookup,
                localTitles = mapOf(888L to "鬼灭之刃 柱训练篇"),
            ),
        )
    }

    @Test
    fun `本地 titleCN 参与匹配时也能命中`() {
        assertEquals(
            777L,
            BilibiliMatcher.match(
                seasonId = null,
                title = "间谍过家家",
                siteMapLookup = siteMapLookup,
                localTitles = mapOf(777L to "SPY×FAMILY", 777L to "间谍过家家"),
            ),
        )
    }

    // ==================== 港澳台后缀剥离 ====================

    @Test
    fun `bili 标题带仅限港澳台后缀仍可匹配本地标题`() {
        assertEquals(
            123L,
            BilibiliMatcher.match(
                seasonId = null,
                title = "某番第一季（仅限港澳台）",
                siteMapLookup = siteMapLookup,
                localTitles = mapOf(123L to "某番第一季"),
            ),
        )
        assertEquals(
            123L,
            BilibiliMatcher.match(
                seasonId = null,
                title = "某番第一季（僅限港澳台地區）",
                siteMapLookup = siteMapLookup,
                localTitles = mapOf(123L to "某番第一季"),
            ),
        )
    }

    @Test
    fun `标题归一化去括注并统一括号`() {
        assertEquals("某番 第二季", BilibiliMatcher.normalizeTitle(" 某番　第二季 ")) // 全角空格 → 半角
        assertEquals("某番(特别篇)", BilibiliMatcher.normalizeTitle("某番（特别篇）"))
        assertEquals("某番", BilibiliMatcher.normalizeTitle("某番（仅限港澳台）"))
        assertEquals("某番", BilibiliMatcher.normalizeTitle("某番(僅限港澳台地區)"))
        // 多后缀循环剥离：仅限港澳台在尾部时逐轮剥除，剩余括注保留
        assertEquals("某番(特别篇)", BilibiliMatcher.normalizeTitle("某番（特别篇）（仅限港澳台）"))
    }

    // ==================== 未匹配 ====================

    @Test
    fun `无 season 且本地无匹配标题时返回 null`() {
        assertNull(
            BilibiliMatcher.match(
                seasonId = null,
                title = "完全不同的标题",
                siteMapLookup = siteMapLookup,
                localTitles = mapOf(1L to "别的作品"),
            ),
        )
    }

    @Test
    fun `空标题不参与标题匹配但可走反查`() {
        assertNull(
            BilibiliMatcher.match(seasonId = null, title = "", siteMapLookup = siteMapLookup, localTitles = mapOf(1L to "别的作品")),
        )
        assertEquals(
            123L,
            BilibiliMatcher.match(seasonId = 28225642, title = "", siteMapLookup = siteMapLookup, localTitles = emptyMap()),
        )
    }
}