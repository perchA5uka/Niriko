package com.otakup.niriko.data.remote.steam

import com.otakup.niriko.data.remote.steam.dto.SteamPriceDto
import com.otakup.niriko.data.remote.steam.dto.SteamStoreSearchItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SteamTitleMatcher 纯函数单元测试。
 */
class SteamTitleMatcherTest {

    // ==================== 归一化 ====================

    @Test
    fun normalizeTitle_stripsSymbolsAndNormalizesWidth() {
        assertEquals("black myth wukong", SteamTitleMatcher.normalizeTitle("Black™ Myth：Wukong®"))
        assertEquals("elden ring", SteamTitleMatcher.normalizeTitle("　ELDEN RING　"))
        assertEquals("hollow(knight)", SteamTitleMatcher.normalizeTitle("Hollow（Knight）"))
    }

    // ==================== 置信度 ====================

    @Test
    fun confidence_exactMatch_isOne() {
        assertEquals(1f, SteamTitleMatcher.confidence("艾尔登法环", "艾尔登法环"), 0.001f)
    }

    @Test
    fun confidence_caseInsensitive() {
        assertEquals(1f, SteamTitleMatcher.confidence("Elden Ring", "elden ring"), 0.001f)
    }

    @Test
    fun confidence_contains_isHigh() {
        val score = SteamTitleMatcher.confidence("艾尔登法环", "艾尔登法环 黄金树幽影")
        assertTrue("包含关系应 ≥0.85,实际 $score", score >= 0.85f)
    }

    @Test
    fun confidence_unrelated_isLow() {
        val score = SteamTitleMatcher.confidence("孤独摇滚", "Dota 2")
        assertTrue("无关标题应 <0.5,实际 $score", score < 0.5f)
    }

    // ==================== 最佳匹配 ====================

    private fun candidate(id: Int, name: String, type: String = "app"): SteamStoreSearchItemDto =
        SteamStoreSearchItemDto(
            type = type,
            name = name,
            id = id,
            price = SteamPriceDto(currency = "CNY", initial = 29800, final = 26800),
        )

    @Test
    fun bestMatch_picksExactOverFuzzy() {
        val result = SteamTitleMatcher.bestMatch(
            "艾尔登法环",
            listOf(candidate(1245620, "艾尔登法环"), candidate(1203620, "艾尔登法环 黄金树幽影")),
        )
        assertNotNull(result)
        assertEquals(1245620, result!!.appId)
        assertEquals(1f, result.confidence, 0.001f)
    }

    @Test
    fun bestMatch_ignoresNonAppTypes() {
        val result = SteamTitleMatcher.bestMatch(
            "黑神话：悟空",
            listOf(candidate(2358720, "黑神话：悟空", type = "app"), candidate(999, "黑神话：悟空 DLC", type = "sub")),
        )
        assertNotNull(result)
        assertEquals(2358720, result!!.appId)
    }

    @Test
    fun bestMatch_carriesPriceAndImage() {
        val result = SteamTitleMatcher.bestMatch(
            "艾尔登法环",
            listOf(candidate(1245620, "艾尔登法环")),
        )
        assertNotNull(result)
        assertEquals(26800, result!!.priceCents)
        assertEquals("CNY", result.currency)
    }

    @Test
    fun bestMatch_noCandidate_belowThreshold_returnsNull() {
        assertNull(SteamTitleMatcher.bestMatch("随机作品名", emptyList()))
        assertNull(SteamTitleMatcher.bestMatch("随机作品名", listOf(candidate(1, "完全无关的名字"))))
    }
}
